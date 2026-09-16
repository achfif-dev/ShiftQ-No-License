package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.posapp.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getById(id: Long): SupplierEntity?

    @Insert
    suspend fun insert(supplier: SupplierEntity): Long

    @Update
    suspend fun update(supplier: SupplierEntity)
}
