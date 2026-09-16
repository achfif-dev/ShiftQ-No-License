package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.posapp.data.local.entity.PromoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromoDao {

    // Dipakai baik untuk layar Kelola Promo (semua, termasuk nonaktif/kedaluwarsa) maupun oleh
    // PromoEngine di PosViewModel — filter aktif & jendela waktu sengaja dilakukan di PromoEngine
    // pakai System.currentTimeMillis() yang selalu segar, bukan di query, supaya promo yang baru
    // saja lewat tanggal berakhirnya langsung berhenti diterapkan tanpa perlu query ulang.
    @Query("SELECT * FROM promos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PromoEntity>>

    @Insert
    suspend fun insert(promo: PromoEntity): Long

    @Update
    suspend fun update(promo: PromoEntity)

    @Query("DELETE FROM promos WHERE id = :id")
    suspend fun delete(id: Long)
}
