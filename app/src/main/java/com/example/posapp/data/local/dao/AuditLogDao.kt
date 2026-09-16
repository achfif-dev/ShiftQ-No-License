package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.posapp.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLogEntity)

    // Dibatasi 500 baris terbaru — cukup untuk keperluan audit sehari-hari tanpa membebani UI
    // dengan histori tak terbatas. Data lama tetap ada di database, cuma tidak ikut ditampilkan.
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<AuditLogEntity>>
}
