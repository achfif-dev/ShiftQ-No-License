package com.example.posapp.presentation.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.DebtPaymentEntity
import com.example.posapp.presentation.theme.PosBrandedTopBar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    viewModel: CustomerDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val detail by viewModel.detail.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPaymentDialog by remember { mutableStateOf(false) }

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
                title = { Text(detail?.name ?: "Detail Pelanggan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            val hasDebt = (detail?.debtBalance ?: 0.0) > 0
            if (hasDebt) {
                ExtendedFloatingActionButton(
                    onClick = { showPaymentDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Catat Pelunasan") },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            detail?.let { d ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (d.debtBalance > 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Sisa Piutang", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            rupiah.format(d.debtBalance.coerceAtLeast(0.0)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        d.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        d.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Riwayat Pelunasan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (payments.isEmpty()) {
                Text("Belum ada pelunasan tercatat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(payments, key = { it.id }) { payment -> PaymentRow(payment) }
                }
            }
        }
    }

    if (showPaymentDialog) {
        RecordPaymentDialog(
            maxAmount = detail?.debtBalance ?: 0.0,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, note ->
                viewModel.recordPayment(amount, note)
                showPaymentDialog = false
            }
        )
    }
}

@Composable
private fun PaymentRow(payment: DebtPaymentEntity) {
    ListItem(
        headlineContent = { Text(rupiah.format(payment.amount), fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                Text(dateTimeFormat.format(Date(payment.createdAt)))
                payment.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun RecordPaymentDialog(
    maxAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf(maxAmount.takeIf { it > 0 }?.toLong()?.toString() ?: "") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Pelunasan") },
        text = {
            Column {
                Text(
                    "Sisa piutang saat ini: ${rupiah.format(maxAmount)}. Boleh mencicil, tidak wajib lunas sekaligus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal Pelunasan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amountText.toDoubleOrNull() ?: 0.0, note) },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
