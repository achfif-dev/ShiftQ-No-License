package com.example.posapp.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.sync.OutletSalesSummary
import com.example.posapp.presentation.theme.PosBrandedTopBar
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

/**
 * Gabungan omzet semua cabang yang cloud sync-nya aktif, untuk HARI INI, ditarik langsung dari
 * Firestore (bukan dari database lokal cabang ini). Ini bukan pengganti Laporan per-cabang —
 * hanya ringkasan tingkat pemilik untuk melihat performa semua cabang dari satu HP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiOutletDashboardScreen(
    viewModel: MultiOutletViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            PosBrandedTopBar(
                title = { Text("Ringkasan Semua Cabang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Data hari ini (${uiState.dateKey}) dari cabang yang sinkronisasi cloud-nya aktif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            when {
                !uiState.isConfigured -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Firebase belum dikonfigurasi di perangkat ini. Lihat FIREBASE_SETUP.md.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                uiState.summaries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada cabang yang mengirim data hari ini.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val totalRevenue = uiState.summaries.sumOf { it.totalRevenue }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Total Omzet Semua Cabang", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                rupiah.format(totalRevenue),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn {
                        items(uiState.summaries, key = { it.outletId }) { summary -> OutletRow(summary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutletRow(summary: OutletSalesSummary) {
    ListItem(
        leadingContent = { Icon(Icons.Default.Storefront, contentDescription = null) },
        headlineContent = { Text(summary.outletName, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${summary.totalTransactions} transaksi") },
        trailingContent = { Text(rupiah.format(summary.totalRevenue), fontWeight = FontWeight.Bold) }
    )
    HorizontalDivider()
}
