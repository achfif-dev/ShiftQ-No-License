package com.example.posapp.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Data yang dikumpulkan sepanjang langkah-langkah Onboarding Wizard, sebelum disimpan sekaligus
 * ke [StoreProfileRepository] di langkah terakhir ("Selesai"). Nilai awal sudah masuk akal
 * sebagai default kalau pengguna memilih "Lewati" di tengah jalan. */
data class OnboardingUiState(
    val step: Int = 0,
    val storeName: String = "",
    val address: String = "",
    val phone: String = "",
    val businessType: String = "GENERAL",
    val appColorHex: String? = null,
    val taxEnabled: Boolean = true,
    val taxPercent: String = "11",
    val isSaving: Boolean = false,
    val finished: Boolean = false
) {
    val totalSteps: Int = 4
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onStoreNameChange(value: String) { _uiState.value = _uiState.value.copy(storeName = value) }
    fun onAddressChange(value: String) { _uiState.value = _uiState.value.copy(address = value) }
    fun onPhoneChange(value: String) { _uiState.value = _uiState.value.copy(phone = value) }
    fun onBusinessTypeChange(value: String) { _uiState.value = _uiState.value.copy(businessType = value) }
    fun onColorChange(hex: String?) { _uiState.value = _uiState.value.copy(appColorHex = hex) }
    fun onTaxEnabledChange(enabled: Boolean) { _uiState.value = _uiState.value.copy(taxEnabled = enabled) }
    fun onTaxPercentChange(value: String) { _uiState.value = _uiState.value.copy(taxPercent = value) }

    fun nextStep() {
        val current = _uiState.value
        if (current.step < current.totalSteps - 1) {
            _uiState.value = current.copy(step = current.step + 1)
        }
    }

    fun previousStep() {
        val current = _uiState.value
        if (current.step > 0) {
            _uiState.value = current.copy(step = current.step - 1)
        }
    }

    /** Simpan seluruh pilihan wizard dan tandai onboarding selesai. */
    fun finish() {
        val current = _uiState.value
        if (current.isSaving) return
        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            storeProfileRepository.completeOnboarding(
                name = current.storeName.ifBlank { "Toko Saya" },
                address = current.address,
                phone = current.phone,
                businessType = current.businessType,
                appColorHex = current.appColorHex,
                taxEnabled = current.taxEnabled,
                taxPercent = current.taxPercent.toDoubleOrNull() ?: 11.0
            )
            _uiState.value = _uiState.value.copy(isSaving = false, finished = true)
        }
    }

    /** "Lewati" — tidak mengubah profil toko, cuma menandai wizard sudah pernah dilihat. */
    fun skip() {
        val current = _uiState.value
        if (current.isSaving) return
        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            storeProfileRepository.skipOnboarding()
            _uiState.value = _uiState.value.copy(isSaving = false, finished = true)
        }
    }
}
