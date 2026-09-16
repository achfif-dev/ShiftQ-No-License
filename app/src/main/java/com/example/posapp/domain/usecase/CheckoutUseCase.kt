package com.example.posapp.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.AuditLogRepository
import com.example.posapp.data.repository.CustomerRepository
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.data.settings.StoreProfileRepository
import com.example.posapp.domain.model.Cart
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class CheckoutResult {
    data class Success(val transactionId: Long, val invoiceNumber: String, val change: Double) : CheckoutResult()
    data class Error(val message: String) : CheckoutResult()
}

/** Satu baris pembayaran. Satu transaksi bisa punya beberapa (mis. sebagian Cash, sisanya QRIS). */
data class PaymentSplit(val method: PaymentMethod, val amount: Double)

/**
 * Use case tunggal untuk memproses pembayaran:
 * - Validasi cart tidak kosong & total pembayaran (bisa lebih dari satu metode/split) cukup
 * - Generate nomor invoice
 * - Simpan transaksi + item + rincian pembayaran ke Room (mengurangi stok otomatis lewat repository)
 */
class CheckoutUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val customerRepository: CustomerRepository,
    private val storeProfileRepository: StoreProfileRepository,
    private val auditLogRepository: AuditLogRepository
) {
    suspend operator fun invoke(
        cart: Cart,
        payments: List<PaymentSplit>,
        note: String? = null,
        cashierName: String? = null,
        customerId: Long? = null,
        /** Role aktor yang login saat checkout ini diproses — HANYA dipakai untuk audit log
         * diskon manual (lihat [logManualDiscountIfAny]), tidak memengaruhi validasi checkout
         * itu sendiri (batas diskon sudah ditegakkan lebih awal di PosViewModel lewat
         * DiscountPolicy, sebelum cart ini sampai ke sini). */
        actorRole: UserRole? = null
    ): CheckoutResult {
        when (val validation = CheckoutValidator.validate(cart, payments, customerId)) {
            is CheckoutValidator.ValidationResult.Invalid -> return CheckoutResult.Error(validation.message)
            CheckoutValidator.ValidationResult.Valid -> Unit
        }

        val validPayments = payments.filter { it.amount > 0 }
        val totalPaid = CheckoutValidator.totalPaid(validPayments)
        val change = (totalPaid - cart.total).coerceAtLeast(0.0)
        val primaryMethod = if (validPayments.size == 1) validPayments.first().method else PaymentMethod.MIXED

        val items = cart.lines.map { line ->
            TransactionItemEntity(
                transactionId = 0,
                productId = line.product.id,
                variantId = line.variant?.id,
                variantLabelSnapshot = line.variant?.variantLabel,
                productNameSnapshot = line.product.name + (line.variant?.let { " (${it.variantLabel})" } ?: ""),
                priceSnapshot = line.unitPrice,
                quantity = line.quantity,
                unitSnapshot = line.product.unit,
                itemDiscount = line.discount,
                itemNote = line.note
            )
        }

        // dueDate (v15) hanya diisi untuk baris BON — dipakai Reminder Piutang Jatuh Tempo.
        // Diambil sekali di sini (bukan per baris) karena semua baris BON dalam satu transaksi
        // yang sama wajar punya tenggat yang sama.
        val bonDueDays = storeProfileRepository.profile.first().bonDueDays
        val bonDueDate = System.currentTimeMillis() + bonDueDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000
        val paymentEntities = validPayments.map {
            TransactionPaymentEntity(
                transactionId = 0,
                method = it.method,
                amount = it.amount,
                dueDate = if (it.method == PaymentMethod.BON) bonDueDate else null
            )
        }

        // `transactions.invoiceNumber` punya unique index di DB (lihat Migrations.kt). Nomor
        // invoice sudah dibuat presisi milidetik + sufiks acak supaya tabrakan nyaris mustahil,
        // tapi tetap coba ulang beberapa kali dengan nomor baru kalau entah bagaimana tetap
        // bentrok (mis. jam sistem device diubah manual), daripada checkout gagal total.
        var lastError: Exception? = null
        repeat(MAX_INVOICE_RETRY) {
            val invoiceNumber = generateInvoiceNumber()
            val transaction = TransactionEntity(
                invoiceNumber = invoiceNumber,
                subtotal = cart.subtotal,
                taxPercent = cart.taxPercent,
                taxAmount = cart.taxAmount,
                discountAmount = cart.totalDiscount, // diskon manual + penukaran poin loyalitas (v13)
                total = cart.total,
                paymentMethod = primaryMethod,
                amountPaid = totalPaid,
                changeAmount = change,
                note = note,
                cashierName = cashierName,
                customerId = customerId
            )
            try {
                val txId = transactionRepository.checkout(transaction, items, paymentEntities)
                applyLoyaltyPoints(cart, customerId)
                logManualDiscountIfAny(cart, invoiceNumber, cashierName, actorRole)
                return CheckoutResult.Success(txId, invoiceNumber, change)
            } catch (e: SQLiteConstraintException) {
                lastError = e // nomor invoice bentrok, ulangi dengan nomor baru
            } catch (e: Exception) {
                return CheckoutResult.Error(e.message ?: "Gagal menyimpan transaksi")
            }
        }
        return CheckoutResult.Error(lastError?.message ?: "Gagal menyimpan transaksi (nomor invoice bentrok)")
    }

    private fun generateInvoiceNumber(): String {
        val datePart = SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        return "INV-$datePart-$randomSuffix"
    }

    /**
     * Poin loyalitas (v13): dilakukan SETELAH transaksi utama sukses tersimpan, sengaja DI LUAR
     * withTransaction milik TransactionRepository.checkout — kegagalan di sini (mis. app
     * crash tepat di antara dua operasi) paling buruk membuat pelanggan tidak dapat/tidak
     * kehilangan poin sesuai jadwal, bukan merusak data transaksi/stok yang jauh lebih kritis.
     * 1. Kurangi saldo sebesar poin yang ditukar pelanggan sebagai potongan (jika ada).
     * 2. Tambah poin baru dari total belanja transaksi ini, HANYA jika fitur loyalitas
     *    masih aktif saat checkout ini diproses (bisa saja dimatikan admin di tengah hari).
     */
    private suspend fun applyLoyaltyPoints(cart: Cart, customerId: Long?) {
        if (customerId == null) return
        if (cart.loyaltyPointsRedeemed > 0) {
            customerRepository.adjustLoyaltyPoints(customerId, -cart.loyaltyPointsRedeemed)
        }
        val profile = storeProfileRepository.profile.first()
        if (!profile.loyaltyEnabled || profile.loyaltyRupiahPerPoint <= 0) return
        val earned = (cart.total / profile.loyaltyRupiahPerPoint).toLong()
        if (earned > 0) {
            customerRepository.adjustLoyaltyPoints(customerId, earned)
        }
    }

    /**
     * TEMUAN KEAMANAN (audit ulang): sebelumnya diskon manual (per-baris & per-transaksi) sama
     * sekali tidak tercatat di AuditLogRepository -- beda dari Void/Koreksi/Retur/Shift yang
     * semuanya tercatat. Tanpa jejak ini, kasir yang berulang kali memberi diskon besar ke
     * "kenalan"/diri sendiri (celah "sweethearting") tidak meninggalkan bukti apa pun untuk
     * ditelusuri, hanya bisa ketahuan TIDAK LANGSUNG lewat selisih kas saat tutup shift.
     * Dicatat SEKALI per transaksi yang selesai checkout (bukan tiap kali kasir mengetik ulang
     * nilai di layar) supaya audit log tidak dibanjiri percobaan yang belum final. Batas nilai
     * diskon itu sendiri sudah ditegakkan lebih awal oleh DiscountPolicy di PosViewModel --
     * fungsi ini murni pencatatan, bukan validasi/penegakan kedua.
     */
    private suspend fun logManualDiscountIfAny(
        cart: Cart,
        invoiceNumber: String,
        cashierName: String?,
        actorRole: UserRole?
    ) {
        val manualDiscount = cart.transactionDiscount + cart.lines.sumOf { it.discount }
        if (manualDiscount <= 0.0) return
        // BUG (ditemukan saat audit ulang): cart.subtotal SUDAH BERSIH dari diskon manual
        // per-baris & promo per-baris (lihat properti Cart.subtotal) -- memakainya langsung
        // sebagai pembagi membuat persentase yang tercatat LEBIH BESAR dari diskon yang
        // sesungguhnya diberikan (mis. diskon 20% dari Rp100rb jadi tercatat sebagai 25%,
        // karena dibagi Rp80rb bukan Rp100rb). grossSubtotal di bawah menghitung ulang subtotal
        // SEBELUM diskon apa pun (manual maupun promo), supaya persentase di log sesuai makna
        // "dari subtotal" yang sebenarnya dipahami pembaca log.
        val grossSubtotal = cart.lines.sumOf { it.unitPrice * it.quantity }
        val percentOfSubtotal = if (grossSubtotal > 0) (manualDiscount / grossSubtotal) * 100 else 0.0
        auditLogRepository.log(
            actorName = cashierName ?: "Kasir",
            actorRole = actorRole,
            action = "DISKON_MANUAL",
            description = "Diskon manual Rp${manualDiscount.toInt()} " +
                "(${"%.1f".format(percentOfSubtotal)}% dari subtotal) pada transaksi $invoiceNumber"
        )
    }

    private companion object {
        const val MAX_INVOICE_RETRY = 3
    }
}
