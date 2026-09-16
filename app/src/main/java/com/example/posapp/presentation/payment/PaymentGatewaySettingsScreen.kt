package com.example.posapp.presentation.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Self-service: toko mendaftar akun Midtrans SENDIRI (https://midtrans.com), lalu tempel
 * Merchant ID/Server Key/Client Key dari dashboard Midtrans masing-masing di sini. Developer
 * aplikasi TIDAK PERNAH melihat/menyentuh kredensial ini — langsung terenkripsi TLS ke Cloud
 * Function dan disimpan server-side, bukan di HP maupun repo developer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewaySettingsScreen(onBack: () -> Unit) {
    val viewModel: PaymentGatewaySettingsViewModel = hiltViewModel()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var merchantId by remember { mutableStateOf("") }
    var serverKey by remember { mutableStateOf("") }
    var clientKey by remember { mutableStateOf("") }
    var isProduction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Gateway (QRIS Otomatis)") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (isConfigured) "Status: Terhubung ✓" else "Status: Belum Terhubung",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dengan QRIS Otomatis, pembayaran pelanggan terkonfirmasi LUNAS secara " +
                            "otomatis di layar Kasir (tanpa cek manual) begitu masuk ke rekening " +
                            "Midtrans toko Anda. QRIS statis manual (Pengaturan > Profil Toko) tetap " +
                            "tersedia sebagai cadangan kalau internet mati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Cara mendapatkan Merchant ID/Server Key/Client Key:", style = MaterialTheme.typography.labelLarge)
            Text(
                "1. Daftar/login di dashboard.midtrans.com (gratis, akun toko sendiri)\n" +
                    "2. Settings > Access Keys — salin Merchant ID, Server Key, Client Key\n" +
                    "3. Tempel di bawah ini lalu tekan Simpan & Uji Koneksi",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = merchantId, onValueChange = { merchantId = it },
                label = { Text("Merchant ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = serverKey, onValueChange = { serverKey = it },
                label = { Text("Server Key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = clientKey, onValueChange = { clientKey = it },
                label = { Text("Client Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = isProduction, onCheckedChange = { isProduction = it })
                Spacer(Modifier.width(8.dp))
                Text(if (isProduction) "Mode Produksi (transaksi asli)" else "Mode Sandbox (uji coba, tidak charge asli)")
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveCredentials(merchantId, serverKey, clientKey, isProduction) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                else Text("Simpan & Uji Koneksi")
            }

            if (isConfigured) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Putuskan Payment Gateway")
                }
            }

            uiState.message?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
