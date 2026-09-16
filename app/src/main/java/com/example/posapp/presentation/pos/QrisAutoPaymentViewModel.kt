package com.example.posapp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.payment.GatewayResult
import com.example.posapp.data.payment.PaymentGatewayRepository
import com.example.posapp.data.payment.QrisCharge
import com.example.posapp.data.payment.QrisChargeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrisAutoUiState(
    val isConfigured: Boolean = false,
    val isCreating: Boolean = false,
    val charge: QrisCharge? = null,
    val status: QrisChargeStatus = QrisChargeStatus.PENDING,
    val error: String? = null,
)

/**
 * Menjembatani PosScreen dengan [PaymentGatewayRepository] khusus untuk kartu "QRIS Otomatis"
 * di dialog pembayaran — terpisah dari PosViewModel utama supaya tidak menambah kerumitan state
 * checkout yang sudah ada, dan supaya bisa dipakai/dites independen.
 */
@HiltViewModel
class QrisAutoPaymentViewModel @Inject constructor(
    private val gatewayRepository: PaymentGatewayRepository,
) : ViewModel() {

    val isConfigured: StateFlow<Boolean> = gatewayRepository.isConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow(QrisAutoUiState())
    val uiState: StateFlow<QrisAutoUiState> = _uiState.asStateFlow()

    // BUG PENTING (ditemukan saat audit ulang): sebelum ada ini, setiap createCharge() dipanggil
    // (mis. kasir batal lalu buat ulang QRIS di dialog yang sama, atau ViewModel dipakai ulang
    // lintas beberapa transaksi karena scope-nya bertahan selama layar Kasir tidak ditinggalkan
    // — lihat komentar di PosScreen.kt) akan menambah SATU LAGI collector Firestore listener
    // tanpa membatalkan yang lama. Listener lama & baru sama-sama menulis ke _uiState yang sama,
    // jadi status charge LAMA yang telat datang bisa menimpa status charge BARU yang lebih valid
    // (race condition — bisa membuat transaksi baru "kelihatan" lunas padahal belum, atau
    // sebaliknya). [observeJob] memastikan cuma ADA SATU listener aktif kapan pun.
    private var observeJob: Job? = null

    /** @param orderId Sebaiknya ID unik sementara (belum tentu nomor invoice final — invoice baru
     * dibuat setelah checkout tersimpan) supaya bisa dicocokkan lewat webhook Midtrans. */
    fun createCharge(orderId: String, amountRupiah: Long) {
        observeJob?.cancel()
        _uiState.value = QrisAutoUiState(isCreating = true)
        viewModelScope.launch {
            when (val result = gatewayRepository.createCharge(orderId, amountRupiah)) {
                is GatewayResult.Success -> {
                    _uiState.value = QrisAutoUiState(charge = result.data, status = QrisChargeStatus.PENDING)
                    observeJob = launch {
                        gatewayRepository.observeChargeStatus(orderId).collect { status ->
                            _uiState.value = _uiState.value.copy(status = status)
                        }
                    }
                }
                is GatewayResult.Error -> {
                    _uiState.value = QrisAutoUiState(error = result.message)
                }
            }
        }
    }

    fun reset() {
        observeJob?.cancel()
        observeJob = null
        _uiState.value = QrisAutoUiState()
    }
}
