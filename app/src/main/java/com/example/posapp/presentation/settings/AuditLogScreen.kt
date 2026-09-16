package com.example.posapp.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.presentation.theme.PosBrandedTopBar

private val actionLabels = mapOf(
    "VOID_TRANSAKSI" to "Void Transaksi",
    "RETUR_BARANG" to "Retur Barang",
    "KOREKSI_TRANSAKSI" to "Koreksi Transaksi",
    "TAMBAH_USER" to "Tambah Pengguna",
    "HAPUS_USER" to "Hapus Pengguna",
    "RESTORE_BACKUP" to "Restore Backup"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        topBar = {
            PosBrandedTopBar(
                title = { Text("Log Aktivitas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Belum ada aktivitas sensitif yang tercatat.\n" +
                        "Log ini mencatat Void, Retur, Koreksi Transaksi, Tambah/Hapus Pengguna, dan Restore Backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(rows, key = { "${it.timestampMillis}-${it.action}-${it.actorName}" }) { row ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    actionLabels[row.action] ?: row.action,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.action == "RESTORE_BACKUP" || row.action == "VOID_TRANSAKSI") {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Text(
                                    row.timestampLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(row.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "oleh ${row.actorName} (${row.actorRole})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
