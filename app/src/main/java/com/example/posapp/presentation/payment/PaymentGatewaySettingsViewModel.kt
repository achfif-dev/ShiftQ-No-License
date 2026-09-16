package com.example.posapp.presentation.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.payment.GatewayCredentialsInput
import com.example.posapp.data.payment.GatewayResult
import com.example.posapp.data.payment.PaymentGatewayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentGatewayUiState(
    val isSaving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class PaymentGatewaySettingsViewModel @Inject constructor(
    private val repository: PaymentGatewayRepository,
) : ViewModel() {

    val isConfigured: StateFlow<Boolean> = repository.isConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow(PaymentGatewayUiState())
    val uiState: StateFlow<PaymentGatewayUiState> = _uiState.asStateFlow()

    fun saveCredentials(merchantId: String, serverKey: String, clientKey: String, isProduction: Boolean) {
        if (merchantId.isBlank() || serverKey.isBlank() || clientKey.isBlank()) {
            _uiState.value = PaymentGatewayUiState(message = "Merchant ID, Server Key, dan Client Key wajib diisi.", isError = true)
            return
        }
        _uiState.value = PaymentGatewayUiState(isSaving = true)
        viewModelScope.launch {
            val result = repository.saveCredentials(
                GatewayCredentialsInput(merchantId, serverKey, clientKey, isProduction)
            )
            _uiState.value = when (result) {
                is GatewayResult.Success -> PaymentGatewayUiState(message = "Payment gateway berhasil terhubung.", isError = false)
                is GatewayResult.Error -> PaymentGatewayUiState(message = result.message, isError = true)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.clearCredentials()
            _uiState.value = PaymentGatewayUiState(message = "Payment gateway diputuskan.", isError = false)
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
