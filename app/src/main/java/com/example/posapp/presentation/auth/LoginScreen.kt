package com.example.posapp.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Layar login PIN. Muncul di awal aplikasi ketika fitur "Login PIN" diaktifkan
 * dari Pengaturan, atau otomatis saat pertama kali app dibuka (setup PIN admin pertama).
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onLoginSuccess()
    }

    Scaffold { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val isCompactHeight = maxHeight < 480.dp

            if (isCompactHeight) {
                // Layar pendek (landscape ponsel): header & keypad berdampingan + bisa discroll
                // agar tombol "Konfirmasi" tidak pernah terpotong oleh navigation bar.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoginHeader(isFirstRun = uiState.isFirstRun)
                        Spacer(Modifier.height(16.dp))
                        PinDots(length = uiState.pin.length)
                        uiState.errorMessage?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    NumericKeypad(
                        onDigit = viewModel::onDigit,
                        onBackspace = viewModel::onBackspace,
                        onSubmit = viewModel::submit,
                        isLoading = uiState.isLoading,
                        lockoutSecondsRemaining = uiState.lockoutSecondsRemaining
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LoginHeader(isFirstRun = uiState.isFirstRun)

                    Spacer(Modifier.height(32.dp))
                    PinDots(length = uiState.pin.length)

                    uiState.errorMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(32.dp))
                    NumericKeypad(
                        onDigit = viewModel::onDigit,
                        onBackspace = viewModel::onBackspace,
                        onSubmit = viewModel::submit,
                        isLoading = uiState.isLoading,
                        lockoutSecondsRemaining = uiState.lockoutSecondsRemaining
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginHeader(isFirstRun: Boolean) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isFirstRun) Icons.Default.Storefront else Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        if (isFirstRun) "Selamat Datang!" else "Masuk",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        if (isFirstRun) "Buat PIN admin untuk mengamankan aplikasi kasir ini"
        else "Masukkan PIN untuk melanjutkan",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PinDots(length: Int, maxLength: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(maxLength) { index ->
            val filled = index < length
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    // > 0 selama device masih dalam masa lockout PIN (lihat LoginAttemptRepository) --
    // tombol Konfirmasi dinonaktifkan & menampilkan hitung mundur, supaya jelas kenapa PIN
    // yang sudah benar sekalipun sementara tidak bisa dipakai masuk.
    lockoutSecondsRemaining: Long = 0L
) {
    val isLocked = lockoutSecondsRemaining > 0
    // Grid manual (bukan LazyVerticalGrid) karena keypad ini dipakai di dalam Column yang
    // bisa discroll (verticalScroll) — menaruh layout lazy (yang juga scrollable) di dalam
    // Column scrollable akan crash ("infinity maximum height constraints").
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "back")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(64.dp))
                        "back" -> KeypadButton(icon = Icons.AutoMirrored.Filled.Backspace, onClick = onBackspace)
                        else -> KeypadButton(label = key, onClick = { onDigit(key) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier.width(260.dp).height(50.dp),
            enabled = !isLoading && !isLocked
        ) {
            Text(
                when {
                    isLocked -> "Coba lagi dalam ${lockoutSecondsRemaining}d"
                    isLoading -> "Memproses..."
                    else -> "Konfirmasi"
                }
            )
        }
    }
}

@Composable
private fun KeypadButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.headlineSmall)
            } else if (icon != null) {
                Icon(icon, contentDescription = "Hapus")
            }
        }
    }
}
