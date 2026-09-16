package com.example.posapp.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.presentation.theme.PosBrandedTopBar

/**
 * Pengaturan Sinkronisasi Cloud (Fase 4 - fondasi Multi-Cabang). Nonaktif secara default —
 * fitur ini mengirim RINGKASAN OMZET HARIAN (bukan detail transaksi/produk/pelanggan) cabang
 * ini ke Firestore, supaya bisa digabung di layar Ringkasan Semua Cabang. Butuh proyek Firebase
 * sendiri, lihat FIREBASE_SETUP.md di root repo untuk panduan setup lengkap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: CloudSyncViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    var outletName by remember(profile.outletName) { mutableStateOf(profile.outletName) }
    var groupCodeInput by remember(profile.syncGroupCode) { mutableStateOf(profile.syncGroupCode) }
    val isConfigured = remember { viewModel.isCloudConfigured() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.ensureOutletId()
        viewModel.ensureSyncGroupCode()
    }

    Scaffold(
        topBar = {
            PosBrandedTopBar(
                title = { Text("Sinkronisasi Cloud") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!isConfigured) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Firebase belum dikonfigurasi",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Ikuti panduan di FIREBASE_SETUP.md (root repo) untuk membuat proyek Firebase " +
                                    "sendiri sebelum menyalakan sinkronisasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = outletName,
                onValueChange = { outletName = it },
                label = { Text("Nama Cabang Ini") },
                placeholder = { Text("Contoh: Cabang Kelapa Gading") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Tampil di layar Ringkasan Semua Cabang") }
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { viewModel.updateOutletName(outletName) },
                enabled = outletName.isNotBlank() && outletName != profile.outletName
            ) { Text("Simpan Nama Cabang") }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Kode Grup Sinkronisasi", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Cabang-cabang yang memakai kode YANG SAMA PERSIS akan saling melihat data lewat " +
                    "Sinkronisasi Cloud & Cek Stok Semua Cabang. Salin kode ini ke cabang lain untuk " +
                    "menggabungkannya ke grup toko yang sama — tidak perlu aktivasi apa pun.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = groupCodeInput,
                    onValueChange = { groupCodeInput = it.uppercase() },
                    label = { Text("Kode Grup") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { clipboard.setText(AnnotatedString(profile.syncGroupCode)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin kode")
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { viewModel.updateSyncGroupCode(groupCodeInput) },
                enabled = groupCodeInput.isNotBlank() && groupCodeInput != profile.syncGroupCode
            ) { Text("Simpan Kode Grup") }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Aktifkan Sinkronisasi Cloud", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Kirim ringkasan omzet harian cabang ini otomatis setiap kali ada transaksi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = profile.cloudSyncEnabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    enabled = isConfigured
                )
            }

            if (!profile.cloudSyncEnabled) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sinkronisasi nonaktif — app tetap berjalan 100% offline seperti biasa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
