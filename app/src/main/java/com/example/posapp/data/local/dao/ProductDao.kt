package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun search(query: String, categoryId: Long?): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE sku = :sku LIMIT 1")
    suspend fun findBySku(sku: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock <= lowStockThreshold ORDER BY stock ASC")
    fun observeLowStock(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)

    // WHERE stock >= :qty mencegah stok jadi minus akibat race condition/data stale (mis. dua
    // checkout hampir bersamaan). Mengembalikan jumlah baris terdampak (0 = stok tidak cukup
    // SAAT INI di DB, bukan cuma snapshot keranjang) supaya pemanggil (TransactionRepository)
    // bisa membatalkan seluruh transaksi kalau stok ternyata kurang.
    @Query("UPDATE products SET stock = stock - :qty, updatedAt = :now WHERE id = :productId AND stock >= :qty")
    suspend fun decreaseStock(productId: Long, qty: Int, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE products SET stock = stock + :qty, updatedAt = :now WHERE id = :productId")
    suspend fun increaseStock(productId: Long, qty: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products")
    suspend fun getAllForExport(): List<ProductEntity>
}
