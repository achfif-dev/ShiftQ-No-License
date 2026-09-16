package com.example.posapp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.repository.UserRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Menentukan apakah layar login harus ditampilkan.
 *
 * PENTING (perbaikan bug): sebelumnya keputusan ini HANYA bergantung pada preferensi
 * `pinLoginEnabled` (default false). Tapi preferensi itu cuma bisa diaktifkan lewat menu
 * Pengaturan > Pengguna, yang mensyaratkan sudah ada user — sementara satu-satunya cara
 * membuat user pertama (mode "Buat PIN Admin" di LoginScreen) cuma muncul kalau layar
 * login sudah tampil. Akibatnya layar login tidak akan PERNAH muncul di instalasi baru.
 *
 * Sekarang gate memakai kondisi: tampilkan login jika BELUM ada user sama sekali
 * (wajib setup admin pertama) ATAU jika pinLoginEnabled aktif dan belum ada sesi login.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val userRepository: UserRepository,
    storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _requiresLogin = MutableStateFlow<Boolean?>(null) // null = masih dicek
    val requiresLogin: StateFlow<Boolean?> = _requiresLogin.asStateFlow()

    init {
        viewModelScope.launch {
            storeProfileRepository.profile.collect { profile ->
                val hasUser = userRepository.hasAnyUser()
                _requiresLogin.value = !hasUser || profile.pinLoginEnabled
            }
        }
    }
}
