package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.backup.BackupRepository
import com.example.posapp.data.backup.BackupResult
import com.example.posapp.data.backup.RestoreAuditLog
import com.example.posapp.data.export.ExcelExporter
import com.example.posapp.data.export.FileShareHelper
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class SettingsEvent {
    data class ExportReady(val file: File, val mimeType: String) : SettingsEvent()
    data class ShowMessage(val message: String) : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val excelExporter: ExcelExporter,
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val fileShareHelper: FileShareHelper,
    private val sessionManager: SessionManager,
    private val restoreAuditLog: RestoreAuditLog
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events

    private val _backups = MutableStateFlow<List<File>>(emptyList())
    val backups: StateFlow<List<File>> = _backups.asStateFlow()

    init {
        refreshBackups()
    }

    private fun refreshBackups() {
        _backups.value = backupRepository.listLocalBackups()
    }

    fun backupNow(password: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { backupRepository.backup(password) }) {
                is BackupResult.Success -> {
                    refreshBackups()
                    _events.emit(SettingsEvent.ExportReady(result.file, "application/octet-stream"))
                }
                is BackupResult.Error -> _events.emit(SettingsEvent.ShowMessage(result.message))
            }
        }
    }

    fun restoreFrom(file: File, password: String) {
        viewModelScope.launch {
            // Dicatat SEBELUM restore, ke FILE TEKS terpisah (bukan tabel audit_logs Room) —
            // lihat dokumentasi RestoreAuditLog untuk alasannya: restore menimpa seluruh file
            // database, jadi log yang ditulis ke Room (sebelum ATAU sesudah) akan ikut hilang.
            restoreAuditLog.append(
                actorName = sessionManager.currentUser.value?.name ?: "Admin",
                actorRole = sessionManager.currentUser.value?.role?.name ?: "-",
                sourceFileName = file.name
            )
            when (val result = withContext(Dispatchers.IO) { backupRepository.restore(file, password) }) {
                is BackupResult.Success -> _events.emit(
                    SettingsEvent.ShowMessage("Restore berhasil. Silakan tutup dan buka ulang aplikasi.")
                )
                is BackupResult.Error -> _events.emit(SettingsEvent.ShowMessage(result.message))
            }
        }
    }

    fun listLocalBackups(): List<File> = backupRepository.listLocalBackups()

    /** Dipakai UI untuk memutuskan apakah dialog restore perlu menampilkan kolom password. */
    fun isEncryptedBackup(file: File): Boolean = backupRepository.isEncryptedBackup(file)

    /** Hapus satu file backup lokal dari Riwayat Backup Lokal (tidak memengaruhi data aplikasi). */
    fun deleteBackup(file: File) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { backupRepository.deleteBackup(file) }
            if (deleted) {
                refreshBackups()
                _events.emit(SettingsEvent.ShowMessage("Backup \"${file.name}\" dihapus"))
            } else {
                _events.emit(SettingsEvent.ShowMessage("Gagal menghapus file backup"))
            }
        }
    }

    /** Menyalin file yang sudah di-export ke lokasi pilihan pengguna (mis. folder Download). */
    fun saveExportToUri(file: File, destinationUri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { fileShareHelper.saveToUri(file, destinationUri) }
            _events.emit(
                if (ok) SettingsEvent.ShowMessage("File tersimpan di perangkat.")
                else SettingsEvent.ShowMessage("Gagal menyimpan file ke perangkat.")
            )
        }
    }

    fun exportProductsExcel() {
        viewModelScope.launch {
            val products = productRepository.getAllForExport()
            val file = withContext(Dispatchers.IO) { excelExporter.exportProducts(products) }
            _events.emit(SettingsEvent.ExportReady(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        }
    }

    fun exportTransactionsExcel() {
        viewModelScope.launch {
            val transactions = transactionRepository.observeAll().first()
            val file = withContext(Dispatchers.IO) { excelExporter.exportTransactions(transactions) }
            _events.emit(SettingsEvent.ExportReady(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        }
    }

    fun exportProductsCsv() {
        viewModelScope.launch {
            val products = productRepository.getAllForExport()
            val file = withContext(Dispatchers.IO) { excelExporter.exportProductsCsv(products) }
            _events.emit(SettingsEvent.ExportReady(file, "text/csv"))
        }
    }
}
