package com.example.posapp.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.export.FileShareHelper
import com.example.posapp.data.export.PdfInvoiceGenerator
import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.printer.PrintResult
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.printer.toPrinterConfig
import com.example.posapp.data.repository.ExpenseRepository
import com.example.posapp.data.repository.ReturnItemRequest
import com.example.posapp.data.repository.ReturnValidationException
import com.example.posapp.data.repository.ReturnWithItems
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.data.settings.StoreProfileRepository
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
import java.util.Calendar
import javax.inject.Inject

enum class ReportRangePreset { TODAY, THIS_WEEK, THIS_MONTH, CUSTOM }

data class ReportUiState(
    val preset: ReportRangePreset = ReportRangePreset.TODAY,
    val startMillis: Long = startOfToday(),
    val endMillis: Long = System.currentTimeMillis(),
    val summary: DailySalesSummary = DailySalesSummary(0.0, 0, 0.0),
    val totalExpenses: Double = 0.0, // Total Beban Usaha (diprorata) untuk periode terpilih — hanya dihitung/ditampilkan untuk Admin
    val netProfit: Double = 0.0, // Laba Bersih = Laba Kotor - Total Beban Usaha — ringkasan khusus Admin
    val topItems: List<TopSellingItem> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(), // riwayat penjualan periode terpilih
    val isAdmin: Boolean = false, // HANYA Admin: boleh koreksi harga/qty transaksi & Void — Manager tetap TIDAK termasuk ini
    val canViewExpenses: Boolean = false, // Admin & Manager: boleh lihat Laba Bersih & kelola Beban Usaha
    val isLoading: Boolean = false
)

/** Detail satu transaksi (header + item) yang sedang dibuka/dikoreksi lewat Riwayat Penjualan. */
data class TransactionDetailUiState(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity>,
    val returnHistory: List<ReturnWithItems> = emptyList(), // riwayat retur/void transaksi ini
    val isSaving: Boolean = false
)

sealed class ReportEvent {
    data class ShowMessage(val message: String) : ReportEvent()
    data class PdfReady(val file: File) : ReportEvent() // invoice PDF hasil export ulang dari Riwayat Penjualan, siap dibagikan
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val printerRepository: PrinterRepository,
    private val pdfInvoiceGenerator: PdfInvoiceGenerator,
    private val fileShareHelper: FileShareHelper,
    private val storeProfileRepository: StoreProfileRepository,
    private val productRepository: com.example.posapp.data.repository.ProductRepository,
    private val auditLogRepository: com.example.posapp.data.repository.AuditLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow<TransactionDetailUiState?>(null)
    val detailState: StateFlow<TransactionDetailUiState?> = _detailState.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>()
    val events: SharedFlow<ReportEvent> = _events

    init {
        // Sama seperti guard peran di Pengaturan: user null (login PIN tidak wajib) dianggap Admin.
        val currentUser = sessionManager.currentUser.value
        val isAdmin = currentUser == null || currentUser.role == UserRole.ADMIN
        val canViewExpenses = isAdmin || currentUser?.role == UserRole.MANAGER
        _uiState.value = _uiState.value.copy(isAdmin = isAdmin, canViewExpenses = canViewExpenses)
        load()
    }

    fun selectPreset(preset: ReportRangePreset) {
        val calendar = Calendar.getInstance()
        val start: Long
        when (preset) {
            ReportRangePreset.TODAY -> {
                start = startOfToday()
            }
            ReportRangePreset.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                start = calendar.timeInMillis
            }
            ReportRangePreset.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                start = calendar.timeInMillis
            }
            ReportRangePreset.CUSTOM -> start = _uiState.value.startMillis
        }
        _uiState.value = _uiState.value.copy(preset = preset, startMillis = start, endMillis = System.currentTimeMillis())
        load()
    }

    fun setCustomRange(start: Long, end: Long) {
        _uiState.value = _uiState.value.copy(preset = ReportRangePreset.CUSTOM, startMillis = start, endMillis = end)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = _uiState.value
            val summary = transactionRepository.getSalesSummary(state.startMillis, state.endMillis)
            val topItems = transactionRepository.getTopSellingItems(state.startMillis, state.endMillis)
            val transactions = transactionRepository.observeRange(state.startMillis, state.endMillis).first()
            // Laba Bersih menyingkap struktur biaya toko — hanya dihitung untuk Admin & Manager.
            val totalExpenses = if (state.canViewExpenses) {
                expenseRepository.getTotalForRange(state.startMillis, state.endMillis)
            } else 0.0
            _uiState.value = _uiState.value.copy(
                summary = summary,
                topItems = topItems,
                transactions = transactions,
                totalExpenses = totalExpenses,
                netProfit = summary.totalGrossProfit - totalExpenses,
                isLoading = false
            )
        }
    }

    /** Buka detail satu transaksi dari daftar Riwayat Penjualan (lihat saja untuk Kasir, bisa dikoreksi untuk Admin). */
    fun openTransactionDetail(transactionId: Long) {
        viewModelScope.launch {
            val result = transactionRepository.getTransactionWithItems(transactionId)
            if (result == null) {
                _events.emit(ReportEvent.ShowMessage("Transaksi tidak ditemukan"))
                return@launch
            }
            val (transaction, items) = result
            val returnHistory = transactionRepository.getReturnHistory(transactionId)
            _detailState.value = TransactionDetailUiState(transaction, items, returnHistory)
        }
    }

    fun closeTransactionDetail() {
        _detailState.value = null
    }

    /**
     * Simpan koreksi Admin atas riwayat penjualan (mis. kasir salah input qty/harga/produk).
     * [updatedItems] hanya berisi item yang MASIH ADA (sudah termasuk perubahan qty/harga/diskon);
     * [deletedItemIds] berisi item yang dihapus total dari transaksi. Stok disesuaikan otomatis.
     */
    fun saveTransactionCorrection(
        updatedTransaction: TransactionEntity,
        updatedItems: List<TransactionItemEntity>,
        deletedItemIds: List<Long>
    ) {
        val current = _detailState.value ?: return
        if (updatedItems.isEmpty()) {
            viewModelScope.launch { _events.emit(ReportEvent.ShowMessage("Transaksi harus punya minimal 1 item. Gunakan tombol \"Hapus Transaksi\" bila ingin menghapus seluruhnya.")) }
            return
        }
        viewModelScope.launch {
            // Pertahanan lapis kedua (fail-closed) — lihat catatan di voidTransaction() di bawah
            // untuk kenapa cek ini tidak cukup hanya di UI. Audit menyeluruh menemukan fungsi ini
            // sebelumnya TIDAK PERNAH memeriksa role sama sekali di sini.
            val profile = storeProfileRepository.profile.first()
            val allowed = com.example.posapp.domain.auth.Permission.canCorrectTransaction(
                sessionManager.currentUser.value, profile.pinLoginEnabled
            )
            if (!allowed) {
                _events.emit(ReportEvent.ShowMessage("Hanya Admin yang boleh mengoreksi transaksi"))
                return@launch
            }
            _detailState.value = current.copy(isSaving = true)
            val editorName = sessionManager.currentUser.value?.name ?: "Admin"
            transactionRepository.updateTransactionWithCorrection(
                updatedTransaction = updatedTransaction,
                originalItems = current.items,
                updatedItems = updatedItems,
                deletedItemIds = deletedItemIds,
                editedByName = editorName
            )
            auditLogRepository.log(
                actorName = editorName,
                actorRole = sessionManager.currentUser.value?.role,
                action = "KOREKSI_TRANSAKSI",
                description = "Mengoreksi transaksi ${updatedTransaction.invoiceNumber} " +
                    "(${current.items.size} item asli -> ${updatedItems.size} item, ${deletedItemIds.size} item dihapus)"
            )
            _detailState.value = null
            _events.emit(ReportEvent.ShowMessage("Riwayat penjualan berhasil dikoreksi"))
            load()
        }
    }

    /**
     * Membatalkan (VOID) SATU transaksi sepenuhnya — pengganti fungsi hapus permanen yang lama.
     * Beda dari hapus permanen: transaksi tetap tersimpan (statusnya jadi VOIDED) supaya ada
     * jejak audit, hanya dikeluarkan dari perhitungan Laporan. Stok dikembalikan otomatis.
     * Admin-only: tombol memang disembunyikan untuk non-Admin di UI (lihat `isAdmin` di
     * [ReportUiState]), tapi itu BUKAN satu-satunya penghalang — [Permission.canVoidTransaction]
     * dicek ULANG di sini juga (fail-closed), sama seperti pola pertahanan lapis kedua yang
     * dipakai StockViewModel.adjustStock untuk Opname (audit menyeluruh berikutnya menemukan
     * fungsi ini sebelumnya HANYA mengandalkan UI, celah yang sama persis yang sudah diperbaiki
     * di rute lain pada audit 2026-09-06 — sekarang konsisten).
     */
    fun voidTransaction(transactionId: Long, reason: String) {
        val invoiceNumber = _detailState.value?.transaction?.invoiceNumber ?: "#$transactionId"
        viewModelScope.launch {
            val profile = storeProfileRepository.profile.first()
            val allowed = com.example.posapp.domain.auth.Permission.canVoidTransaction(
                sessionManager.currentUser.value, profile.pinLoginEnabled
            )
            if (!allowed) {
                _events.emit(ReportEvent.ShowMessage("Hanya Admin yang boleh membatalkan (void) transaksi"))
                return@launch
            }
            _detailState.value = _detailState.value?.copy(isSaving = true)
            val byName = sessionManager.currentUser.value?.name ?: "Admin"
            try {
                transactionRepository.voidTransaction(transactionId, reason, byName)
                auditLogRepository.log(
                    actorName = byName,
                    actorRole = sessionManager.currentUser.value?.role,
                    action = "VOID_TRANSAKSI",
                    description = "Membatalkan (void) transaksi $invoiceNumber — alasan: \"$reason\""
                )
                _detailState.value = null
                _events.emit(ReportEvent.ShowMessage("Transaksi berhasil dibatalkan (void)"))
                load()
            } catch (e: ReturnValidationException) {
                _detailState.value = _detailState.value?.copy(isSaving = false)
                _events.emit(ReportEvent.ShowMessage(e.message ?: "Gagal membatalkan transaksi"))
            }
        }
    }

    /**
     * Memproses retur barang (sebagian atau seluruh item) untuk transaksi yang sedang dibuka di
     * detail Riwayat Penjualan. Tersedia untuk Kasir maupun Admin (lihat Permission.canProcessReturn)
     * — alasan retur wajib diisi di layer UI sebelum fungsi ini dipanggil.
     */
    fun processReturn(
        items: List<ReturnItemRequest>,
        reason: String,
        refundAmount: Double,
        refundMethod: PaymentMethod?
    ) {
        val transactionId = _detailState.value?.transaction?.id ?: return
        val invoiceNumber = _detailState.value?.transaction?.invoiceNumber ?: "#$transactionId"
        viewModelScope.launch {
            _detailState.value = _detailState.value?.copy(isSaving = true)
            val processedByName = sessionManager.currentUser.value?.name ?: "Kasir"
            try {
                transactionRepository.processReturn(
                    transactionId = transactionId,
                    items = items,
                    reason = reason,
                    refundAmount = refundAmount,
                    refundMethod = refundMethod,
                    processedByName = processedByName
                )
                auditLogRepository.log(
                    actorName = processedByName,
                    actorRole = sessionManager.currentUser.value?.role,
                    action = "RETUR_BARANG",
                    description = "Memproses retur ${items.size} item transaksi $invoiceNumber, " +
                        "refund ${refundAmount.toInt()} (${refundMethod?.name ?: "-"}) — alasan: \"$reason\""
                )
                _detailState.value = null
                _events.emit(ReportEvent.ShowMessage("Retur berhasil diproses"))
                load()
            } catch (e: ReturnValidationException) {
                _detailState.value = _detailState.value?.copy(isSaving = false)
                _events.emit(ReportEvent.ShowMessage(e.message ?: "Gagal memproses retur"))
            }
        }
    }

    /**
     * Cetak ulang struk (thermal printer Bluetooth) untuk transaksi LAMA yang sedang dibuka di
     * detail Riwayat Penjualan — dipakai saat pelanggan minta invoice/struk susulan, tidak harus
     * langsung dicetak saat transaksi selesai. Tersedia untuk Admin maupun Kasir (bukan aksi
     * yang mengubah data, jadi tidak perlu digembok seperti koreksi/hapus transaksi).
     */
    fun printTransaction() {
        val detail = _detailState.value ?: return
        viewModelScope.launch {
            val profile = storeProfileRepository.profile.first()
            val skuMap = if (profile.receiptShowSku) {
                detail.items.map { it.productId }.distinct()
                    .mapNotNull { productId -> productRepository.findById(productId)?.let { productId to it.sku } }
                    .toMap()
            } else emptyMap()
            val result = withContext(Dispatchers.IO) {
                printerRepository.printReceipt(
                    storeName = profile.name,
                    transaction = detail.transaction,
                    items = detail.items,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter,
                    logoImagePath = profile.logoImagePath,
                    language = profile.receiptLanguage,
                    printerConfig = profile.toPrinterConfig(),
                    headerNote = profile.receiptHeaderNote,
                    showSku = profile.receiptShowSku,
                    skuByProductId = skuMap
                )
            }
            when (result) {
                is PrintResult.Success -> _events.emit(ReportEvent.ShowMessage("Struk berhasil dicetak"))
                is PrintResult.Error -> _events.emit(ReportEvent.ShowMessage(result.message))
            }
        }
    }

    /** Buat ulang invoice PDF untuk transaksi lama, siap dibagikan (WhatsApp, email, dll). */
    fun exportTransactionPdf() {
        val detail = _detailState.value ?: return
        viewModelScope.launch {
            val profile = storeProfileRepository.profile.first()
            val skuMap = if (profile.receiptShowSku) {
                detail.items.map { it.productId }.distinct()
                    .mapNotNull { productId -> productRepository.findById(productId)?.let { productId to it.sku } }
                    .toMap()
            } else emptyMap()
            val file = withContext(Dispatchers.IO) {
                pdfInvoiceGenerator.generate(
                    storeName = profile.name,
                    transaction = detail.transaction,
                    items = detail.items,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter,
                    logoImagePath = profile.logoImagePath,
                    language = profile.receiptLanguage,
                    headerNote = profile.receiptHeaderNote,
                    showSku = profile.receiptShowSku,
                    skuByProductId = skuMap
                )
            }
            _events.emit(ReportEvent.PdfReady(file))
        }
    }

    fun createShareIntent(file: File) = fileShareHelper.createShareIntent(file, "application/pdf")
}
