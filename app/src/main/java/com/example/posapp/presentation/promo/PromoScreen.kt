package com.example.posapp.presentation.promo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.PromoEntity
import com.example.posapp.data.local.entity.PromoType
import com.example.posapp.presentation.theme.PosBrandedTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoScreen(
    viewModel: PromoViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingPromo by remember { mutableStateOf<PromoEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var promoPendingDelete by remember { mutableStateOf<PromoEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PromoEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Promo & Diskon Otomatis") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            if (uiState.promoFeatureEnabled) {
                FloatingActionButton(onClick = { editingPromo = null; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Promo")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Aktifkan Promo Otomatis", fontWeight = FontWeight.Bold)
                        Text(
                            "Promo di bawah ini diterapkan otomatis di kasir tanpa input manual, selama diaktifkan di sini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.promoFeatureEnabled,
                        onCheckedChange = { viewModel.setPromoFeatureEnabled(it) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (uiState.promos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada promo. Tekan + untuk membuat promo pertama.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.promos, key = { it.id }) { promo ->
                        PromoRow(
                            promo = promo,
                            categoryName = uiState.categories.firstOrNull { it.id == promo.categoryId }?.name,
                            productName = uiState.products.firstOrNull { it.id == promo.productId }?.name,
                            onToggleActive = { viewModel.setActive(promo, it) },
                            onEdit = { editingPromo = promo; showEditor = true },
                            onDelete = { promoPendingDelete = promo }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        PromoEditorDialog(
            existing = editingPromo,
            categories = uiState.categories,
            products = uiState.products,
            onDismiss = { showEditor = false },
            onSave = { name, type, percent, minPurchase, categoryId, productId, buyQty, getFreeQty ->
                viewModel.savePromo(editingPromo, name, type, percent, minPurchase, categoryId, productId, buyQty, getFreeQty)
                showEditor = false
            }
        )
    }

    promoPendingDelete?.let { promo ->
        AlertDialog(
            onDismissRequest = { promoPendingDelete = null },
            title = { Text("Hapus Promo") },
            text = { Text("Hapus promo \"${promo.name}\"? Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePromo(promo.id)
                    promoPendingDelete = null
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { promoPendingDelete = null }) { Text("Batal") } }
        )
    }
}

private fun promoTypeLabel(type: PromoType): String = when (type) {
    PromoType.PERCENT_MIN_PURCHASE -> "Diskon % Min. Belanja"
    PromoType.PERCENT_CATEGORY -> "Diskon % Per Kategori"
    PromoType.BUY_X_GET_Y_FREE -> "Beli X Gratis Y"
}

private fun promoDescription(promo: PromoEntity, categoryName: String?, productName: String?): String = when (promo.type) {
    PromoType.PERCENT_MIN_PURCHASE -> "Diskon ${promo.percent}% untuk belanja min. Rp${promo.minPurchase.toLong()}"
    PromoType.PERCENT_CATEGORY -> "Diskon ${promo.percent}% untuk kategori ${categoryName ?: "(sudah dihapus)"}"
    PromoType.BUY_X_GET_Y_FREE -> "Beli ${promo.buyQty} gratis ${promo.getFreeQty} — ${productName ?: "(produk sudah dihapus)"}"
}

@Composable
private fun PromoRow(
    promo: PromoEntity,
    categoryName: String?,
    productName: String?,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(promo.name, fontWeight = FontWeight.Bold)
                    Text(promoTypeLabel(promo.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = promo.isActive, onCheckedChange = onToggleActive)
            }
            Spacer(Modifier.height(4.dp))
            Text(promoDescription(promo, categoryName, productName), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Ubah") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromoEditorDialog(
    existing: PromoEntity?,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (
        name: String, type: PromoType, percent: Double, minPurchase: Double,
        categoryId: Long?, productId: Long?, buyQty: Int, getFreeQty: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: PromoType.PERCENT_MIN_PURCHASE) }
    var percentText by remember { mutableStateOf(existing?.percent?.takeIf { it > 0 }?.toString() ?: "") }
    var minPurchaseText by remember { mutableStateOf(existing?.minPurchase?.takeIf { it > 0 }?.toString() ?: "") }
    var selectedCategoryId by remember { mutableStateOf(existing?.categoryId) }
    var selectedProductId by remember { mutableStateOf(existing?.productId) }
    var buyQtyText by remember { mutableStateOf((existing?.buyQty ?: 1).toString()) }
    var getFreeQtyText by remember { mutableStateOf((existing?.getFreeQty ?: 1).toString()) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var productMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Promo Baru" else "Ubah Promo") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Promo") },
                    placeholder = { Text("Contoh: Diskon Akhir Pekan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Jenis Promo", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Column {
                    PromoType.entries.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(selected = type == t, onClick = { type = t })
                            Text(promoTypeLabel(t), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                when (type) {
                    PromoType.PERCENT_MIN_PURCHASE -> {
                        OutlinedTextField(
                            value = percentText,
                            onValueChange = { percentText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Diskon (%)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = minPurchaseText,
                            onValueChange = { minPurchaseText = it.filter { c -> c.isDigit() } },
                            label = { Text("Minimal Belanja (Rp)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PromoType.PERCENT_CATEGORY -> {
                        OutlinedTextField(
                            value = percentText,
                            onValueChange = { percentText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Diskon (%)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                            OutlinedTextField(
                                value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Pilih kategori",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kategori") },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat.name) }, onClick = {
                                        selectedCategoryId = cat.id
                                        categoryMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    PromoType.BUY_X_GET_Y_FREE -> {
                        ExposedDropdownMenuBox(expanded = productMenuExpanded, onExpandedChange = { productMenuExpanded = it }) {
                            OutlinedTextField(
                                value = products.firstOrNull { it.id == selectedProductId }?.name ?: "Pilih produk",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Produk") },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = productMenuExpanded, onDismissRequest = { productMenuExpanded = false }) {
                                products.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                        selectedProductId = p.id
                                        productMenuExpanded = false
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = buyQtyText,
                                onValueChange = { buyQtyText = it.filter { c -> c.isDigit() } },
                                label = { Text("Beli (qty)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = getFreeQtyText,
                                onValueChange = { getFreeQtyText = it.filter { c -> c.isDigit() } },
                                label = { Text("Gratis (qty)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    name,
                    type,
                    percentText.toDoubleOrNull() ?: 0.0,
                    minPurchaseText.toDoubleOrNull() ?: 0.0,
                    selectedCategoryId,
                    selectedProductId,
                    buyQtyText.toIntOrNull() ?: 1,
                    getFreeQtyText.toIntOrNull() ?: 1
                )
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
