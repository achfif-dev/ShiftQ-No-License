package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.posapp.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    // Hanya boleh ada satu shift OPEN pada satu waktu (per device/toko) — kasir berikutnya
    // harus melanjutkan shift yang sama atau menunggu shift sebelumnya ditutup dulu.
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Query("SELECT * FROM shifts ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftEntity?

    @Insert
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)
}
