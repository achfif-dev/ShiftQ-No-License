package com.example.posapp.presentation.report

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.repository.ReturnItemRequest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.posapp.presentation.theme.PosBrandedTopBar

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenExpenses: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showVoidConfirm by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current

    val bluetoothPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.printTransaction() }

    fun requestPrint() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.printTransaction()
            } else {
                autoLockManager.expectExternalActivityReturn()
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            viewModel.printTransaction()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ReportEvent.PdfReady -> {
                    autoLockManager.expectExternalActivityReturn()
                    context.startActivity(
                        Intent.createChooser(viewModel.createShareIntent(event.file), "Bagikan Invoice PDF")
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Laporan Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        // Satu LazyColumn untuk SELURUH konten layar (bukan Column biasa) agar di mode
        // lanskap — di mana tinggi layar jauh lebih pendek — seluruh konten (ringkasan,
        // produk terlaris, riwayat transaksi) tetap bisa discroll sampai bawah.
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetChip("Hari Ini", uiState.preset == ReportRangePreset.TODAY) { viewModel.selectPreset(ReportRangePreset.TODAY) }
                    PresetChip("Minggu Ini", uiState.preset == ReportRangePreset.THIS_WEEK) { viewModel.selectPreset(ReportRangePreset.THIS_WEEK) }
                    PresetChip("Bulan Ini", uiState.preset == ReportRangePreset.THIS_MONTH) { viewModel.selectPreset(ReportRangePreset.THIS_MONTH) }
                }

                Spacer(Modifier.height(16.dp))

                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Omzet",
                        value = rupiah.format(uiState.summary.totalRevenue),
                        accent = MaterialTheme.colorScheme.primary
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Laba Kotor",
                        value = rupiah.format(uiState.summary.totalGrossProfit),
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(8.dp))
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Jumlah Transaksi",
                    value = "${uiState.summary.totalTransactions}",
                    accent = MaterialTheme.colorScheme.secondary
                )

                if (uiState.canViewExpenses) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Khusus Admin & Manager",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Ringkasan Laba Bersih", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Hanya terlihat oleh Admin & Manager — tidak ditampilkan ke Kasir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            NetProfitRow("Laba Kotor", rupiah.format(uiState.summary.totalGrossProfit))
                            Spacer(Modifier.height(6.dp))
                            NetProfitRow("Beban Usaha (periode ini)", "- ${rupiah.format(uiState.totalExpenses)}")
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            NetProfitRow(
                                "Laba Bersih",
                                rupiah.format(uiState.netProfit),
                                emphasized = true,
                                valueColor = if (uiState.netProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onOpenExpenses, modifier = Modifier.fillMaxWidth()) {
                                Text("Kelola Beban Usaha")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Produk Terlaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.topItems.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada penjualan pada periode ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(uiState.topItems, key = { "top-${it.productId}" }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.productName, fontWeight = FontWeight.Medium)
                            Text("Terjual: ${item.totalQty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(rupiah.format(item.totalRevenue))
                    }
                    HorizontalDivider()
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Riwayat Penjualan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (!uiState.isAdmin) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Hanya lihat — hanya Admin yang bisa mengoreksi",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (uiState.isAdmin) "Tap transaksi untuk mengoreksi kesalahan input kasir"
                    else "Tap transaksi untuk melihat detail (hanya Admin yang bisa mengedit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.transactions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada transaksi pada periode ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(uiState.transactions, key = { it.id }) { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openTransactionDetail(tx.id) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.invoiceNumber, fontWeight = FontWeight.Medium)
                            Text(
                                dateTimeFormat.format(java.util.Date(tx.createdAt)) +
                                    (tx.cashierName?.let { " · $it" } ?: "") +
                                    (if (tx.editedByName != null) " · Diedit ${tx.editedByName}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tx.status == "VOIDED") {
                                Text(
                                    "Dibatalkan (Void)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (tx.returnedAmount > 0.0) {
                                Text(
                                    "Diretur sebagian: ${rupiah.format(tx.returnedAmount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Text(rupiah.format(tx.total), fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    detailState?.let { detail ->
        TransactionDetailDialog(
            detail = detail,
            isAdmin = uiState.isAdmin,
            onDismiss = { viewModel.closeTransactionDetail() },
            onSave = { updatedTransaction, updatedItems, deletedItemIds ->
                viewModel.saveTransactionCorrection(updatedTransaction, updatedItems, deletedItemIds)
            },
            onVoidRequest = { showVoidConfirm = true },
            onReturnRequest = { showReturnDialog = true },
            onPrint = { requestPrint() },
            onExportPdf = { viewModel.exportTransactionPdf() }
        )
    }

    if (showVoidConfirm) {
        val detail = detailState
        if (detail == null) {
            showVoidConfirm = false
        } else {
            VoidConfirmDialog(
                invoiceNumber = detail.transaction.invoiceNumber,
                onDismiss = { showVoidConfirm = false },
                onConfirm = { reason ->
                    showVoidConfirm = false
                    viewModel.voidTransaction(detail.transaction.id, reason)
                }
            )
        }
    }

    if (showReturnDialog) {
        val detail = detailState
        if (detail == null) {
            showReturnDialog = false
        } else {
            ReturnDialog(
                detail = detail,
                onDismiss = { showReturnDialog = false },
                onConfirm = { items, reason, refundAmount, refundMethod ->
                    showReturnDialog = false
                    viewModel.processReturn(items, reason, refundAmount, refundMethod)
                }
            )
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/**
 * Dialog konfirmasi Void (batalkan transaksi sepenuhnya). Beda dari dialog hapus permanen yang
 * lama: alasan WAJIB diisi (disimpan di transaction_returns untuk audit — lihat
 * TransactionRepository.voidTransaction), tombol konfirmasi baru aktif setelah alasan diisi.
 */
@Composable
private fun VoidConfirmDialog(
    invoiceNumber: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batalkan Transaksi (Void)?") },
        text = {
            Column {
                Text(
                    "Transaksi $invoiceNumber akan ditandai DIBATALKAN dan stok produknya " +
                        "dikembalikan penuh. Transaksi tetap tersimpan untuk audit, hanya tidak " +
                        "dihitung lagi di Laporan. Tindakan ini tidak bisa dibatalkan."
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan pembatalan (wajib)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank()
            ) {
                Text("Batalkan Transaksi", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

/** State baris item yang bisa diedit di dalam dialog koreksi transaksi (hanya untuk Admin). */
private class EditableItemState(
    val itemId: Long,
    val label: String,
    initialQuantity: Int,
    initialPrice: Double,
    initialDiscount: Double
) {
    var quantity by mutableStateOf(initialQuantity.toString())
    var price by mutableStateOf(initialPrice.toString())
    var discount by mutableStateOf(initialDiscount.toString())
    var deleted by mutableStateOf(false)

    val lineTotal: Double
        get() = (price.toDoubleOrNull() ?: 0.0) * (quantity.toIntOrNull() ?: 0) - (discount.toDoubleOrNull() ?: 0.0)
}

/**
 * Dialog detail transaksi dari Riwayat Penjualan. Kasir hanya melihat (read-only); Admin bisa
 * mengoreksi quantity/harga/diskon per item, menghapus item yang salah input, dan mengubah
 * catatan — lalu total transaksi dihitung ulang otomatis mengikuti rumus checkout (Cart.kt):
 * subtotal -> dikurangi diskon transaksi -> ditambah pajak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailDialog(
    detail: TransactionDetailUiState,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity, List<TransactionItemEntity>, List<Long>) -> Unit,
    onVoidRequest: () -> Unit,
    onReturnRequest: () -> Unit,
    onPrint: () -> Unit,
    onExportPdf: () -> Unit
) {
    val transaction = detail.transaction
    var note by remember(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    val editableItems = remember(transaction.id) {
        detail.items.map { item ->
            EditableItemState(
                // productNameSnapshot sudah menyertakan label varian saat checkout (lihat
                // CheckoutUseCase), jadi jangan tempel lagi variantLabelSnapshot di sini —
                // itu yang menyebabkan nama tampil dobel, mis. "baju hem (L hitam) (L hitam)".
                itemId = item.id,
                label = item.productNameSnapshot,
                initialQuantity = item.quantity,
                initialPrice = item.priceSnapshot,
                initialDiscount = item.itemDiscount
            )
        }.toMutableStateList()
    }

    val subtotal = editableItems.filter { !it.deleted }.sumOf { it.lineTotal }
    val taxAmount = ((subtotal - transaction.discountAmount).coerceAtLeast(0.0)) * (transaction.taxPercent / 100.0)
    val total = (subtotal - transaction.discountAmount + taxAmount).coerceAtLeast(0.0)
    val remainingCount = editableItems.count { !it.deleted }
    // PERBAIKAN BUG: transaksi VOID tidak punya tombol "Simpan Koreksi" (lihat di bawah -- tidak
    // masuk akal mengoreksi transaksi yang sudah dibatalkan), tapi field edit qty/harga/diskon
    // & ikon hapus item TETAP ditampilkan dan bisa "diklik" tanpa efek apa pun karena tidak ada
    // yang bisa disimpan. Dari sudut pandang pengguna, ikon hapus terkesan "tidak berfungsi".
    // [canEditTransaction] menonaktifkan seluruh mode edit untuk transaksi VOID, konsisten
    // dengan tidak adanya tombol simpan -- transaksi VOID jadi murni baca-saja.
    val canEditTransaction = isAdmin && transaction.status != "VOIDED"

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(transaction.invoiceNumber, style = MaterialTheme.typography.titleLarge)
                Text(
                    dateTimeFormat.format(java.util.Date(transaction.createdAt)) +
                        (transaction.cashierName?.let { " · Kasir: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.editedByName != null && transaction.editedAt != null) {
                    Text(
                        "Dikoreksi oleh ${transaction.editedByName} · ${dateTimeFormat.format(java.util.Date(transaction.editedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (transaction.status == "VOIDED") {
                    Text(
                        "DIBATALKAN (VOID) oleh ${transaction.voidedByName ?: "-"} · " +
                            (transaction.voidedAt?.let { dateTimeFormat.format(java.util.Date(it)) } ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (detail.returnHistory.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Riwayat Retur/Void",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    detail.returnHistory.forEach { entry ->
                        Text(
                            "${if (entry.header.isVoid) "Void" else "Retur"} ${rupiah.format(entry.header.refundAmount)} " +
                                "oleh ${entry.header.processedByName} · ${dateTimeFormat.format(java.util.Date(entry.header.createdAt))} " +
                                "— \"${entry.header.reason}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                editableItems.forEach { item ->
                    if (!item.deleted) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (canEditTransaction) {
                                    IconButton(onClick = { item.deleted = true }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus item")
                                    }
                                }
                            }
                            if (canEditTransaction) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = item.quantity,
                                        onValueChange = { item.quantity = it.filter { c -> c.isDigit() } },
                                        label = { Text("Qty") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = item.price,
                                        onValueChange = { item.price = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Harga Satuan") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = item.discount,
                                        onValueChange = { item.discount = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Diskon") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            } else {
                                Text(
                                    "${item.quantity} x ${rupiah.format(item.price.toDoubleOrNull() ?: 0.0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (canEditTransaction) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (transaction.note != null) {
                    Text("Catatan: ${transaction.note}", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Kirim Invoice",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bisa dicetak/dibagikan kapan saja, tidak harus saat transaksi selesai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrint, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cetak Struk")
                    }
                    OutlinedButton(onClick = onExportPdf, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bagikan PDF")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(rupiah.format(total), fontWeight = FontWeight.Bold)
                }

                // UX FIX (audit 2026-09-10): sebelumnya kalau semua item dihapus, "Simpan
                // Koreksi" cuma diam-diam jadi disabled tanpa penjelasan -- dari sudut pandang
                // pengguna ikon hapus terkesan "tidak berfungsi" (paling kelihatan di transaksi
                // 1-item: hapus satu-satunya item = macet, tidak ada cara lanjut). Sekarang kasih
                // pesan eksplisit yang mengarahkan ke Void, tombol yang memang dibuat untuk
                // membatalkan transaksi sepenuhnya.
                if (canEditTransaction && remainingCount == 0) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            // canEditTransaction sudah mensyaratkan isAdmin (lihat definisinya di
                            // atas), jadi di titik ini yang melihat pesan ini pasti Admin -- tidak
                            // perlu cabang pesan untuk non-Admin.
                            "Semua item dihapus — transaksi tidak bisa disimpan kosong. " +
                                "Kalau memang mau membatalkan seluruh transaksi ini, gunakan tombol Void di bawah.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Footer disusun bertumpuk (bukan satu Row sempit) supaya tombol "Simpan
                // Koreksi" selalu punya ruang penuh selebar dialog dan teksnya tidak
                // terpotong jadi beberapa baris ("Simp/an/Kore/ksi") di layar sempit.
                Column(Modifier.fillMaxWidth()) {
                    if (transaction.status != "VOIDED") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = onReturnRequest,
                                enabled = !detail.isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retur Barang", maxLines = 1)
                            }
                            if (isAdmin) {
                                // Ditekankan visual (filled, bukan cuma teks) saat semua item
                                // sudah dihapus -- ini jadi satu-satunya jalan keluar yang valid
                                // di kondisi itu (lihat pesan peringatan di atas Total).
                                if (remainingCount == 0) {
                                    Button(
                                        onClick = onVoidRequest,
                                        enabled = !detail.isSaving,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Void")
                                    }
                                } else {
                                    TextButton(
                                        onClick = onVoidRequest,
                                        enabled = !detail.isSaving
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Void", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text(if (isAdmin) "Batal" else "Tutup") }
                        if (isAdmin && transaction.status != "VOIDED") {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val updatedItems = detail.items.mapNotNull { original ->
                                        val edited = editableItems.find { it.itemId == original.id } ?: return@mapNotNull null
                                        if (edited.deleted) null
                                        else original.copy(
                                            quantity = (edited.quantity.toIntOrNull() ?: 1).coerceAtLeast(1),
                                            priceSnapshot = edited.price.toDoubleOrNull() ?: original.priceSnapshot,
                                            itemDiscount = edited.discount.toDoubleOrNull() ?: 0.0
                                        )
                                    }
                                    val deletedIds = editableItems.filter { it.deleted }.map { it.itemId }
                                    val updatedTransaction = transaction.copy(
                                        subtotal = subtotal,
                                        taxAmount = taxAmount,
                                        total = total,
                                        note = note.ifBlank { null }
                                    )
                                    onSave(updatedTransaction, updatedItems, deletedIds)
                                },
                                enabled = !detail.isSaving && remainingCount > 0
                            ) {
                                Text(
                                    if (detail.isSaving) "Menyimpan..." else "Simpan Koreksi",
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetProfitRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun SummaryCard(modifier: Modifier = Modifier, label: String, value: String, accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .background(accent, shape = MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** State satu baris item yang bisa dipilih untuk diretur (qty & apakah layak masuk stok lagi). */
private class ReturnRowState(val item: TransactionItemEntity, val maxQty: Int) {
    var qty by mutableStateOf("0")
    var restocked by mutableStateOf(true)
}

/**
 * Dialog proses Retur Barang: pilih item & qty yang dikembalikan pelanggan (dibatasi maksimal
 * sisa qty yang belum pernah diretur sebelumnya — lihat TransactionDao.getReturnedQuantityForItem
 * untuk aturan yang sama di layer data), tandai layak jual lagi atau rusak, isi alasan wajib,
 * lalu tentukan nominal & metode pengembalian uang (default = total harga item terpilih,
 * proporsional terhadap diskon per-item, tapi boleh diedit manual).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReturnDialog(
    detail: TransactionDetailUiState,
    onDismiss: () -> Unit,
    onConfirm: (items: List<ReturnItemRequest>, reason: String, refundAmount: Double, refundMethod: PaymentMethod?) -> Unit
) {
    val alreadyReturnedByItem = remember(detail.transaction.id) {
        detail.returnHistory.flatMap { it.items }
            .groupBy { it.transactionItemId }
            .mapValues { (_, itemRows) -> itemRows.sumOf { it.quantityReturned } }
    }

    val rows = remember(detail.transaction.id) {
        detail.items.mapNotNull { item ->
            val maxQty = item.quantity - (alreadyReturnedByItem[item.id] ?: 0)
            if (maxQty <= 0) null else ReturnRowState(item, maxQty)
        }
    }

    var reason by remember { mutableStateOf("") }
    var refundMethod by remember { mutableStateOf<PaymentMethod?>(detail.transaction.paymentMethod) }
    var refundAmountText by remember { mutableStateOf("") }

    val selectedTotal = rows.sumOf { row ->
        val qty = row.qty.toIntOrNull() ?: 0
        val unitNet = if (row.item.quantity > 0) row.item.lineTotal / row.item.quantity else 0.0
        unitNet * qty
    }
    val selectedItems = rows.mapNotNull { row ->
        val qty = row.qty.toIntOrNull() ?: 0
        if (qty <= 0) null else ReturnItemRequest(row.item.id, qty, row.restocked)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Retur Barang", style = MaterialTheme.typography.titleLarge)
                Text(
                    detail.transaction.invoiceNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (rows.isEmpty()) {
                    Text("Semua item di transaksi ini sudah diretur seluruhnya.")
                } else {
                    rows.forEach { row ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text("${row.item.productNameSnapshot} (maks ${row.maxQty})", fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = row.qty,
                                    onValueChange = { text ->
                                        val digits = text.filter { it.isDigit() }
                                        row.qty = if (digits.isEmpty()) "0" else (digits.toIntOrNull() ?: 0).coerceIn(0, row.maxQty).toString()
                                    },
                                    label = { Text("Qty retur") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = row.restocked, onCheckedChange = { row.restocked = it })
                                    Text("Layak jual lagi", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan retur (wajib)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(Modifier.height(12.dp))
                Text("Metode Pengembalian Uang", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PaymentMethod.CASH, PaymentMethod.DEBIT_CREDIT, PaymentMethod.QRIS).forEach { method ->
                        FilterChip(
                            selected = refundMethod == method,
                            onClick = { refundMethod = method },
                            label = { Text(method.name) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = refundAmountText.ifEmpty { selectedTotal.toInt().toString() },
                    onValueChange = { refundAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Nominal Refund") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Default dihitung otomatis dari item terpilih, bisa diubah manual bila perlu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = selectedItems.isNotEmpty() && reason.isNotBlank() && !detail.isSaving,
                        onClick = {
                            val amount = refundAmountText.toDoubleOrNull() ?: selectedTotal
                            onConfirm(selectedItems, reason.trim(), amount, refundMethod)
                        }
                    ) {
                        Text("Proses Retur")
                    }
                }
            }
        }
    }
}
