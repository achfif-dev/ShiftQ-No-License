package com.example.posapp.presentation.customer

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.auth.LocalAutoLockManager
import com.example.posapp.data.local.dao.CustomerWithDebt
import com.example.posapp.data.local.dao.OverdueDebtor
import com.example.posapp.presentation.theme.PosBrandedTopBar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))

/**
 * Daftar pelanggan + saldo piutang berjalan (dihitung otomatis dari transaksi Bon dikurangi
 * pelunasan, lihat [CustomerRepository][com.example.posapp.data.repository.CustomerRepository]).
 * Terbuka untuk semua kasir (bukan admin-only) karena dipakai sehari-hari saat mencatat Bon
 * atau menerima pelunasan dari pelanggan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    viewModel: CustomerListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenDetail: (Long) -> Unit = {}
) {
    val customers by viewModel.customers.collectAsState()
    val overdueDebtors by viewModel.overdueDebtors.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showOverdueSection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Pelanggan & Piutang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.navigationBarsPadding()) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pelanggan")
            }
        }
    ) { padding ->
        if (customers.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada pelanggan. Tap + untuk menambahkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (overdueDebtors.isNotEmpty()) {
                    item {
                        OverdueDebtorsBanner(
                            debtors = overdueDebtors,
                            expanded = showOverdueSection,
                            onToggleExpanded = { showOverdueSection = !showOverdueSection }
                        )
                    }
                }
                items(customers, key = { it.id }) { customer ->
                    CustomerRow(customer = customer, onClick = { onOpenDetail(customer.id) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        CustomerFormDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address ->
                viewModel.addCustomer(name, phone, address)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun OverdueDebtorsBanner(
    debtors: List<OverdueDebtor>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val context = LocalContext.current
    val autoLockManager = LocalAutoLockManager.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${debtors.size} pelanggan piutangnya sudah jatuh tempo",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "Tutup" else "Lihat", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                debtors.forEach { debtor ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            debtor.customerName,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "${rupiah.format(debtor.debtBalance)} — jatuh tempo sejak ${dateFormat.format(Date(debtor.earliestDueDate))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        val phone = debtor.customerPhone
                        if (!phone.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    val text = "Halo ${debtor.customerName}, mengingatkan piutang belanja sebesar " +
                                        "${rupiah.format(debtor.debtBalance)} sudah jatuh tempo. Mohon segera dilunasi, terima kasih."
                                    val normalized = phone.replace(Regex("[^0-9+]"), "")
                                        .let { if (it.startsWith("0")) "62" + it.substring(1) else it }
                                        .removePrefix("+")
                                    autoLockManager.expectExternalActivityReturn()
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse("https://wa.me/$normalized?text=${android.net.Uri.encode(text)}")
                                            }
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ingatkan via WhatsApp")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(customer: CustomerWithDebt, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
        headlineContent = { Text(customer.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { customer.phone?.let { Text(it) } },
        trailingContent = {
            if (customer.debtBalance > 0) {
                Text(
                    rupiah.format(customer.debtBalance),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text("Lunas", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun CustomerFormDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Tambah Pelanggan", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("No. HP (opsional)") }, singleLine = true,
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
