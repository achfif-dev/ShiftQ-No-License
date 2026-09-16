package com.example.posapp.presentation.label

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.presentation.theme.PosBrandedTopBar
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPrintScreen(
    viewModel: LabelPrintViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LabelPrintEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Cetak Label Harga/Barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Cari produk (nama/SKU)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card {
                    Column(Modifier.heightIn(max = 240.dp)) {
                        LazyColumn {
                            items(searchResults, key = { it.id }) { product ->
                                ListItem(
                                    headlineContent = { Text(product.name) },
                                    supportingContent = { Text("${product.sku} — ${rupiah.format(product.sellPrice)}") },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.addToQueue(product) }) {
                                            Icon(Icons.Default.Add, contentDescription = "Tambah ke antrean")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Antrean Cetak (${queue.sumOf { it.copies }} label)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Cari & pilih produk di atas untuk ditambahkan ke antrean cetak.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(queue, key = { it.product.id }) { item ->
                        Card {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.product.name, fontWeight = FontWeight.SemiBold)
                                    Text(item.product.sku, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.updateCopies(item.product.id, item.copies - 1) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Kurangi")
                                }
                                Text("${item.copies}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                                IconButton(onClick = { viewModel.updateCopies(item.product.id, item.copies + 1) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Tambah")
                                }
                                IconButton(onClick = { viewModel.removeFromQueue(item.product.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus dari antrean", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.printQueue() },
                    enabled = !isPrinting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Mencetak...")
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cetak Semua Label")
                    }
                }
            }
        }
    }
}
