package com.example.posapp.presentation.product

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.domain.usecase.ProductImportSummary
import com.example.posapp.presentation.theme.PosBrandedTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductImportScreen(
    viewModel: ProductImportViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull()
        viewModel.onFileContentRead(text)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductImportEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Import Produk Massal") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            when (val s = step) {
                is ProductImportStep.PickFile -> PickFileContent(onPickFile = { filePicker.launch("text/*") })
                is ProductImportStep.Parsing -> LoadingContent("Membaca file...")
                is ProductImportStep.Preview -> PreviewContent(
                    result = s.parseResult,
                    onConfirm = viewModel::confirmImport,
                    onCancel = viewModel::cancelPreview
                )
                is ProductImportStep.Importing -> LoadingContent("Menyimpan produk...")
                is ProductImportStep.Done -> DoneContent(summary = s.summary, onClose = onBack, onImportAnother = viewModel::reset)
            }
        }
    }
}

@Composable
private fun PickFileContent(onPickFile: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Pilih file CSV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Kolom wajib: Nama, SKU, Harga Jual. Kolom lain opsional (Harga Beli, Satuan, Stok, " +
                "Alert Stok Tipis, Diskon (%), Aktif, Kategori) — urutan kolom bebas, cocok dengan " +
                "hasil Export Produk (CSV) di Pengaturan kalau mau diedit dan diimpor ulang.\n\n" +
                "Produk dengan SKU yang sudah ada akan DIPERBARUI (nama/harga/dll) — stok TIDAK " +
                "ikut diubah lewat import, supaya penjualan yang sudah terjadi tidak tertimpa. " +
                "SKU baru akan ditambahkan sebagai produk baru.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickFile) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pilih File CSV")
        }
    }
}

@Composable
private fun LoadingContent(label: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(label)
    }
}

@Composable
private fun PreviewContent(
    result: com.example.posapp.data.export.ProductCsvParser.ParseResult,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text("Pratinjau Import", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${result.validRows.size} baris siap diimpor", fontWeight = FontWeight.SemiBold)
                }
                if (result.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${result.errors.size} baris dilewati karena error", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (result.errors.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Detail baris error:", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(result.errors) { err ->
                    Text(
                        "Baris ${err.lineNumber}: ${err.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Batal") }
            Button(
                onClick = onConfirm,
                enabled = result.validRows.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Impor ${result.validRows.size} Produk") }
        }
    }
}

@Composable
private fun DoneContent(summary: ProductImportSummary, onClose: () -> Unit, onImportAnother: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Import Selesai", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${summary.added} produk baru ditambahkan")
        Text("${summary.updated} produk diperbarui")
        if (summary.rowErrors.isNotEmpty()) {
            Text("${summary.rowErrors.size} baris dilewati", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImportAnother) { Text("Impor File Lain") }
            Button(onClick = onClose) { Text("Selesai") }
        }
    }
}
