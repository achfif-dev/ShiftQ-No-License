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

    /** Versi skema Room yang dikenal build ini — WAJIB dinaikkan bersamaan dengan
     * `@Database(version = ...)` di AppDatabase, lihat pengecekan di [restore]. */
    private val CURRENT_SCHEMA_VERSION = AppDatabase.SCHEMA_VERSION
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
     *
     * PERBAIKAN (audit — restore bisa menghancurkan data tanpa jalan pulang): versi sebelumnya
     * langsung `copyTo(dbFile, overwrite = true)` tanpa pengaman apa pun. Tiga hal yang bisa
     * membuat seluruh data toko hilang permanen, semuanya sekarang ditutup:
     *
     * 1. TIDAK ADA salinan pengaman. Kalau apa pun gagal di tengah penimpaan, database asli
     *    sudah telanjur rusak dan tidak ada jalan kembali. Sekarang DB lama disalin dulu ke
     *    `pos_database.prerestore` dan dikembalikan otomatis kalau langkah mana pun gagal.
     * 2. TIDAK ADA verifikasi hasil dekripsi. File hasil dekripsi tidak pernah dicek benar-benar
     *    database SQLite atau bukan. Sekarang header 16-byte SQLite ("SQLite format 3\u0000")
     *    diperiksa sebelum file itu dipercaya.
     * 3. TIDAK ADA pengecekan versi skema. Me-restore backup yang dibuat APK lebih BARU ke APK
     *    lama akan kena `fallbackToDestructiveMigrationOnDowngrade` dan MENGHAPUS SEMUANYA saat
     *    app dibuka lagi. Sekarang `PRAGMA user_version` file backup dibaca dulu dan restore
     *    DITOLAK kalau versinya lebih tinggi dari skema yang dikenal app ini, dengan pesan yang
     *    memberi tahu pengguna untuk memperbarui aplikasi lebih dulu.
     */
    fun restore(sourceFile: File, password: String): BackupResult {
        var tempDecrypted: File? = null
        var safetyCopy: File? = null
        return try {
            if (!sourceFile.exists()) return BackupResult.Error("File backup tidak ditemukan")

            val restoreSource: File = if (BackupCrypto.hasMagic(sourceFile)) {
                if (password.isBlank()) return BackupResult.Error("Masukkan password backup")
                val temp = File(context.cacheDir, "restore_${System.currentTimeMillis()}.db")
                try {
                    BackupCrypto.decrypt(source = sourceFile, destination = temp, password = password)
                } catch (e: BackupCrypto.WrongPasswordException) {
                    temp.delete()
                    return BackupResult.Error("Password salah atau file backup rusak")
                }
                tempDecrypted = temp
                temp
            } else {
                sourceFile
            }

            if (!isSqliteFile(restoreSource)) {
                return BackupResult.Error(
                    "File ini bukan database yang valid (mungkin password salah atau file rusak). " +
                        "Data lama TIDAK diubah."
                )
            }

            val backupSchemaVersion = readSchemaVersion(restoreSource)
            if (backupSchemaVersion != null && backupSchemaVersion > CURRENT_SCHEMA_VERSION) {
                return BackupResult.Error(
                    "Backup ini dibuat oleh versi aplikasi yang lebih baru (skema v$backupSchemaVersion, " +
                        "aplikasi ini v$CURRENT_SCHEMA_VERSION). Perbarui aplikasi dulu, baru restore. " +
                        "Data lama TIDAK diubah."
                )
            }

            appDatabase.close()

            // Salinan pengaman SEBELUM apa pun ditimpa — satu-satunya jalan pulang kalau
            // penyalinan di bawah gagal di tengah jalan (storage penuh, proses dimatikan, dll).
            if (dbFile.exists()) {
                val copy = File(dbFile.path + ".prerestore")
                dbFile.copyTo(copy, overwrite = true)
                safetyCopy = copy
            }

            // Hapus file -wal dan -shm lama agar tidak konflik dengan database hasil restore.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            try {
                restoreSource.copyTo(dbFile, overwrite = true)
            } catch (e: Exception) {
                safetyCopy?.let { runCatching { it.copyTo(dbFile, overwrite = true) } }
                return BackupResult.Error(
                    "Restore gagal di tengah proses, database lama sudah dikembalikan: ${e.message ?: "-"}"
                )
            }

            // Sukses: salinan pengaman disimpan (tidak dihapus) sampai restore berikutnya, supaya
            // pemilik toko masih punya jalan pulang kalau ternyata salah pilih file backup.
            BackupResult.Success(dbFile)
        } catch (e: Exception) {
            safetyCopy?.let { runCatching { it.copyTo(dbFile, overwrite = true) } }
            BackupResult.Error(e.message ?: "Gagal melakukan restore. Pastikan file backup & password valid.")
        } finally {
            tempDecrypted?.delete()
        }
    }

    /** Header wajib setiap file SQLite 3: "SQLite format 3" + byte 0. */
    private fun isSqliteFile(file: File): Boolean = try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(16)
            raf.readFully(header)
            String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
        }
    } catch (e: Exception) {
        false
    }

    /** `PRAGMA user_version` disimpan SQLite sebagai big-endian 4 byte di offset 60 — dibaca
     * langsung dari file supaya tidak perlu membuka database yang belum tentu bisa dibuka. */
    private fun readSchemaVersion(file: File): Int? = try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(60)
            val b = ByteArray(4)
            raf.readFully(b)
            ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        }
    } catch (e: Exception) {
        null
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
