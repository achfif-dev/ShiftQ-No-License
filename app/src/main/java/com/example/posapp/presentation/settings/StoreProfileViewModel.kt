package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.qris.QrisImageDecoder
import com.example.posapp.data.settings.StoreProfile
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.posapp.data.settings.quickCashAmountList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StoreProfileEvent {
    data class ShowMessage(val message: String) : StoreProfileEvent()
}

@HiltViewModel
class StoreProfileViewModel @Inject constructor(
    private val storeProfileRepository: StoreProfileRepository,
    private val qrisImageDecoder: QrisImageDecoder,
    private val printerRepository: PrinterRepository
) : ViewModel() {

    // PERBAIKAN BUG (layar putih macet setelah auto-lock, terutama di HP yang agresif membunuh
    // proses background seperti Vivo/FuntouchOS): `uiState` di bawah SELALU mulai dari
    // StoreProfile() default (pinLoginEnabled = false) sebelum data asli dari DataStore selesai
    // dibaca -- MainActivity butuh cara membedakan "belum tahu" dari "memang pinLoginEnabled =
    // false", supaya tidak terlanjur merender layar terproteksi dengan asumsi keliru "tidak perlu
    // login" tepat saat proses baru saja dibuat ulang OS. `isLoaded` jadi true SEKALI setelah
    // emisi pertama data asli tiba.
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val uiState: StateFlow<StoreProfile> = storeProfileRepository.profile
        .onEach { _isLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreProfile())

    private val _events = MutableSharedFlow<StoreProfileEvent>()
    val events: SharedFlow<StoreProfileEvent> = _events

    fun save(name: String, address: String, phone: String, footer: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(StoreProfileEvent.ShowMessage("Nama toko tidak boleh kosong")) }
            return
        }
        viewModelScope.launch {
            storeProfileRepository.update(name.trim(), address.trim(), phone.trim(), footer.trim())
            _events.emit(StoreProfileEvent.ShowMessage("Profil toko tersimpan"))
        }
    }

    /**
     * Simpan gambar QRIS baru, lalu coba baca ulang kode QR di dalamnya untuk mendapatkan
     * payload EMVCo mentah — bila berhasil, QRIS Dinamis (nominal otomatis) aktif di layar
     * pembayaran; bila gagal (gambar bukan kode QR EMVCo yang valid), fallback ke gambar statis.
     */
    fun setQrisImagePath(path: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateQrisImagePath(path)
            if (path == null) {
                storeProfileRepository.updateQrisRawContent(null)
                _events.emit(StoreProfileEvent.ShowMessage("Gambar QRIS dihapus"))
                return@launch
            }
            val rawContent = qrisImageDecoder.decode(path)
            // v: audit ulang QRIS dinamis -- sebelumnya di sini cuma dicek `rawContent != null`
            // (artinya "ML Kit berhasil baca SATU barcode apa saja"), bukan apakah isinya benar
            // payload QRIS. Sekarang divalidasi strukturnya lewat QrisUtil.isValidQris (cek
            // field wajib + CRC cocok) sebelum dianggap sah -- kalau tidak valid, DIANGGAP SAMA
            // seperti gagal dibaca (rawContent disimpan null), supaya tidak salah mengklaim
            // "QRIS Dinamis aktif" untuk QR yang sebenarnya bukan QRIS sama sekali.
            val validatedRawContent = rawContent?.takeIf { com.example.posapp.data.qris.QrisUtil.isValidQris(it) }
            storeProfileRepository.updateQrisRawContent(validatedRawContent)
            _events.emit(
                StoreProfileEvent.ShowMessage(
                    when {
                        validatedRawContent != null -> "Gambar QRIS tersimpan — QRIS Dinamis aktif (nominal otomatis)"
                        rawContent != null -> "Gambar QRIS tersimpan, tapi kode QR yang terbaca BUKAN QRIS yang valid — nominal tidak akan otomatis terisi. Pastikan foto memang QRIS pembayaran, bukan kode QR lain."
                        else -> "Gambar QRIS tersimpan, tapi kode QR tidak terbaca — nominal tidak akan otomatis terisi"
                    }
                )
            )
        }
    }

    fun setPinLoginEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setPinLoginEnabled(enabled) }
    }

    /** Simpan logo toko baru (dipakai di header struk cetak & PDF invoice), atau null untuk menghapus. */
    fun setLogoImagePath(path: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateLogoImagePath(path)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (path != null) "Logo toko tersimpan" else "Logo toko dihapus")
            )
        }
    }

    /** @param hex Format "#RRGGBB", atau null untuk kembali ke warna default aplikasi. */
    fun setAppColor(hex: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateAppColorHex(hex)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (hex != null) "Warna aplikasi diperbarui" else "Warna aplikasi dikembalikan ke default")
            )
        }
    }

    /** Ganti font tampilan aplikasi (lihat PosFontOption di presentation/theme/Font.kt). */
    fun setFontChoice(fontKey: String) {
        viewModelScope.launch {
            storeProfileRepository.updateFontChoice(fontKey)
            _events.emit(StoreProfileEvent.ShowMessage("Font aplikasi diperbarui"))
        }
    }

    /** Ganti bahasa yang dipakai di struk cetak & PDF invoice ("id" atau "en"). */
    fun setReceiptLanguage(languageCode: String) {
        viewModelScope.launch {
            storeProfileRepository.updateReceiptLanguage(languageCode)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (languageCode == "en") "Bahasa struk diubah ke English" else "Bahasa struk diubah ke Indonesia")
            )
        }
    }

    /** Aktifkan/nonaktifkan pajak (PPN) & atur persentasenya. Berlaku untuk transaksi berikutnya. */
    fun setTaxSettings(enabled: Boolean, percent: Double) {
        viewModelScope.launch {
            storeProfileRepository.updateTaxSettings(enabled, percent.coerceIn(0.0, 100.0))
            _events.emit(
                StoreProfileEvent.ShowMessage(if (enabled) "Pajak diaktifkan (${percent}%)" else "Pajak dinonaktifkan")
            )
        }
    }

    /** Atur batas maksimum diskon manual (persen) yang boleh diberikan Kasir biasa tanpa
     * Admin/Manager login sendiri — lihat DiscountPolicy.kt untuk penjelasan lengkap. */
    fun setMaxKasirDiscountPercent(percent: Int) {
        viewModelScope.launch {
            val clamped = percent.coerceIn(0, 100)
            storeProfileRepository.updateMaxKasirDiscountPercent(clamped)
            _events.emit(StoreProfileEvent.ShowMessage("Batas diskon manual Kasir diatur ke $clamped%"))
        }
    }

    /** Tambah satu nominal cepat Cash baru (mis. 50000) ke daftar tombol cepat di layar Pembayaran. */
    fun addQuickCashAmount(amount: Long) {
        if (amount <= 0) return
        viewModelScope.launch {
            val current = storeProfileRepository.profile.first().quickCashAmountList()
            val updated = (current + amount).distinct().sorted()
            storeProfileRepository.updateQuickCashAmounts(updated.joinToString(","))
            _events.emit(StoreProfileEvent.ShowMessage("Nominal cepat ditambahkan"))
        }
    }

    /** Hapus satu nominal cepat Cash dari daftar tombol cepat di layar Pembayaran. */
    fun removeQuickCashAmount(amount: Long) {
        viewModelScope.launch {
            val current = storeProfileRepository.profile.first().quickCashAmountList()
            val updated = current.filter { it != amount }
            storeProfileRepository.updateQuickCashAmounts(updated.joinToString(","))
            _events.emit(StoreProfileEvent.ShowMessage("Nominal cepat dihapus"))
        }
    }

    /** Daftar nama printer Bluetooth yang sudah di-pair di sistem, untuk pilihan di UI Pengaturan
     * (dulu fungsi ini sudah ada di PrinterRepository tapi tidak pernah dipakai di mana pun —
     * struk selalu tercetak ke printer pertama yang ditemukan walau toko punya >1 printer). */
    fun listPairedPrinters(): List<String> = printerRepository.listPairedBluetoothPrinters()

    /** Daftar printer USB yang terdeteksi tersambung (lihat Pengaturan > Profil Toko > Printer). */
    fun listUsbPrinters(): List<String> = printerRepository.listUsbPrinters()

    /** @param name Nama printer yang dipilih pengguna dari [listPairedPrinters], atau null untuk
     * kembali ke perilaku default (pakai printer ter-pairing pertama). */
    fun setSelectedPrinter(name: String?) {
        viewModelScope.launch {
            storeProfileRepository.setSelectedPrinterName(name)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (name != null) "Printer struk diatur ke \"$name\"" else "Kembali memakai printer ter-pairing pertama")
            )
        }
    }

    /** @param type "BLUETOOTH", "LAN", atau "USB". Dipanggil dari layar Pengaturan Printer. */
    fun setPrinterConnection(type: String, lanIp: String, lanPort: Int, paperWidthMm: Float) {
        viewModelScope.launch {
            storeProfileRepository.setPrinterConfig(type, lanIp, lanPort, paperWidthMm)
            _events.emit(StoreProfileEvent.ShowMessage("Pengaturan printer disimpan"))
        }
    }

    /** @param type "RETAIL", "FNB", atau "GENERAL" — lihat StoreProfile.businessType. */
    fun setBusinessType(type: String) {
        viewModelScope.launch {
            storeProfileRepository.updateBusinessType(type)
            _events.emit(StoreProfileEvent.ShowMessage("Tipe bisnis diperbarui"))
        }
    }

    /** Aktifkan/nonaktifkan program poin loyalitas & atur nilai tukarnya. */
    fun setLoyaltySettings(enabled: Boolean, rupiahPerPoint: Long, pointValueRupiah: Long) {
        viewModelScope.launch {
            storeProfileRepository.updateLoyaltySettings(enabled, rupiahPerPoint, pointValueRupiah)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (enabled) "Poin loyalitas diaktifkan" else "Poin loyalitas dinonaktifkan")
            )
        }
    }

    fun setWhatsappReceiptEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setWhatsappReceiptEnabled(enabled) }
    }

    fun setTableTaggingEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setTableTaggingEnabled(enabled) }
    }

    fun setSupplierPoEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setSupplierPoEnabled(enabled) }
    }

    fun setLowStockNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setLowStockNotificationsEnabled(enabled) }
    }

    /** Jangka waktu (hari) sebelum piutang BON dianggap jatuh tempo — dipakai transaksi BON
     * BERIKUTNYA, tidak mengubah dueDate transaksi yang sudah tersimpan. */
    fun updateBonDueDays(days: Int) {
        viewModelScope.launch {
            storeProfileRepository.updateBonDueDays(days)
            _events.emit(StoreProfileEvent.ShowMessage("Jangka waktu jatuh tempo BON diperbarui"))
        }
    }

    fun updateReceiptCustomization(showSku: Boolean, headerNote: String) {
        viewModelScope.launch {
            storeProfileRepository.updateReceiptCustomization(showSku, headerNote)
            _events.emit(StoreProfileEvent.ShowMessage("Tampilan struk diperbarui"))
        }
    }
}
