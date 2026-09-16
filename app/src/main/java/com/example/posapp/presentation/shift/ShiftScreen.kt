package com.example.posapp.presentation.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.CashMovementEntity
import com.example.posapp.data.local.entity.CashMovementType
import com.example.posapp.data.local.entity.ShiftEntity
import com.example.posapp.data.local.entity.ShiftStatus
import com.example.posapp.data.repository.ShiftPaymentBreakdown
import com.example.posapp.presentation.theme.PosBrandedTopBar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

/** Ringkasan yang ditampilkan setelah shift ditutup — dipisah dari event VM biar tidak dipakai ulang mentah-mentah di UI state. */
private data class ClosedSummary(
    val expected: Double,
    val actual: Double,
    val difference: Double,
    val breakdown: ShiftPaymentBreakdown
)

/**
 * Layar Buka/Tutup Kasir. Kas awal (saat buka) & kas akhir (saat tutup) diinput manual oleh
 * kasir berdasarkan hitungan fisik uang di laci — [ShiftViewModel]/[com.example.posapp.data.repository.ShiftRepository]
 * yang menghitung "kas yang seharusnya ada" dari data transaksi asli (tunai bersih dari
 * kembalian + kas masuk/keluar non-penjualan), supaya selisih kas benar-benar mencerminkan
 * kejujuran fisik, bukan angka yang bisa disesuaikan kasir sendiri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(
    viewModel: ShiftViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onShiftOpened: () -> Unit = {}
) {
    val activeShift by viewModel.activeShift.collectAsState()
    val activeCashMovements by viewModel.activeCashMovements.collectAsState()
    val history by viewModel.history.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showMovementDialog by remember { mutableStateOf(false) }
    var closedSummary by remember { mutableStateOf<ClosedSummary?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShiftEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ShiftEvent.ShiftClosed -> {
                    closedSummary = ClosedSummary(event.expectedCash, event.actualCash, event.difference, event.breakdown)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Buka/Tutup Kasir") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (activeShift == null) {
                NoActiveShiftCard(onOpenClick = { showOpenDialog = true })
            } else {
                ActiveShiftCard(
                    shift = activeShift!!,
                    movements = activeCashMovements,
                    onCloseClick = { showCloseDialog = true },
                    onCashMovementClick = { showMovementDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Riwayat Shift", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) {
                Text("Belum ada riwayat shift.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history, key = { it.id }) { shift -> ShiftHistoryRow(shift) }
                }
            }
        }
    }

    if (showOpenDialog) {
        OpenShiftDialog(
            cashierLabel = viewModel.currentCashierLabel(),
            onDismiss = { showOpenDialog = false },
            onConfirm = { startCash ->
                viewModel.openShift(startCash)
                showOpenDialog = false
                onShiftOpened()
            }
        )
    }

    if (showMovementDialog) {
        CashMovementDialog(
            onDismiss = { showMovementDialog = false },
            onConfirm = { type, amount, reason ->
                viewModel.recordCashMovement(type, amount, reason)
                showMovementDialog = false
            }
        )
    }

    if (showCloseDialog) {
        CloseShiftDialog(
            onDismiss = { showCloseDialog = false },
            onConfirm = { actualCash, note ->
                viewModel.closeShift(actualCash, note)
                showCloseDialog = false
            }
        )
    }

    closedSummary?.let { summary ->
        ShiftClosedSummaryDialog(
            expectedCash = summary.expected,
            actualCash = summary.actual,
            difference = summary.difference,
            breakdown = summary.breakdown,
            onDismiss = { closedSummary = null }
        )
    }
}

/**
 * Ditampilkan menggantikan PosScreen ketika "Wajibkan Login PIN" aktif tapi belum ada shift
 * yang dibuka — mencegah transaksi tunai terjadi tanpa tercatat di shift mana pun.
 */
@Composable
fun ShiftRequiredPrompt(onOpenShift: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.LockClock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Buka shift dahulu sebelum mulai transaksi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Hitung uang tunai fisik di laci, lalu buka shift baru.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenShift) { Text("Buka Kasir") }
        }
    }
}

@Composable
private fun NoActiveShiftCard(onOpenClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(
                "Belum ada shift yang dibuka",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Hitung uang tunai di laci lalu buka shift sebelum mulai melayani transaksi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenClick) { Text("Buka Kasir") }
        }
    }
}

@Composable
private fun ActiveShiftCard(
    shift: ShiftEntity,
    movements: List<CashMovementEntity>,
    onCloseClick: () -> Unit,
    onCashMovementClick: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Shift Sedang Berjalan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text("Kasir: ${shift.cashierName}", color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("Dibuka: ${dateTimeFormat.format(Date(shift.startedAt))}", color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("Kas Awal: ${rupiah.format(shift.startCash)}", color = MaterialTheme.colorScheme.onSecondaryContainer)

            if (movements.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kas Masuk/Keluar Shift Ini",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                movements.take(5).forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${if (m.type == CashMovementType.IN) "+ " else "- "}${m.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            rupiah.format(m.amount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (m.type == CashMovementType.IN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (movements.size > 5) {
                    Text(
                        "+${movements.size - 5} lainnya",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCashMovementClick) {
                    Text("Kas Masuk/Keluar")
                }
                Button(onClick = onCloseClick) {
                    Icon(Icons.Default.LockClock, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tutup Kasir")
                }
            }
        }
    }
}

@Composable
private fun ShiftHistoryRow(shift: ShiftEntity) {
    ListItem(
        headlineContent = { Text(shift.cashierName, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                Text(dateTimeFormat.format(Date(shift.startedAt)))
                if (shift.status == ShiftStatus.CLOSED && shift.difference != null) {
                    val diff = shift.difference
                    val diffLabel = when {
                        diff == 0.0 -> "Kas pas, tidak ada selisih"
                        diff > 0 -> "Kas lebih ${rupiah.format(diff)}"
                        else -> "Kas kurang ${rupiah.format(-diff)}"
                    }
                    Text(diffLabel, color = if (diff == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                } else {
                    Text("Masih berjalan", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun OpenShiftDialog(
    cashierLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (startCash: Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buka Kasir") },
        text = {
            Column {
                Text("Kasir: $cashierLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Kas Awal (hasil hitung fisik laci)") },
                    placeholder = { Text("Contoh: 200000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amountText.toDoubleOrNull() ?: 0.0) },
                enabled = amountText.toDoubleOrNull() != null
            ) { Text("Buka Shift") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Dialog catat kas masuk/keluar di luar transaksi penjualan — lihat ShiftRepository.recordCashMovement. */
@Composable
private fun CashMovementDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: CashMovementType, amount: Double, reason: String) -> Unit
) {
    var type by remember { mutableStateOf(CashMovementType.OUT) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kas Masuk/Keluar") },
        text = {
            Column {
                Text(
                    "Untuk pergerakan kas fisik di laci DI LUAR transaksi penjualan, mis. ambil kas untuk keperluan lain atau setoran tambahan modal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == CashMovementType.OUT,
                        onClick = { type = CashMovementType.OUT },
                        label = { Text("Kas Keluar") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                    )
                    FilterChip(
                        selected = type == CashMovementType.IN,
                        onClick = { type = CashMovementType.IN },
                        label = { Text("Kas Masuk") },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal") },
                    placeholder = { Text("Contoh: 50000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan (wajib)") },
                    placeholder = { Text("Contoh: Beli galon air") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(type, amount ?: 0.0, reason) },
                enabled = amount != null && amount > 0 && reason.isNotBlank()
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun CloseShiftDialog(
    onDismiss: () -> Unit,
    onConfirm: (actualCash: Double, note: String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tutup Kasir") },
        text = {
            Column {
                Text(
                    "Hitung ulang seluruh uang tunai fisik di laci sekarang, lalu masukkan totalnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Kas Akhir (hasil hitung fisik laci)") },
                    placeholder = { Text("Contoh: 850000") },
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
                enabled = amountText.toDoubleOrNull() != null
            ) { Text("Tutup Shift") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ShiftClosedSummaryDialog(
    expectedCash: Double,
    actualCash: Double,
    difference: Double,
    breakdown: ShiftPaymentBreakdown,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shift Ditutup") },
        text = {
            Column {
                Text("Kas seharusnya (sistem): ${rupiah.format(expectedCash)}")
                Text("Kas fisik (dihitung kasir): ${rupiah.format(actualCash)}")
                Spacer(Modifier.height(8.dp))
                val diffLabel = when {
                    difference == 0.0 -> "Kas pas, tidak ada selisih ✓"
                    difference > 0 -> "Kas lebih ${rupiah.format(difference)}"
                    else -> "Kas kurang ${rupiah.format(-difference)}"
                }
                Text(
                    diffLabel,
                    fontWeight = FontWeight.Bold,
                    color = if (difference == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Rincian Penjualan per Metode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                BreakdownRow("Tunai (bersih)", breakdown.cashNet)
                BreakdownRow("QRIS", breakdown.qris)
                BreakdownRow("Debit/Kredit", breakdown.debitCredit)
                BreakdownRow("Bon/Piutang (info, tidak menambah kas)", breakdown.bon)
                if (breakdown.cashInFromMovements > 0 || breakdown.cashOutFromMovements > 0) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Kas Masuk/Keluar Non-Penjualan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    BreakdownRow("Kas Masuk", breakdown.cashInFromMovements)
                    BreakdownRow("Kas Keluar", -breakdown.cashOutFromMovements)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun BreakdownRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(rupiah.format(amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
