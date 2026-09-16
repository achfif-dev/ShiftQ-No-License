package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.posapp.data.local.entity.CashMovementEntity
import com.example.posapp.data.local.entity.CashMovementType
import kotlinx.coroutines.flow.Flow

@Dao
interface CashMovementDao {

    @Insert
    suspend fun insert(movement: CashMovementEntity): Long

    @Query("SELECT * FROM cash_movements WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun observeForShift(shiftId: Long): Flow<List<CashMovementEntity>>

    // Dipakai saat tutup shift: total kas masuk/keluar non-penjualan untuk shift ini, dipisah
    // per tipe supaya bisa ditampilkan terpisah di ringkasan tutup kasir (bukan cuma net-nya).
    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_movements WHERE shiftId = :shiftId AND type = :type")
    suspend fun getMovementTotal(shiftId: Long, type: CashMovementType): Double
}
