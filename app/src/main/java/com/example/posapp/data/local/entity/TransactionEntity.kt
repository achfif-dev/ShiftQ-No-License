package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentMethod { CASH, DEBIT_CREDIT, QRIS, BON, MIXED } // MIXED = split payment (>1 metode dalam satu transaksi); BON = piutang, wajib terhubung ke CustomerEntity

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("customerId"), Index("invoiceNumber", unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,      // mis. INV-20260822-0001
    val subtotal: Double,
    val taxPercent: Double,         // PPN %
    val taxAmount: Double,
    val discountAmount: Double,     // diskon transaksi (nominal)
    val total: Double,
    val paymentMethod: PaymentMethod,
    val amountPaid: Double,
    val changeAmount: Double,
    val note: String? = null,
    val cashierName: String? = null, // snapshot nama kasir yang login saat transaksi (fitur multi-user)
    val customerId: Long? = null,    // diisi bila transaksi ini terhubung ke pelanggan (wajib untuk BON)
    val createdAt: Long = System.currentTimeMillis(),
    val editedByName: String? = null, // nama Admin terakhir yang mengoreksi transaksi ini (audit trail)
    val editedAt: Long? = null,
    // "COMPLETED" (normal) atau "VOIDED" (dibatalkan penuh — lihat TransactionRepository.voidTransaction).
    // Transaksi VOIDED tetap tersimpan apa adanya untuk audit, tapi dikeluarkan dari semua
    // perhitungan Laporan (omzet, laba, kas shift, produk terlaris).
    val status: String = "COMPLETED",
    // Akumulasi nominal yang sudah diretur/refund ke pelanggan (lihat TransactionReturnEntity).
    // Penjualan bersih di Laporan = total - returnedAmount. TIDAK termasuk refund akibat VOID
    // (void tidak dianggap penjualan sama sekali, bukan pengurangan dari penjualan yang terjadi).
    val returnedAmount: Double = 0.0,
    val voidedByName: String? = null,
    val voidedAt: Long? = null
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("transactionId"), Index("productId")]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val variantId: Long? = null,        // diisi bila item merupakan kombinasi varian tertentu
    val variantLabelSnapshot: String? = null,
    val productNameSnapshot: String, // simpan snapshot nama & harga saat transaksi
    val priceSnapshot: Double,
    val quantity: Int,
    val unitSnapshot: String = "pcs", // snapshot satuan produk saat transaksi (pcs/kg/liter/dus/dll)
    val itemDiscount: Double = 0.0,
    val itemNote: String? = null
) {
    val lineTotal: Double
        get() = (priceSnapshot * quantity) - itemDiscount
}

/** Rincian per-metode pembayaran untuk satu transaksi (mendukung split/multi-pembayaran). */
@Entity(
    tableName = "transaction_payments",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionId")]
)
data class TransactionPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val method: PaymentMethod,
    val amount: Double,
    /** Tanggal jatuh tempo piutang (v15) — HANYA diisi untuk baris method=BON, dihitung otomatis
     * saat checkout dari StoreProfile.bonDueDays (lihat CheckoutUseCase). Null untuk metode lain
     * & untuk baris BON lama dari sebelum fitur ini ada (tidak pernah dianggap jatuh tempo,
     * lihat CustomerDao.observeOverdueDebtors). */
    val dueDate: Long? = null
)

@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: String,        // "IN", "OUT", "OPNAME"
    val quantity: Int,       // selalu positif; arah ditentukan oleh 'type'
    val reason: String? = null,
    val variantId: Long? = null,             // diisi bila penyesuaian ini untuk kombinasi varian tertentu
    val variantLabelSnapshot: String? = null, // snapshot label varian saat penyesuaian dibuat, untuk riwayat
    val createdAt: Long = System.currentTimeMillis()
)
