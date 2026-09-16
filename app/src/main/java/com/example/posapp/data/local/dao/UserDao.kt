package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY role ASC, name ASC")
    fun observeAll(): Flow<List<UserEntity>>

    // Setiap user punya salt sendiri (PBKDF2), jadi hash PIN tidak bisa dicocokkan lewat WHERE
    // di SQL secara langsung — verifikasi dilakukan di UserRepository per-user dengan salt-nya
    // masing-masing. Jumlah user di satu toko kecil (kasir/admin), jadi ini tetap murah.
    @Query("SELECT * FROM users WHERE isActive = 1")
    suspend fun getAllActive(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1")
    suspend fun countActive(): Int

    // Dipakai UserRepository.deleteUser sebagai jaring pengaman terakhir supaya toko tidak
    // pernah kehilangan akses Admin sama sekali (lihat catatan lengkap di UserRepository).
    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1 AND role = 'ADMIN'")
    suspend fun countActiveAdmins(): Int

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)
}
