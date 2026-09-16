package com.example.posapp.presentation.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.ExpenseEntity
import com.example.posapp.data.local.entity.ExpensePeriod
import com.example.posapp.presentation.theme.PosBrandedTopBar
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

/**
 * Kelola daftar Beban Usaha (Sewa Toko, Gaji Karyawan, Listrik & Air, dll) yang dipakai untuk
 * menghitung Laba Bersih di Laporan. Layar ini hanya boleh dibuka Admin — guard peran dilakukan
 * di NavHost (sama seperti layar Pengaturan lain), bukan di sini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val expenses by viewModel.expenses.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var expensePendingEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expensePendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExpenseEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Beban Usaha") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Beban Usaha")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Beban ini otomatis diprorata sesuai rentang tanggal yang dipilih di Laporan " +
                    "untuk menghitung Laba Bersih (mis. beban Bulanan Rp3.000.000 dihitung ~Rp100.000 untuk laporan 1 hari)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (expenses.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada beban usaha. Tap + untuk menambahkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(expenses, key = { it.id }) { expense ->
                        ExpenseRow(
                            expense = expense,
                            onToggleActive = { active -> viewModel.setActive(expense, active) },
                            onEdit = { expensePendingEdit = expense },
                            onDeleteRequest = { expensePendingDelete = expense }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ExpenseFormDialog(
            title = "Tambah Beban Usaha",
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, period ->
                viewModel.addExpense(name, amount, period)
                showAddDialog = false
            }
        )
    }

    expensePendingEdit?.let { expense ->
        ExpenseFormDialog(
            title = "Edit Beban Usaha",
            initialName = expense.name,
            initialAmount = expense.amount,
            initialPeriod = expense.period,
            onDismiss = { expensePendingEdit = null },
            onSave = { name, amount, period ->
                viewModel.updateExpense(expense, name, amount, period)
                expensePendingEdit = null
            }
        )
    }

    expensePendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expensePendingDelete = null },
            title = { Text("Hapus beban usaha?") },
            text = { Text("\"${expense.name}\" akan dihapus dan tidak lagi dihitung di Laba Bersih.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteExpense(expense.id); expensePendingDelete = null }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { expensePendingDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            tint = if (expense.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                expense.name,
                fontWeight = FontWeight.SemiBold,
                color = if (expense.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${rupiah.format(expense.amount)} / ${expense.period.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = expense.isActive, onCheckedChange = onToggleActive)
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDeleteRequest) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus")
        }
    }
}

@Composable
private fun ExpenseFormDialog(
    title: String,
    initialName: String = "",
    initialAmount: Double? = null,
    initialPeriod: ExpensePeriod = ExpensePeriod.BULANAN,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, period: ExpensePeriod) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var amountText by remember { mutableStateOf(initialAmount?.let { it.toLong().toString() } ?: "") }
    var period by remember { mutableStateOf(initialPeriod) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama Beban") },
                    placeholder = { Text("Contoh: Sewa Toko, Gaji Karyawan") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal") },
                    placeholder = { Text("Contoh: 3000000") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Periode Pemakaian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpensePeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(name.trim(), amountText.toDoubleOrNull() ?: 0.0, period) },
                        enabled = name.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0
                    ) { Text("Simpan") }
                }
            }
        }
    }
}
