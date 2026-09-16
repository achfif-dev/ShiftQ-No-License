package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.ProductVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductVariantDao {

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isActive = 1 ORDER BY variantLabel ASC")
    fun observeForProduct(productId: Long): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isActive = 1 ORDER BY variantLabel ASC")
    suspend fun getForProduct(productId: Long): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants WHERE sku = :sku LIMIT 1")
    suspend fun findBySku(sku: String): ProductVariantEntity?

    @Query("SELECT * FROM product_variants WHERE id = :id")
    suspend fun findById(id: Long): ProductVariantEntity?

    @Insert
    suspend fun insert(variant: ProductVariantEntity): Long

    @Update
    suspend fun update(variant: ProductVariantEntity)

    @Query("UPDATE product_variants SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)

    // Lihat catatan di ProductDao.decreaseStock: guard stok >= qty + return jumlah baris
    // terdampak, supaya kekurangan stok bisa membatalkan seluruh transaksi checkout.
    @Query("UPDATE product_variants SET stock = stock - :qty WHERE id = :variantId AND stock >= :qty")
    suspend fun decreaseStock(variantId: Long, qty: Int): Int

    @Query("UPDATE product_variants SET stock = stock + :qty WHERE id = :variantId")
    suspend fun increaseStock(variantId: Long, qty: Int)
}
