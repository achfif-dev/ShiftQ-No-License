package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.AuditLogRepository
import com.example.posapp.data.repository.UserRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManagementUiState(
    val users: List<UserEntity> = emptyList(),
    val pinLoginEnabled: Boolean = false,
    val autoLockMinutes: Int = 5
)

sealed class UserManagementEvent {
    data class ShowMessage(val message: String) : UserManagementEvent()
}

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val storeProfileRepository: StoreProfileRepository,
    private val sessionManager: SessionManager,
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<UserManagementEvent>()
    val events: SharedFlow<UserManagementEvent> = _events

    val uiState: StateFlow<UserManagementUiState> = combine(
        userRepository.observeAll(), storeProfileRepository.profile
    ) { users, profile ->
        UserManagementUiState(
            users = users,
            pinLoginEnabled = profile.pinLoginEnabled,
            autoLockMinutes = profile.autoLockMinutes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserManagementUiState())

    fun setPinLoginEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // BUG SERIUS terkait (ditemukan bersamaan dengan bug di deleteUser() di atas):
            // sebelumnya hanya mensyaratkan "ada minimal 1 user" APAPUN rolenya. Kalau toko
            // baru menambahkan Kasir dulu (tanpa Admin) lalu langsung mengaktifkan PIN login,
            // rute "settings"/"user_management" langsung terkunci untuk SEMUA orang (keduanya
            // admin-only) — tidak ada yang bisa membuat Admin atau mematikan PIN lagi. Sekarang
            // disyaratkan minimal 1 ADMIN aktif, bukan sekadar 1 user apa pun.
            if (enabled && userRepository.countActiveAdmins() <= 0) {
                _events.emit(UserManagementEvent.ShowMessage("Tambahkan minimal satu pengguna dengan role Admin terlebih dahulu"))
                return@launch
            }
            storeProfileRepository.setPinLoginEnabled(enabled)
        }
    }

    /** @param minutes 0 untuk mematikan auto-lock idle (kunci-saat-background tetap selalu aktif). */
    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { storeProfileRepository.updateAutoLockMinutes(minutes) }
    }

    fun addUser(name: String, pin: String, role: UserRole) {
        if (name.isBlank() || pin.length < 4) {
            viewModelScope.launch { _events.emit(UserManagementEvent.ShowMessage("Nama & PIN (min 4 digit) wajib diisi")) }
            return
        }
        viewModelScope.launch {
            try {
                userRepository.createUser(name, pin, role)
                auditLogRepository.log(
                    actorName = sessionManager.currentUser.value?.name ?: "Admin",
                    actorRole = sessionManager.currentUser.value?.role,
                    action = "TAMBAH_USER",
                    description = "Menambahkan pengguna \"$name\" dengan role ${role.name}"
                )
                _events.emit(UserManagementEvent.ShowMessage("Pengguna \"$name\" ditambahkan"))
            } catch (e: Exception) {
                _events.emit(UserManagementEvent.ShowMessage("Gagal menambah pengguna: nama mungkin sudah dipakai"))
            }
        }
    }

    /**
     * BUG SERIUS (ditemukan saat audit menyeluruh): sebelumnya fungsi ini menghapus (soft-delete)
     * user APA PUN tanpa syarat. Kalau toko hanya punya satu Admin dan Admin itu terhapus
     * (sengaja atau salah pencet) sementara PIN login aktif, TIDAK ADA LAGI cara masuk ke
     * Pengaturan/Manajemen Pengguna — halaman itu sendiri admin-only — jadi toko permanen
     * terkunci dari fitur manajemennya sendiri kecuali hapus data aplikasi (kehilangan semua
     * transaksi). Sekarang dicegah: kalau user yang dihapus adalah Admin DAN dialah satu-satunya
     * Admin aktif yang tersisa, penghapusan ditolak dengan pesan jelas.
     */
    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            if (user.role == UserRole.ADMIN && userRepository.countActiveAdmins() <= 1) {
                _events.emit(
                    UserManagementEvent.ShowMessage(
                        "Tidak bisa menghapus \"${user.name}\" — ini Admin aktif terakhir. " +
                            "Tambahkan Admin lain dulu sebelum menghapus akun ini, supaya toko " +
                            "tidak kehilangan akses ke Pengaturan."
                    )
                )
                return@launch
            }
            userRepository.deleteUser(user.id)
            auditLogRepository.log(
                actorName = sessionManager.currentUser.value?.name ?: "Admin",
                actorRole = sessionManager.currentUser.value?.role,
                action = "HAPUS_USER",
                description = "Menghapus pengguna \"${user.name}\" (role ${user.role.name})"
            )
            _events.emit(UserManagementEvent.ShowMessage("${user.name} dihapus"))
        }
    }
}
