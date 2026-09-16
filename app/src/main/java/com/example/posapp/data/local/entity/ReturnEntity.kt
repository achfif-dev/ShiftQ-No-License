package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Header retur/void untuk satu transaksi. Satu transaksi bisa punya LEBIH DARI SATU baris retur
 * (retur sebagian, bertahap dari hari ke hari), tapi VOID selalu retur penuh & ditandai juga di
 * TransactionEntity.status supaya tidak bisa diproses retur/void lagi setelahnya.
 *
 * PENTING: transaksi ASLI (TransactionEntity/TransactionItemEntity) tidak pernah diubah nilainya
 * oleh retur — semua nilai historis (harga, qty, total) tetap seperti transaksi awal. Laporan
 * menghitung penjualan bersih dengan mengurangi TransactionEntity.returnedAmount dari total.
 * Ini beda dari "Koreksi Transaksi" (ReportViewModel.saveTransactionCorrection) yang memang
 * mengubah data transaksi langsung — dipakai untuk salah input kasir, bukan barang kembali dari
 * pelanggan.
 */
@Entity(
    tableName = "transaction_returns",
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
data class TransactionReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    // true = VOID (batalkan seluruh transaksi, dianggap tidak pernah terjadi — dipakai kalau
    //   kasir salah pencet dan uangnya belum benar-benar final, atau utk koreksi darurat Admin).
    // false = RETUR biasa (barang dikembalikan pelanggan setelah dibawa pulang, bisa sebagian
    //   item saja; uang dikembalikan/refund, transaksi asal tetap tercatat apa adanya).
    val isVoid: Boolean,
    val reason: String,
    val refundAmount: Double,       // nominal yang dikembalikan ke pelanggan
    val refundMethod: String? = null, // snapshot nama PaymentMethod (CASH/DEBIT_CREDIT/QRIS/dst.) — boleh beda dari metode bayar asal, mis. bayar QRIS tapi refund tunai. Disimpan String biasa (bukan tipe PaymentMethod) supaya tidak perlu TypeConverter nullable baru.
    val processedByName: String,     // audit: siapa yang memproses retur/void ini
    val createdAt: Long = System.currentTimeMillis()
)

/** Rincian item yang diretur dalam satu baris transaction_returns. Untuk VOID, baris ini dibuat
 * untuk SEMUA item transaksi asal (retur penuh otomatis). Untuk retur biasa, hanya berisi item
 * (dan qty) yang benar-benar dikembalikan pelanggan. */
@Entity(
    tableName = "transaction_return_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionReturnEntity::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionItemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("returnId"), Index("transactionItemId")]
)
data class TransactionReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val transactionItemId: Long,
    val productId: Long,   // disalin dari transaction_items supaya penyesuaian stok tidak perlu join balik
    val variantId: Long? = null,
    val quantityReturned: Int,
    // true = barang masih layak jual, stok dikembalikan ke persediaan.
    // false = barang rusak/tidak layak jual lagi, stok TIDAK dikembalikan (jadi kerugian toko).
    val restocked: Boolean
)
