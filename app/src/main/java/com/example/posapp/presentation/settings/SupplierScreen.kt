package com.example.posapp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.SupplierEntity
import com.example.posapp.presentation.theme.PosBrandedTopBar

/**
 * Kelola daftar pemasok (v13) — dipakai untuk mengelompokkan produk stok tipis saat membuat
 * draf Pesanan Pembelian (lihat StockScreen). Admin-only, sama seperti rute Pengaturan lain
 * (digerbang independen dari MainActivity via Permission.canAccessSettings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierScreen(
    viewModel: SupplierViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var deactivatingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SupplierEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Pemasok") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.navigationBarsPadding()) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pemasok")
            }
        }
    ) { padding ->
        if (suppliers.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada pemasok. Tap + untuk menambahkan, lalu pilih pemasok saat mengisi data produk.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(suppliers, key = { it.id }) { supplier ->
                    ListItem(
                        modifier = Modifier.clickable { editingSupplier = supplier },
                        leadingContent = { Icon(Icons.Default.LocalShipping, contentDescription = null) },
                        headlineContent = { Text(supplier.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { supplier.phone?.let { Text(it) } },
                        trailingContent = {
                            TextButton(onClick = { deactivatingSupplier = supplier }) { Text("Nonaktifkan") }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        SupplierFormDialog(
            title = "Tambah Pemasok",
            initialName = "", initialPhone = "", initialAddress = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address ->
                viewModel.addSupplier(name, phone, address)
                showAddDialog = false
            }
        )
    }

    editingSupplier?.let { supplier ->
        SupplierFormDialog(
            title = "Edit Pemasok",
            initialName = supplier.name,
            initialPhone = supplier.phone ?: "",
            initialAddress = supplier.address ?: "",
            onDismiss = { editingSupplier = null },
            onSave = { name, phone, address ->
                viewModel.updateSupplier(supplier, name, phone, address)
                editingSupplier = null
            }
        )
    }

    deactivatingSupplier?.let { supplier ->
        // Konfirmasi wajib: sekali dinonaktifkan, pemasok TIDAK muncul lagi di daftar mana pun
        // (observeAll() hanya mengambil yang aktif) dan saat ini belum ada layar "Pemasok
        // Nonaktif" untuk mengaktifkan kembali — jadi ini praktis one-way dari sisi UI.
        AlertDialog(
            onDismissRequest = { deactivatingSupplier = null },
            title = { Text("Nonaktifkan \"${supplier.name}\"?") },
            text = { Text("Pemasok ini tidak akan muncul lagi di daftar Pemasok maupun pilihan produk. Produk yang sudah memakai pemasok ini tidak berubah, tapi tidak bisa diatur ulang ke pemasok ini lagi lewat aplikasi.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setActive(supplier, false)
                    deactivatingSupplier = null
                }) { Text("Nonaktifkan") }
            },
            dismissButton = {
                TextButton(onClick = { deactivatingSupplier = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun SupplierFormDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialAddress: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama Pemasok") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("No. WhatsApp (opsional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Alamat (opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(name.trim(), phone.trim(), address.trim()) }, enabled = name.isNotBlank()) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}
