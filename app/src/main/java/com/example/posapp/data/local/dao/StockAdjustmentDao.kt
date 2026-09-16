package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {

    @Insert
    suspend fun insert(adjustment: StockAdjustmentEntity): Long

    @Query("SELECT * FROM stock_adjustments WHERE productId = :productId ORDER BY createdAt DESC")
    fun observeForProduct(productId: Long): Flow<List<StockAdjustmentEntity>>

    @Query("SELECT * FROM stock_adjustments WHERE variantId = :variantId ORDER BY createdAt DESC")
    fun observeForVariant(variantId: Long): Flow<List<StockAdjustmentEntity>>

    @Query("SELECT * FROM stock_adjustments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StockAdjustmentEntity>>
}
