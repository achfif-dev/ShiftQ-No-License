package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// MANAGER: peran baru di antara KASIR dan ADMIN — bisa jual & retur seperti Kasir, PLUS akses
// Beban Usaha (catat pengeluaran operasional toko), tapi TIDAK bisa: Void transaksi, koreksi
// harga/qty transaksi, kelola Pengguna & PIN, ubah Profil Toko/pajak, backup/restore, atau akses
// Cloud Sync & Multi-Outlet — semua itu tetap murni ADMIN-only (lihat Permission.kt).
enum class UserRole { ADMIN, MANAGER, KASIR }

/**
 * Pengguna aplikasi (pemilik toko/admin & kasir) untuk fitur login PIN. PIN asli tidak pernah
 * disimpan.
 *
 * [pinHash] + [pinSalt]: sejak v10, PIN baru di-hash dengan PBKDF2WithHmacSHA256 + salt acak
 * per-user (lihat UserRepository) — PIN kasir biasanya cuma 4-6 digit, jadi hash tanpa salt
 * (skema lama) rentan rainbow table kalau file database bocor. [pinSalt] bernilai null untuk
 * akun lama yang PIN-nya masih memakai skema SHA-256 polos (sebelum v10); akun tsb otomatis
 * di-upgrade ke PBKDF2+salt begitu berhasil login berikutnya, jadi field ini boleh null hanya
 * sementara sampai user itu login lagi.
 */
@Entity(tableName = "users", indices = [Index("name", unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String,
    val pinSalt: String? = null,
    val role: UserRole = UserRole.KASIR,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
