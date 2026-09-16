package com.example.posapp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.LoginAttemptRepository
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.UserRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val pin: String = "",
    val isFirstRun: Boolean = false, // belum ada user sama sekali -> minta buat PIN admin pertama
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    /** > 0 selama device masih dalam masa lockout akibat terlalu banyak PIN salah beruntun --
     * lihat [LoginAttemptRepository]. UI (LoginScreen) memakai ini untuk menonaktifkan tombol
     * submit & menghitung mundur, bukan cuma menampilkan errorMessage sekali saja. */
    val lockoutSecondsRemaining: Long = 0L
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val storeProfileRepository: StoreProfileRepository,
    private val loginAttemptRepository: LoginAttemptRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hasUser = userRepository.hasAnyUser()
            _uiState.value = _uiState.value.copy(isFirstRun = !hasUser)
        }
        // Kalau layar Login ini dibuka ULANG (app di-restart/di-force-stop) di TENGAH masa
        // lockout yang sudah berjalan, tetap tampilkan hitung mundurnya -- lockout tersimpan
        // di DataStore (LoginAttemptRepository), bukan di ViewModel ini, jadi tidak boleh
        // "hilang" hanya karena ViewModel dibuat ulang.
        watchLockout()
    }

    /** Menghitung mundur [LoginUiState.lockoutSecondsRemaining] setiap detik selama device
     * masih terkunci, supaya tombol submit otomatis aktif lagi begitu masa lockout habis tanpa
     * pengguna perlu keluar-masuk layar. */
    private fun watchLockout() {
        viewModelScope.launch {
            while (true) {
                val lock = loginAttemptRepository.currentLockState()
                _uiState.value = _uiState.value.copy(lockoutSecondsRemaining = lock?.remainingSeconds ?: 0L)
                if (lock == null) return@launch
                delay(1_000L)
            }
        }
    }

    fun onDigit(digit: String) {
        val current = _uiState.value
        if (current.pin.length >= 6) return
        _uiState.value = current.copy(pin = current.pin + digit, errorMessage = null)
    }

    fun onBackspace() {
        val current = _uiState.value
        _uiState.value = current.copy(pin = current.pin.dropLast(1), errorMessage = null)
    }

    fun onClear() {
        _uiState.value = _uiState.value.copy(pin = "", errorMessage = null)
    }

    fun submit() {
        val pin = _uiState.value.pin
        if (pin.length < 4) {
            _uiState.value = _uiState.value.copy(errorMessage = "PIN minimal 4 digit")
            return
        }
        viewModelScope.launch {
            // Pembuatan PIN admin pertama BUKAN percobaan menebak PIN yang sudah ada (belum ada
            // user sama sekali) -- tidak melewati/tidak dihitung ke lockout sama sekali.
            if (_uiState.value.isFirstRun) {
                _uiState.value = _uiState.value.copy(isLoading = true)
                userRepository.createUser("Admin", pin, UserRole.ADMIN)
                // Ambil kembali entity yang baru dibuat (dengan pinHash yang benar) alih-alih
                // memakai entity kosong, supaya sesi login konsisten dengan data di database.
                val createdUser = userRepository.login(pin)
                if (createdUser != null) {
                    sessionManager.login(createdUser)
                    // Admin pertama sudah dibuat -> aktifkan proteksi PIN secara otomatis
                    // supaya aplikasi benar-benar meminta login di pembukaan berikutnya.
                    storeProfileRepository.setPinLoginEnabled(true)
                }
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                return@launch
            }

            // TEMUAN KEAMANAN (audit ulang): sebelumnya tidak ada batas percobaan PIN gagal sama
            // sekali -- lihat komentar lengkap di LoginAttemptRepository. Cek lockout di SINI
            // (bukan cuma mengandalkan UI menonaktifkan tombol) supaya tetap fail-closed walau
            // tombol submit sempat ditekan lewat cara lain (mis. automation/accessibility).
            val existingLock = loginAttemptRepository.currentLockState()
            if (existingLock != null) {
                _uiState.value = _uiState.value.copy(
                    pin = "",
                    lockoutSecondsRemaining = existingLock.remainingSeconds,
                    errorMessage = "Terlalu banyak percobaan gagal. Coba lagi dalam ${existingLock.remainingSeconds} detik."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            val user = userRepository.login(pin)
            if (user != null) {
                loginAttemptRepository.recordSuccess()
                sessionManager.login(user)
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
            } else {
                val newLock = loginAttemptRepository.recordFailure()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pin = "",
                    lockoutSecondsRemaining = newLock?.remainingSeconds ?: 0L,
                    errorMessage = newLock?.let {
                        "Terlalu banyak percobaan gagal. Coba lagi dalam ${it.remainingSeconds} detik."
                    } ?: "PIN salah, coba lagi"
                )
                if (newLock != null) watchLockout()
            }
        }
    }
}
