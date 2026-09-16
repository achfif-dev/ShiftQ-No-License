package com.example.posapp.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Log super sederhana KHUSUS event Restore Backup, disimpan sebagai file teks biasa di
 * penyimpanan internal app — SENGAJA di luar database Room (bukan tabel `audit_logs` biasa).
 *
 * Alasannya: [BackupRepository.restore] menutup lalu MENIMPA seluruh file database (termasuk
 * tabel audit_logs) dengan isi file backup lama. Kalau jejak "siapa melakukan restore kapan"
 * disimpan di dalam database yang sama, jejak itu justru ikut hilang/tertimpa oleh restore itu
 * sendiri — audit trail yang paling penting justru yang paling gampang hilang. File teks di luar
 * database ini tidak tersentuh oleh proses restore, jadi riwayatnya selalu utuh lintas restore.
 */
@Singleton
class RestoreAuditLog @Inject constructor(@ApplicationContext private val context: Context) {
    private val file: File get() = File(context.filesDir, "restore_audit_log.txt")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("id", "ID"))

    /** Dipanggil SEBELUM BackupRepository.restore() — lihat SettingsViewModel.restoreFrom. */
    fun append(actorName: String, actorRole: String, sourceFileName: String) {
        try {
            val safeName = sourceFileName.replace("|", "_").replace("\n", " ")
            val safeActor = actorName.replace("|", "_").replace("\n", " ")
            file.appendText("${dateFormat.format(Date())}|$safeActor|$actorRole|$safeName\n")
        } catch (_: Exception) {
            // Gagal mencatat log TIDAK BOLEH menggagalkan proses restore itu sendiri.
        }
    }

    data class Entry(val timestamp: String, val actorName: String, val actorRole: String, val sourceFileName: String)

    fun readAll(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines()
                .mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size == 4) Entry(parts[0], parts[1], parts[2], parts[3]) else null
                }
                .reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
