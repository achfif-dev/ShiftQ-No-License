package com.example.posapp.data.repository

import androidx.room.withTransaction
import com.example.posapp.data.local.AppDatabase
import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
import com.example.posapp.data.local.entity.TransactionReturnEntity
import com.example.posapp.data.local.entity.TransactionReturnItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Dilempar saat stok produk/varian di database ternyata tidak cukup PERSIS SAAT checkout
 * dieksekusi (bukan cuma snapshot keranjang di UI) — misalnya kasir lain sudah menjual item
 * yang sama beberapa detik sebelumnya. Membatalkan seluruh transaksi (lihat withTransaction). */
class InsufficientStockException(message: String) : Exception(message)

/** Dilempar saat permintaan retur tidak valid (mis. qty retur melebihi qty yang dibeli, atau
 * transaksi yang mau diretur/void ternyata sudah VOIDED sebelumnya). */
class ReturnValidationException(message: String) : Exception(message)

/** Satu baris permintaan retur: item transaksi mana, berapa qty, dan apakah barangnya masih
 * layak dikembalikan ke stok (restocked) atau rusak/dibuang (restocked = false). */
data class ReturnItemRequest(
    val transactionItemId: Long,
    val quantity: Int,
    val restocked: Boolean
)

/** Detail lengkap satu baris retur/void untuk ditampilkan di riwayat retur suatu transaksi. */
data class ReturnWithItems(
    val header: TransactionReturnEntity,
    val items: List<TransactionReturnItemEntity>
)

@Singleton
class TransactionRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao,
    private val productVariantDao: ProductVariantDao
) {
    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeRange(start: Long, end: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeRange(start, end)

    /** Laba kotor sudah dikurangi nilai laba dari item yang diretur pada rentang yang sama —
     * lihat komentar TransactionDao.getReturnedGrossProfitInRange untuk alasannya. */
    suspend fun getSalesSummary(start: Long, end: Long): DailySalesSummary {
        val summary = transactionDao.getSalesSummary(start, end)
        val returnedProfit = transactionDao.getReturnedGrossProfitInRange(start, end)
        return summary.copy(totalGrossProfit = summary.totalGrossProfit - returnedProfit)
    }

    suspend fun getTopSellingItems(start: Long, end: Long, limit: Int = 10): List<TopSellingItem> =
        transactionDao.getTopSellingItems(start, end, limit)

    suspend fun getTransactionWithItems(id: Long): Pair<TransactionEntity, List<TransactionItemEntity>>? {
        val transaction = transactionDao.getById(id) ?: return null
        val items = transactionDao.getItems(id)
        return transaction to items
    }

    suspend fun getPayments(transactionId: Long): List<TransactionPaymentEntity> =
        transactionDao.getPayments(transactionId)

    /**
     * Menyimpan transaksi + item-nya (+ rincian split pembayaran bila ada) DAN mengurangi stok
     * tiap produk sebagai SATU transaksi database (`withTransaction`) — sebelumnya insert
     * transaksi dan pengurangan stok berjalan sebagai operasi terpisah, jadi kalau app crash di
     * tengah proses (mis. keranjang berisi banyak item), transaksi bisa tersimpan tapi stok
     * sebagian tidak ter-update. Dipanggil oleh CheckoutUseCase agar 1 checkout = 1 operasi
     * benar-benar konsisten (semua berhasil, atau semua dibatalkan/rollback).
     *
     * @throws InsufficientStockException bila stok riil di DB ternyata tidak cukup saat
     * dieksekusi (bukan cuma snapshot keranjang) — seluruh transaksi otomatis dibatalkan.
     */
    suspend fun checkout(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        payments: List<TransactionPaymentEntity> = emptyList()
    ): Long = appDatabase.withTransaction {
        val txId = transactionDao.insertFullTransaction(transaction, items, payments)
        items.forEach { item ->
            val rowsAffected = if (item.variantId != null) {
                productVariantDao.decreaseStock(item.variantId, item.quantity)
            } else {
                productDao.decreaseStock(item.productId, item.quantity)
            }
            if (rowsAffected == 0) {
                throw InsufficientStockException(
                    "Stok ${item.productNameSnapshot} tidak mencukupi (mungkin baru saja terjual di transaksi lain)"
                )
            }
        }
        txId
    }

    /**
     * Mengoreksi transaksi yang sudah tersimpan (fitur "Riwayat Penjualan bisa diedit Admin",
     * untuk memperbaiki kesalahan input kasir). Menyesuaikan stok produk/varian berdasarkan
     * selisih quantity tiap item, mengembalikan stok utuh untuk item yang dihapus, lalu
     * menyimpan total baru + jejak audit (editedByName/editedAt).
     *
     * @param originalItems item transaksi SEBELUM diedit (dipakai untuk menghitung selisih stok).
     * @param updatedItems item yang tersisa setelah diedit (quantity/harga/diskon boleh berubah).
     * @param deletedItemIds id item yang dihapus sepenuhnya dari transaksi.
     */
    suspend fun updateTransactionWithCorrection(
        updatedTransaction: TransactionEntity,
        originalItems: List<TransactionItemEntity>,
        updatedItems: List<TransactionItemEntity>,
        deletedItemIds: List<Long>,
        editedByName: String
    ): Unit = appDatabase.withTransaction {
        // Item yang dihapus total: kembalikan stoknya lalu hapus baris item.
        deletedItemIds.forEach { itemId ->
            val original = originalItems.find { it.id == itemId } ?: return@forEach
            if (original.variantId != null) {
                productVariantDao.increaseStock(original.variantId, original.quantity)
            } else {
                productDao.increaseStock(original.productId, original.quantity)
            }
            transactionDao.deleteItem(itemId)
        }

        // Item yang masih ada: sesuaikan stok hanya sebesar SELISIH quantity lama vs baru.
        updatedItems.forEach { updated ->
            val original = originalItems.find { it.id == updated.id }
            val delta = updated.quantity - (original?.quantity ?: 0) // >0 = tambah qty (stok berkurang lagi)
            if (delta != 0) {
                val rowsAffected = if (updated.variantId != null) {
                    if (delta > 0) productVariantDao.decreaseStock(updated.variantId, delta)
                    else { productVariantDao.increaseStock(updated.variantId, -delta); 1 }
                } else {
                    if (delta > 0) productDao.decreaseStock(updated.productId, delta)
                    else { productDao.increaseStock(updated.productId, -delta); 1 }
                }
                if (rowsAffected == 0) {
                    throw InsufficientStockException(
                        "Stok ${updated.productNameSnapshot} tidak mencukupi untuk koreksi ini"
                    )
                }
            }
            transactionDao.updateItem(updated)
        }

        transactionDao.updateTransaction(
            updatedTransaction.copy(editedByName = editedByName, editedAt = System.currentTimeMillis())
        )
    }

    /**
     * Menghapus SATU transaksi sepenuhnya (koreksi Admin untuk transaksi yang salah input
     * total dari awal, mis. kasir checkout transaksi yang salah/ganda). Mengembalikan stok
     * seluruh item ke produk/varian terkait sebelum baris transaksi (beserta item-itemnya,
     * lewat FK CASCADE) dihapus. Tidak bisa dibatalkan.
     */
    suspend fun deleteTransaction(transactionId: Long): Unit = appDatabase.withTransaction {
        val items = transactionDao.getItems(transactionId)
        items.forEach { item ->
            if (item.variantId != null) {
                productVariantDao.increaseStock(item.variantId, item.quantity)
            } else {
                productDao.increaseStock(item.productId, item.quantity)
            }
        }
        transactionDao.deleteTransaction(transactionId)
    }

    /**
     * Membatalkan SATU transaksi sepenuhnya (Admin-only, dicek di layer ViewModel lewat
     * Permission). Beda dari [deleteTransaction] lama: transaksi TIDAK dihapus dari database,
     * hanya ditandai status="VOIDED" — sehingga tetap ada jejak audit lengkap (kapan, siapa,
     * kenapa) tapi dikeluarkan dari semua perhitungan Laporan. Stok seluruh item dikembalikan
     * penuh (asumsi: void dipakai untuk transaksi salah pencet, barang belum betulan berpindah).
     *
     * @throws ReturnValidationException bila transaksi sudah pernah di-void sebelumnya.
     */
    suspend fun voidTransaction(
        transactionId: Long,
        reason: String,
        voidedByName: String
    ): Unit = appDatabase.withTransaction {
        val transaction = transactionDao.getById(transactionId)
            ?: throw ReturnValidationException("Transaksi tidak ditemukan")
        if (transaction.status == "VOIDED") {
            throw ReturnValidationException("Transaksi ini sudah dibatalkan sebelumnya")
        }
        val items = transactionDao.getItems(transactionId)
        items.forEach { item ->
            if (item.variantId != null) {
                productVariantDao.increaseStock(item.variantId, item.quantity)
            } else {
                productDao.increaseStock(item.productId, item.quantity)
            }
        }
        transactionDao.insertReturn(
            TransactionReturnEntity(
                transactionId = transactionId,
                isVoid = true,
                reason = reason,
                // Kalau sebelumnya sudah ada retur sebagian (returnedAmount > 0), void cuma
                // membatalkan SISA yang belum diretur — bukan seluruh total transaksi lagi.
                refundAmount = transaction.total - transaction.returnedAmount,
                refundMethod = null,
                processedByName = voidedByName
            )
        )
        transactionDao.markVoided(transactionId, voidedByName, System.currentTimeMillis())
    }

    /**
     * Memproses retur barang (sebagian atau seluruh item) untuk transaksi yang sudah selesai —
     * dipakai saat pelanggan mengembalikan barang setelah dibawa pulang. Transaksi asal TIDAK
     * diubah nilainya; retur dicatat sebagai baris terpisah (bisa berkali-kali/bertahap untuk
     * transaksi yang sama) dan akumulasi nominalnya ditambahkan ke `returnedAmount` transaksi.
     *
     * @param items daftar item beserta qty yang diretur & apakah barangnya layak masuk stok lagi.
     * @throws ReturnValidationException bila transaksi sudah VOIDED, item tidak ditemukan, atau
     * qty retur (dijumlah dengan retur sebelumnya untuk item yang sama) melebihi qty yang dibeli.
     */
    suspend fun processReturn(
        transactionId: Long,
        items: List<ReturnItemRequest>,
        reason: String,
        refundAmount: Double,
        refundMethod: PaymentMethod?,
        processedByName: String
    ): Unit = appDatabase.withTransaction {
        val transaction = transactionDao.getById(transactionId)
            ?: throw ReturnValidationException("Transaksi tidak ditemukan")
        if (transaction.status == "VOIDED") {
            throw ReturnValidationException("Transaksi ini sudah dibatalkan (void), tidak bisa diretur")
        }
        if (items.isEmpty()) {
            throw ReturnValidationException("Pilih minimal 1 item yang diretur")
        }
        val originalItems = transactionDao.getItems(transactionId).associateBy { it.id }

        val returnId = transactionDao.insertReturn(
            TransactionReturnEntity(
                transactionId = transactionId,
                isVoid = false,
                reason = reason,
                refundAmount = refundAmount,
                refundMethod = refundMethod?.name,
                processedByName = processedByName
            )
        )

        val returnItemRows = items.map { request ->
            val original = originalItems[request.transactionItemId]
                ?: throw ReturnValidationException("Item transaksi tidak ditemukan")
            if (request.quantity <= 0) {
                throw ReturnValidationException("Qty retur ${original.productNameSnapshot} harus lebih dari 0")
            }
            val alreadyReturned = transactionDao.getReturnedQuantityForItem(request.transactionItemId)
            if (alreadyReturned + request.quantity > original.quantity) {
                throw ReturnValidationException(
                    "Qty retur ${original.productNameSnapshot} melebihi qty yang dibeli " +
                        "(sudah diretur $alreadyReturned dari ${original.quantity})"
                )
            }
            if (request.restocked) {
                if (original.variantId != null) {
                    productVariantDao.increaseStock(original.variantId, request.quantity)
                } else {
                    productDao.increaseStock(original.productId, request.quantity)
                }
            }
            TransactionReturnItemEntity(
                returnId = returnId,
                transactionItemId = request.transactionItemId,
                productId = original.productId,
                variantId = original.variantId,
                quantityReturned = request.quantity,
                restocked = request.restocked
            )
        }
        transactionDao.insertReturnItems(returnItemRows)
        transactionDao.addReturnedAmount(transactionId, refundAmount)
    }

    /** Riwayat retur/void untuk satu transaksi (ditampilkan di dialog detail Riwayat Penjualan). */
    suspend fun getReturnHistory(transactionId: Long): List<ReturnWithItems> =
        transactionDao.getReturnsForTransaction(transactionId).map { header ->
            ReturnWithItems(header, transactionDao.getReturnItems(header.id))
        }
}
