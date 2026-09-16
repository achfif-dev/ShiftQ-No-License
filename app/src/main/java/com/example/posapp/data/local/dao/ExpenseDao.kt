package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.posapp.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    /** Hanya beban aktif — dipakai saat menghitung Laba Bersih di Laporan. */
    @Query("SELECT * FROM expenses WHERE isActive = 1")
    suspend fun getActive(): List<ExpenseEntity>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
