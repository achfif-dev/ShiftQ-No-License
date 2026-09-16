package com.example.posapp.domain.usecase

import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.domain.model.Cart

/**
 * Aturan validasi checkout, dipisah dari [CheckoutUseCase] supaya bisa diuji dengan unit test
 * murni (tanpa Room/Hilt/Android framework) — logika ini yang paling penting untuk benar,
 * karena langsung menentukan apakah transaksi uang boleh diproses atau ditolak.
 */
object CheckoutValidator {

    // Toleransi pembulatan untuk perbandingan nominal Rupiah bertipe Double. Semua harga di app
    // ini bilangan rupiah utuh, tapi diskon persen (mis. 12.5%) atau pajak bisa menghasilkan
    // pecahan yang tidak presisi di representasi biner Double (mis. 0.1 + 0.2 != 0.3). Tanpa
    // toleransi ini, pembayaran pas (uang pas) bisa saja ditolak keliru sebagai "belum cukup"
    // hanya karena selisih floating-point sebesar sepersekian rupiah.
    private const val AMOUNT_EPSILON = 0.5

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    /** Total yang sudah dibayar dari daftar split payment (hanya baris dengan nominal > 0). */
    fun totalPaid(payments: List<PaymentSplit>): Double =
        payments.filter { it.amount > 0 }.sumOf { it.amount }

    /**
     * @param customerId wajib diisi (non-null) kalau salah satu baris pembayaran memakai
     * metode BON/piutang — piutang tanpa pelanggan yang jelas tidak bisa ditagih kembali.
     */
    fun validate(cart: Cart, payments: List<PaymentSplit>, customerId: Long? = null): ValidationResult {
        if (cart.isEmpty) {
            return ValidationResult.Invalid("Keranjang masih kosong")
        }

        val validPayments = payments.filter { it.amount > 0 }
        if (validPayments.isEmpty()) {
            return ValidationResult.Invalid("Masukkan minimal satu metode pembayaran")
        }

        val hasBon = validPayments.any { it.method == PaymentMethod.BON }
        if (hasBon && customerId == null) {
            return ValidationResult.Invalid("Pilih pelanggan terlebih dahulu untuk pembayaran Bon/Piutang")
        }

        val paid = totalPaid(validPayments)
        if (paid < cart.total - AMOUNT_EPSILON) {
            return ValidationResult.Invalid("Total pembayaran belum mencukupi total belanja")
        }

        cart.lines.firstOrNull { it.quantity > it.availableStock }?.let { line ->
            val label = line.variant?.let { " (${it.variantLabel})" } ?: ""
            return ValidationResult.Invalid("Stok ${line.product.name}$label tidak mencukupi")
        }

        return ValidationResult.Valid
    }
}
