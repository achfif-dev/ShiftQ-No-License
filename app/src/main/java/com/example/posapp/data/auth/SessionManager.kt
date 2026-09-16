package com.example.posapp.data.auth

import com.example.posapp.data.local.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyimpan status login saat ini secara in-memory (bukan persisten) — sengaja demikian
 * supaya setiap kali app dibuka ulang, kasir/admin wajib login PIN lagi bila fitur ini aktif.
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    fun login(user: UserEntity) {
        _currentUser.value = user
    }

    fun logout() {
        _currentUser.value = null
    }
}
