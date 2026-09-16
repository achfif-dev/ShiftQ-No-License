package com.example.posapp.presentation.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

/**
 * Lihat stok & harga cabang LAIN secara realtime (read-only) — berguna sebelum menyarankan
 * pelanggan pindah cabang, atau sebelum membuat mutasi stok manual antar cabang. TIDAK mengubah
 * data lokal apa pun; lihat batasan lingkup di OutletCatalogSyncRepository. Data hanya muncul
 * kalau cabang tsb sudah pernah sinkron (Sinkronisasi Cloud aktif + sudah online minimal sekali).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutletStockCheckScreen(onBack: () -> Unit) {
    val viewModel: OutletStockViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cek Stok Semua Cabang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (uiState.outlets.isEmpty()) {
                Text(
                    if (uiState.isLoading) "Memuat daftar cabang..."
                    else "Belum ada cabang lain yang sinkron. Aktifkan Sinkronisasi Cloud di masing-masing cabang (Pengaturan > Multi-Cabang).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            Text("Pilih Cabang", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.outlets.forEach { (id, name) ->
                    FilterChip(
                        selected = uiState.selectedOutletId == id,
                        onClick = { viewModel.selectOutlet(id) },
                        label = { Text(name) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (uiState.selectedOutletId != null) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("Cari nama/SKU produk...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                val rows = viewModel.filteredRows
                if (rows.isEmpty()) {
                    Text("Belum ada data produk dari cabang ini.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn {
                        items(rows) { row ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(row.name, fontWeight = FontWeight.Medium)
                                    Text("SKU: ${row.sku}", style = MaterialTheme.typography.bodySmall)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Stok: ${row.stock}", style = MaterialTheme.typography.bodyMedium)
                                        Text(rupiah.format(row.sellPrice), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
