package com.example.posapp.presentation.stock

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.posapp.presentation.theme.PosBrandedTopBar
import com.example.posapp.presentation.theme.ProductAvatar

private val historyDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val products by viewModel.products.collectAsState()
    val lowStock by viewModel.lowStockProducts.collectAsState()
    val history by viewModel.adjustmentHistory.collectAsState()
    val categoryNamesById by viewModel.categoryNamesById.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val supplierPoEnabled by viewModel.supplierPoEnabled.collectAsState()
    val canPerformOpname by viewModel.canPerformOpname.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var adjustingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var pickingVariantsFor by remember { mutableStateOf<ProductEntity?>(null) }
    var adjustingVariant by remember { mutableStateOf<Pair<ProductEntity, ProductVariantEntity>?>(null) }
    var showLowStockOnly by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showPoDraft by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val displayedList = if (showLowStockOnly) lowStock else products
    val productNameById = remember(products) { products.associateBy({ it.id }, { it.name }) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Stok & Inventaris") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "Riwayat Penyesuaian Stok")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            FilterChip(
                selected = showLowStockOnly,
                onClick = { showLowStockOnly = !showLowStockOnly },
                label = { Text("Stok Tipis Saja (${lowStock.size})") }
            )
            Spacer(Modifier.height(12.dp))

            if (supplierPoEnabled && lowStock.isNotEmpty()) {
                Button(onClick = { showPoDraft = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Buat Draf Pesanan Pembelian (${lowStock.size} produk stok tipis)")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (displayedList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada produk untuk ditampilkan")
                }
            } else {
                // Kartu bulat + avatar produk senada dengan Kasir & Produk (bukan daftar
                // rata bergaris pembatas seperti sebelumnya).
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayedList, key = { it.id }) { product ->
                        StockProductRow(
                            product = product,
                            categoryName = product.categoryId?.let { categoryNamesById[it] },
                            onAdjust = {
                                if (product.hasVariants) pickingVariantsFor = product else adjustingProduct = product
                            }
                        )
                    }
                }
            }
        }
    }

    adjustingProduct?.let { product ->
        StockAdjustDialog(
            title = "Sesuaikan Stok: ${product.name}",
            currentStock = product.stock,
            unit = product.unit,
            canOpname = canPerformOpname,
            onDismiss = { adjustingProduct = null },
            onConfirm = { type, qty, reason ->
                viewModel.adjustStock(product.id, type, qty, reason)
                adjustingProduct = null
            }
        )
    }

    pickingVariantsFor?.let { product ->
        VariantPickerForAdjustDialog(
            product = product,
            viewModel = viewModel,
            onDismiss = { pickingVariantsFor = null },
            onVariantSelected = { variant ->
                adjustingVariant = product to variant
                pickingVariantsFor = null
            }
        )
    }

    adjustingVariant?.let { (product, variant) ->
        StockAdjustDialog(
            title = "Sesuaikan Stok: ${product.name} (${variant.variantLabel})",
            currentStock = variant.stock,
            unit = product.unit,
            canOpname = canPerformOpname,
            onDismiss = { adjustingVariant = null },
            onConfirm = { type, qty, reason ->
                viewModel.adjustVariantStock(product.id, variant, type, qty, reason)
                adjustingVariant = null
            }
        )
    }

    if (showHistory) {
        AdjustmentHistorySheet(
            history = history,
            productNameById = productNameById,
            onDismiss = { showHistory = false }
        )
    }

    if (showPoDraft) {
        PurchaseOrderDraftSheet(
            lowStockProducts = lowStock,
            suppliers = suppliers,
            onDismiss = { showPoDraft = false }
        )
    }
}

@Composable
private fun StockProductRow(
    product: ProductEntity,
    categoryName: String?,
    onAdjust: () -> Unit
) {
    val isLowStock = !product.hasVariants && product.stock <= product.lowStockThreshold

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductAvatar(photoPath = product.photoPath, name = product.name, categoryName = categoryName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                if (product.hasVariants) {
                    Text("Stok dikelola per varian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Text("Stok saat ini: ${product.stock} ${product.unit}", style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
            TextButton(onClick = onAdjust) { Text("Sesuaikan") }
        }
    }
}

@Composable
private fun VariantPickerForAdjustDialog(
    product: ProductEntity,
    viewModel: StockViewModel,
    onDismiss: () -> Unit,
    onVariantSelected: (ProductVariantEntity) -> Unit
) {
    var variants by remember { mutableStateOf<List<ProductVariantEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(product.id) {
        variants = viewModel.getVariantsFor(product.id)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Pilih Varian: ${product.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (variants.isEmpty()) {
                    Text("Belum ada varian untuk produk ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    variants.forEach { variant ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(variant.variantLabel, fontWeight = FontWeight.SemiBold)
                                Text("Stok: ${variant.stock}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onVariantSelected(variant) }) { Text("Pilih") }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                }
            }
        }
    }
}

@Composable
private fun StockAdjustDialog(
    title: String,
    currentStock: Int,
    unit: String = "pcs",
    canOpname: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (type: String, quantity: Int, reason: String?) -> Unit
) {
    var type by remember { mutableStateOf("IN") }
    var quantityText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("Stok saat ini: $currentStock $unit", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "IN", onClick = { type = "IN" }, label = { Text("Stok Masuk") })
                    FilterChip(selected = type == "OUT", onClick = { type = "OUT" }, label = { Text("Stok Keluar") })
                    // Set Opname disembunyikan untuk non-admin (lihat Permission.canPerformStockOpname).
                    // StockViewModel.adjustStock/adjustVariantStock tetap menolak tipe OPNAME dari
                    // non-admin walau chip ini entah bagaimana tetap terkirim, jadi ini murni UX,
                    // bukan satu-satunya penghalang.
                    if (canOpname) {
                        FilterChip(selected = type == "OPNAME", onClick = { type = "OPNAME" }, label = { Text("Set Opname") })
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                    label = { Text(if (type == "OPNAME") "Jumlah stok hasil hitung fisik" else "Jumlah") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val qty = quantityText.toIntOrNull() ?: 0
                        onConfirm(type, qty, reason.ifBlank { null })
                    }) { Text("Simpan") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentHistorySheet(
    history: List<StockAdjustmentEntity>,
    productNameById: Map<Long, String>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 520.dp)) {
            Text("Riwayat Penyesuaian Stok", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Termasuk penyesuaian per produk & per kombinasi varian",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (history.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada riwayat penyesuaian stok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(history, key = { it.id }) { entry ->
                        val productName = productNameById[entry.productId] ?: "Produk tidak dikenal"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    productName + (entry.variantLabelSnapshot?.let { " ($it)" } ?: ""),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    historyDateFormat.format(Date(entry.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                entry.reason?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            AdjustmentTypeBadge(type = entry.type)
                            Spacer(Modifier.width(8.dp))
                            Text("${entry.quantity}", fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentTypeBadge(type: String) {
    val (label, container, content) = when (type) {
        "IN" -> Triple("Masuk", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        "OUT" -> Triple("Keluar", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple("Opname", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(label, color = content, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/**
 * Draf Pesanan Pembelian (v13) dari daftar stok tipis, dikelompokkan per pemasok. Ini SENGAJA
 * cuma menyusun & mengirim teks draf pesanan via WhatsApp — bukan alur "terima barang" penuh.
 * Penerimaan barang & penambahan stok tetap lewat fitur Penyesuaian Stok yang sudah ada, supaya
 * modul ini tetap ringan dan tidak menduplikasi alur yang sudah berjalan baik.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseOrderDraftSheet(
    lowStockProducts: List<ProductEntity>,
    suppliers: List<com.example.posapp.data.local.entity.SupplierEntity>,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current
    val supplierById = remember(suppliers) { suppliers.associateBy { it.id } }
    val grouped = remember(lowStockProducts, suppliers) {
        lowStockProducts.groupBy { it.supplierId?.let { id -> supplierById[id] } }
    }
    // Jumlah yang disarankan dipesan: cukup untuk kembali 2x ambang batas stok tipis di atas
    // stok saat ini, minimal 1 — bukan angka sains, cuma titik awal yang wajar untuk diedit kasir.
    val suggestedQty = remember(lowStockProducts) {
        lowStockProducts.associate { it.id to ((it.lowStockThreshold * 2) - it.stock).coerceAtLeast(1) }
    }
    val qtyInputs = remember(lowStockProducts) {
        mutableStateMapOf<Long, String>().apply {
            lowStockProducts.forEach { put(it.id, (suggestedQty[it.id] ?: 1).toString()) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Text("Draf Pesanan Pembelian", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sesuaikan jumlah lalu kirim draf ke pemasok via WhatsApp. Stok baru bertambah lewat Penyesuaian Stok setelah barang diterima.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            grouped.forEach { (supplier, productsInGroup) ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            supplier?.name ?: "Tanpa Pemasok",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (supplier == null) {
                            Text(
                                "Atur pemasok di halaman Produk supaya draf pesanan bisa dikirim otomatis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        productsInGroup.forEach { product ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Stok saat ini: ${product.stock} ${product.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedTextField(
                                    value = qtyInputs[product.id] ?: "",
                                    onValueChange = { qtyInputs[product.id] = it.filter { c -> c.isDigit() } },
                                    label = { Text("Qty") },
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val phone = supplier?.phone
                        Button(
                            enabled = !phone.isNullOrBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val lines = productsInGroup.joinToString("\n") { p ->
                                    val qty = qtyInputs[p.id]?.toIntOrNull() ?: (suggestedQty[p.id] ?: 1)
                                    "- ${p.name}: $qty ${p.unit}"
                                }
                                val text = "Halo ${supplier?.name}, mohon bantu siapkan pesanan berikut:\n\n$lines\n\nTerima kasih."
                                val normalized = phone!!.replace(Regex("[^0-9+]"), "")
                                    .let { if (it.startsWith("0")) "62" + it.substring(1) else it }
                                    .removePrefix("+")
                                autoLockManager.expectExternalActivityReturn()
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            data = android.net.Uri.parse(
                                                "https://wa.me/$normalized?text=${android.net.Uri.encode(text)}"
                                            )
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (phone.isNullOrBlank()) "Pemasok belum punya no. WhatsApp" else "Kirim ke ${supplier?.name} via WhatsApp")
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Tutup") }
        }
    }
}
