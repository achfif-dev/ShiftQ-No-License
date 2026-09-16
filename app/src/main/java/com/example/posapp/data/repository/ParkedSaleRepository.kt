package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.ParkedSaleDao
import com.example.posapp.data.local.entity.ParkedSaleEntity
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class RestoreParkedSaleResult {
    data class Success(
        val cart: Cart,
        val skippedItemNames: List<String> // produk yang sudah dihapus/nonaktif sejak ditahan
    ) : RestoreParkedSaleResult()
    data class Error(val message: String) : RestoreParkedSaleResult()
}

/**
 * Simpan & muat ulang keranjang yang "ditahan" kasir (v15) — lihat ParkedSaleEntity untuk format
 * [ParkedSaleEntity.itemsData]. Bergantung ke [ProductRepository] (bukan DAO produk langsung)
 * supaya lookup produk konsisten dengan bagian app lain.
 */
@Singleton
class ParkedSaleRepository @Inject constructor(
    private val parkedSaleDao: ParkedSaleDao,
    private val productRepository: ProductRepository
) {
    fun observeAll(): Flow<List<ParkedSaleEntity>> = parkedSaleDao.observeAll()

    /** Tahan [cart] saat ini. Kembalikan false kalau keranjang kosong (tidak ada gunanya ditahan). */
    suspend fun park(
        cart: Cart,
        note: String?,
        customerId: Long?,
        customerName: String?,
        createdByName: String
    ): Boolean {
        if (cart.isEmpty) return false
        val itemsData = cart.lines.joinToString(";") { line ->
            "${line.product.id}:${line.variant?.id ?: 0}:${line.quantity}:${line.discount}"
        }
        parkedSaleDao.insert(
            ParkedSaleEntity(
                itemsData = itemsData,
                note = note?.trim()?.takeIf { it.isNotBlank() },
                customerId = customerId,
                customerName = customerName,
                tableTag = cart.tableTag,
                transactionDiscount = cart.transactionDiscount,
                itemCount = cart.lines.sumOf { it.quantity },
                estimatedTotal = cart.total,
                createdByName = createdByName
            )
        )
        return true
    }

    /**
     * Muat ulang jadi [Cart] siap pakai. Setiap produk/varian di-lookup ULANG dari database
     * SEKARANG (bukan snapshot) — harga/stok sudah pasti yang terbaru. Produk yang sudah
     * dihapus/dinonaktifkan sejak ditahan dilewati (dilaporkan lewat [RestoreParkedSaleResult.Success.skippedItemNames])
     * daripada menggagalkan seluruh proses "Lanjutkan".
     */
    suspend fun restore(id: Long): RestoreParkedSaleResult {
        val parked = parkedSaleDao.getById(id)
            ?: return RestoreParkedSaleResult.Error("Transaksi tertahan ini sudah tidak ada (mungkin sudah dilanjutkan/dihapus)")

        val skipped = mutableListOf<String>()
        val lines = mutableListOf<CartLine>()
        parked.itemsData.split(";").filter { it.isNotBlank() }.forEach { raw ->
            val parts = raw.split(":")
            if (parts.size != 4) return@forEach
            val productId = parts[0].toLongOrNull() ?: return@forEach
            val variantId = parts[1].toLongOrNull()?.takeIf { it != 0L }
            val quantity = parts[2].toIntOrNull() ?: return@forEach
            val discount = parts[3].toDoubleOrNull() ?: 0.0

            val product = productRepository.findById(productId)
            if (product == null || !product.isActive) {
                skipped.add("Produk #$productId")
                return@forEach
            }
            val variant = variantId?.let { productRepository.findVariantById(it) }
            if (variantId != null && (variant == null || !variant.isActive)) {
                skipped.add(product.name)
                return@forEach
            }
            lines.add(CartLine(product = product, variant = variant, quantity = quantity, discount = discount))
        }

        parkedSaleDao.delete(id)

        if (lines.isEmpty() && skipped.isNotEmpty()) {
            return RestoreParkedSaleResult.Error(
                "Semua produk di transaksi tertahan ini sudah tidak tersedia lagi (${skipped.joinToString(", ")})"
            )
        }

        return RestoreParkedSaleResult.Success(
            cart = Cart(
                lines = lines,
                transactionDiscount = parked.transactionDiscount,
                tableTag = parked.tableTag
            ),
            skippedItemNames = skipped
        )
    }

    suspend fun discard(id: Long) = parkedSaleDao.delete(id)
}
