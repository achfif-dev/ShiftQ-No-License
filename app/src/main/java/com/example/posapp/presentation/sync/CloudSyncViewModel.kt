package com.example.posapp.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.settings.StoreProfile
import com.example.posapp.data.settings.StoreProfileRepository
import com.example.posapp.data.sync.CloudSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val storeProfileRepository: StoreProfileRepository,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    val profile: StateFlow<StoreProfile> = storeProfileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreProfile())

    /** Terlihat terkonfigurasi (google-services.json ada) atau tidak — dicek sekali, cukup untuk
     * menampilkan peringatan di UI kalau Admin menyalakan toggle padahal Firebase belum disiapkan. */
    fun isCloudConfigured(): Boolean = cloudSyncRepository.isConfigured()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setCloudSyncEnabled(enabled) }
    }

    fun updateOutletName(name: String) {
        viewModelScope.launch { storeProfileRepository.updateOutletName(name) }
    }

    /** Dipanggil sekali saat layar dibuka supaya outletId sudah pasti ada sebelum toggle dinyalakan. */
    fun ensureOutletId() {
        viewModelScope.launch { storeProfileRepository.ensureOutletId() }
    }

    /** Dipanggil sekali saat layar dibuka supaya kode grup sinkronisasi sudah pasti ada
     * (dibuat otomatis kalau belum pernah) sebelum ditampilkan/disalin ke cabang lain. */
    fun ensureSyncGroupCode() {
        viewModelScope.launch { storeProfileRepository.ensureSyncGroupCode() }
    }

    /** @param code Kode grup baru — isi manual untuk BERGABUNG ke grup cabang lain (salin persis
     * dari cabang utama), atau kosongkan untuk membuat kode acak baru (mis. keluar dari grup lama). */
    fun updateSyncGroupCode(code: String) {
        viewModelScope.launch { storeProfileRepository.updateSyncGroupCode(code) }
    }
}
