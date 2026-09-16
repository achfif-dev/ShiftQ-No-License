package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val productVariantDao: ProductVariantDao
) {
    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()

    fun search(query: String, categoryId: Long?): Flow<List<ProductEntity>> =
        productDao.search(query, categoryId)

    fun observeLowStock(): Flow<List<ProductEntity>> = productDao.observeLowStock()

    suspend fun findBySku(sku: String): ProductEntity? = productDao.findBySku(sku)

    /** Dipakai ParkedSaleRepository.restore (v15) untuk memuat ulang data produk TERBARU (harga/
     * stok bisa sudah berubah sejak transaksi ditahan) dari productId yang tersimpan. */
    suspend fun findById(id: Long): ProductEntity? = productDao.findById(id)

    /** Dipakai ParkedSaleRepository.restore (v15), lihat [findById]. */
    suspend fun findVariantById(id: Long): ProductVariantEntity? = productVariantDao.findById(id)

    suspend fun upsert(product: ProductEntity): Long =
        if (product.id == 0L) productDao.insert(product) else {
            productDao.update(product); product.id
        }

    suspend fun delete(id: Long) = productDao.softDelete(id)

    /**
     * Penyesuaian stok manual (Stock In / Stock Out / Opname) dengan pencatatan riwayat.
     *
     * @throws InsufficientStockException (audit menyeluruh — bug ditemukan) bila OUT/OPNAME
     * turun melebihi stok yang tersedia: [ProductDao.decreaseStock] punya guard SQL
     * `WHERE stock >= :qty` (sama seperti dipakai checkout) yang membuat UPDATE tidak
     * berpengaruh sama sekali (0 baris) kalau stok tidak cukup. SEBELUM PERBAIKAN INI, hasil
     * `rowsAffected` itu sama sekali tidak dicek — riwayat penyesuaian tetap dicatat seolah
     * berhasil dan ViewModel menampilkan "Stok berhasil diperbarui" padahal kolom stok di
     * database TIDAK BERUBAH SAMA SEKALI, membuat riwayat stok menyimpang diam-diam dari
     * angka stok sebenarnya. Sekarang gagal jelas (exception) dan TIDAK mencatat riwayat palsu.
     */
    suspend fun adjustStock(productId: Long, type: String, quantity: Int, reason: String?) {
        when (type) {
            "IN" -> productDao.increaseStock(productId, quantity)
            "OUT" -> {
                val rows = productDao.decreaseStock(productId, quantity)
                if (rows == 0) throw InsufficientStockException("Stok tidak mencukupi untuk pengurangan ini")
            }
            "OPNAME" -> {
                val current = productDao.findById(productId)?.stock ?: 0
                val diff = quantity - current
                if (diff > 0) productDao.increaseStock(productId, diff)
                if (diff < 0) {
                    val rows = productDao.decreaseStock(productId, -diff)
                    if (rows == 0) throw InsufficientStockException("Gagal menyesuaikan stok opname (stok berubah bersamaan, coba lagi)")
                }
            }
        }
        stockAdjustmentDao.insert(
            StockAdjustmentEntity(productId = productId, type = type, quantity = quantity, reason = reason)
        )
    }

    /** Penyesuaian stok manual untuk satu kombinasi varian tertentu, dengan riwayat tersendiri.
     * Lihat catatan lengkap penanganan kegagalan di [adjustStock] — berlaku sama persis di sini
     * untuk [ProductVariantDao.decreaseStock]. */
    suspend fun adjustVariantStock(
        productId: Long,
        variantId: Long,
        variantLabel: String,
        type: String,
        quantity: Int,
        reason: String?
    ) {
        when (type) {
            "IN" -> productVariantDao.increaseStock(variantId, quantity)
            "OUT" -> {
                val rows = productVariantDao.decreaseStock(variantId, quantity)
                if (rows == 0) throw InsufficientStockException("Stok varian \"$variantLabel\" tidak mencukupi untuk pengurangan ini")
            }
            "OPNAME" -> {
                val current = productVariantDao.findById(variantId)?.stock ?: 0
                val diff = quantity - current
                if (diff > 0) productVariantDao.increaseStock(variantId, diff)
                if (diff < 0) {
                    val rows = productVariantDao.decreaseStock(variantId, -diff)
                    if (rows == 0) throw InsufficientStockException("Gagal menyesuaikan stok opname varian \"$variantLabel\" (stok berubah bersamaan, coba lagi)")
                }
            }
        }
        stockAdjustmentDao.insert(
            StockAdjustmentEntity(
                productId = productId,
                type = type,
                quantity = quantity,
                reason = reason,
                variantId = variantId,
                variantLabelSnapshot = variantLabel
            )
        )
    }

    fun observeAdjustmentHistory(): Flow<List<StockAdjustmentEntity>> = stockAdjustmentDao.observeAll()

    suspend fun getAllForExport(): List<ProductEntity> = productDao.getAllForExport()

    // --- Varian produk (matrix Ukuran x Warna dengan stok terpisah) ---

    fun observeVariants(productId: Long): Flow<List<ProductVariantEntity>> =
        productVariantDao.observeForProduct(productId)

    suspend fun getVariants(productId: Long): List<ProductVariantEntity> =
        productVariantDao.getForProduct(productId)

    suspend fun findVariantBySku(sku: String): ProductVariantEntity? =
        productVariantDao.findBySku(sku)

    suspend fun upsertVariant(variant: ProductVariantEntity): Long =
        if (variant.id == 0L) productVariantDao.insert(variant) else {
            productVariantDao.update(variant); variant.id
        }

    suspend fun deleteVariant(id: Long) = productVariantDao.softDelete(id)

    suspend fun decreaseVariantStock(variantId: Long, qty: Int) = productVariantDao.decreaseStock(variantId, qty)

    suspend fun increaseVariantStock(variantId: Long, qty: Int) = productVariantDao.increaseStock(variantId, qty)
}
