package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Jejak aktivitas untuk aksi-aksi sensitif (Void transaksi, Retur barang, Koreksi transaksi,
 * tambah/hapus Pengguna, Restore backup). TIDAK mencatat SETIAP aksi di aplikasi (mis. transaksi
 * jual normal tidak dicatat di sini — itu sudah ada jejaknya sendiri di tabel transactions) —
 * hanya aksi yang berpotensi disalahgunakan atau butuh pertanggungjawaban siapa-melakukan-apa.
 * Tabel ini sengaja tidak punya foreign key ke mana pun (append-only, tidak ikut CASCADE delete
 * apa pun) supaya jejaknya tidak pernah hilang meski data terkait di tabel lain sudah dihapus.
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorName: String,       // nama user yang melakukan aksi ("Admin" default kalau PIN login nonaktif)
    val actorRole: String,       // snapshot role saat itu (ADMIN/MANAGER/KASIR/"-"), String biasa (bukan UserRole) supaya nilai lama tidak rusak kalau enum berubah nanti
    val action: String,          // kode aksi singkat, mis. "VOID_TRANSAKSI", "RETUR_BARANG", "KOREKSI_TRANSAKSI", "TAMBAH_USER", "HAPUS_USER", "RESTORE_BACKUP"
    val description: String,     // detail yang siap ditampilkan apa adanya di layar Log Aktivitas
    val createdAt: Long = System.currentTimeMillis()
)
