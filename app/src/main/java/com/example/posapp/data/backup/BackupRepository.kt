package com.example.posapp.data.backup

import android.content.Context
import com.example.posapp.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val file: File) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

/**
 * Backup & restore dengan cara paling andal untuk Room: baca/tulis file database SQLite secara
 * utuh (setelah WAL checkpoint) alih-alih export ke JSON manual, supaya semua relasi & integritas
 * data tetap terjaga persis seperti kondisi asli.
 *
 * Backup SELALU keluar terenkripsi (AES-256-GCM lewat [BackupCrypto], ekstensi `.posbak`) —
 * sebelumnya file backup adalah salinan `.db` mentah yang bisa dibaca siapa saja kalau HP hilang
 * atau file tersalin ke perangkat lain (termasuk hash PIN & seluruh riwayat transaksi di
 * dalamnya). Restore tetap mendukung file `.db` lama tanpa password untuk kompatibilitas
 * mundur dengan backup yang sudah dibuat pengguna sebelum fitur ini ada.
 *
 * PENTING: Restore mengharuskan AppDatabase dalam keadaan tertutup (db.close()) sebelum file
 * ditimpa, lalu proses harus di-restart agar Room membuka kembali file yang baru. Cara paling
 * aman di Compose: setelah restore sukses, tampilkan pesan lalu minta pengguna membuka ulang app.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase
) {
    private val dbFile: File get() = context.getDatabasePath(AppDatabase.DATABASE_NAME)
    private val backupDir: File get() = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    /** @param password Wajib diisi (min 6 karakter) — dipakai untuk mengenkripsi hasil backup.
     * TIDAK disimpan oleh aplikasi; kalau lupa, file backup ini tidak bisa dipulihkan lagi. */
    fun backup(password: String): BackupResult {
        return try {
            if (!dbFile.exists()) return BackupResult.Error("Database belum memiliki data untuk di-backup")
            if (password.length < 6) return BackupResult.Error("Password backup minimal 6 karakter")

            // Checkpoint WAL dulu supaya semua perubahan sudah tertulis ke file utama sebelum dibaca.
            appDatabase.query("PRAGMA wal_checkpoint(FULL)", null).use { /* no-op, cursor hanya untuk trigger checkpoint */ }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "pos_backup_$timestamp.posbak")
            BackupCrypto.encrypt(source = dbFile, destination = backupFile, password = password)
            BackupResult.Success(backupFile)
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Gagal membuat backup")
        }
    }

    /**
     * @param password Wajib cocok kalau [sourceFile] berformat `.posbak` terenkripsi (dicek lewat
     * isi file, bukan ekstensi nama file — lihat [BackupCrypto.hasMagic]). Diabaikan untuk file
     * backup lama format mentah (dari versi app sebelum fitur enkripsi ini ada).
     */
    fun restore(sourceFile: File, password: String): BackupResult {
        var tempDecrypted: File? = null
        return try {
            if (!sourceFile.exists()) return BackupResult.Error("File backup tidak ditemukan")

            val restoreSource: File = if (BackupCrypto.hasMagic(sourceFile)) {
                if (password.isBlank()) return BackupResult.Error("Masukkan password backup")
                val temp = File(context.cacheDir, "restore_${System.currentTimeMillis()}.db")
                try {
                    BackupCrypto.decrypt(source = sourceFile, destination = temp, password = password)
                } catch (e: BackupCrypto.WrongPasswordException) {
                    return BackupResult.Error("Password salah atau file backup rusak")
                }
                tempDecrypted = temp
                temp
            } else {
                sourceFile
            }

            appDatabase.close()

            // Hapus file -wal dan -shm lama agar tidak konflik dengan database hasil restore.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            restoreSource.copyTo(dbFile, overwrite = true)
            BackupResult.Success(dbFile)
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Gagal melakukan restore. Pastikan file backup & password valid.")
        } finally {
            tempDecrypted?.delete()
        }
    }

    /** Dipakai UI untuk memutuskan apakah perlu menampilkan kolom password saat me-restore
     * file tertentu dari Riwayat Backup Lokal atau dari file picker. */
    fun isEncryptedBackup(file: File): Boolean = BackupCrypto.hasMagic(file)

    fun listLocalBackups(): List<File> {
        return backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Menghapus satu file backup lokal dari Riwayat Backup Lokal. */
    fun deleteBackup(file: File): Boolean {
        return try {
            file.exists() && file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
