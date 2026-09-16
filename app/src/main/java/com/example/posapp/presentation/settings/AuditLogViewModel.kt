package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.backup.RestoreAuditLog
import com.example.posapp.data.repository.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Satu baris tampilan Log Aktivitas — bisa berasal dari tabel audit_logs (Room) ATAU dari file
 * restore_audit_log.txt (khusus event Restore Backup, lihat RestoreAuditLog untuk alasannya). */
data class AuditLogRow(
    val timestampLabel: String,
    val timestampMillis: Long,
    val actorName: String,
    val actorRole: String,
    val action: String,
    val description: String
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository,
    private val restoreAuditLog: RestoreAuditLog
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    private val restoreDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("id", "ID"))

    private val _rows = MutableStateFlow<List<AuditLogRow>>(emptyList())
    val rows: StateFlow<List<AuditLogRow>> = _rows.asStateFlow()

    init {
        viewModelScope.launch {
            auditLogRepository.observeRecent().collect { dbLogs ->
                val dbRows = dbLogs.map { log ->
                    AuditLogRow(
                        timestampLabel = dateFormat.format(Date(log.createdAt)),
                        timestampMillis = log.createdAt,
                        actorName = log.actorName,
                        actorRole = log.actorRole,
                        action = log.action,
                        description = log.description
                    )
                }
                // File restore_audit_log.txt dibaca ulang tiap kali audit_logs berubah (bukan flow
                // reaktif tersendiri) — cukup karena restore jarang terjadi & layar ini biasanya
                // dibuka manual dari menu Pengaturan, bukan terus-menerus dipantau live.
                val restoreRows = restoreAuditLog.readAll().mapNotNull { entry ->
                    val millis = try {
                        restoreDateFormat.parse(entry.timestamp)?.time
                    } catch (_: Exception) {
                        null
                    } ?: return@mapNotNull null
                    AuditLogRow(
                        timestampLabel = dateFormat.format(Date(millis)),
                        timestampMillis = millis,
                        actorName = entry.actorName,
                        actorRole = entry.actorRole,
                        action = "RESTORE_BACKUP",
                        description = "Me-restore database dari file cadangan \"${entry.sourceFileName}\" — SELURUH data toko ditimpa"
                    )
                }
                _rows.value = (dbRows + restoreRows).sortedByDescending { it.timestampMillis }
            }
        }
    }
}
