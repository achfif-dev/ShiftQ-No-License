package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.posapp.data.local.entity.ParkedSaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkedSaleDao {

    @Query("SELECT * FROM parked_sales ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ParkedSaleEntity>>

    @Query("SELECT * FROM parked_sales WHERE id = :id")
    suspend fun getById(id: Long): ParkedSaleEntity?

    @Insert
    suspend fun insert(parkedSale: ParkedSaleEntity): Long

    @Query("DELETE FROM parked_sales WHERE id = :id")
    suspend fun delete(id: Long)
}
