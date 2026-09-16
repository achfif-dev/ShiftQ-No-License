package com.example.posapp.presentation.product

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.presentation.scanner.BarcodeScannerScreen
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import com.example.posapp.presentation.theme.PosBrandedTopBar
import com.example.posapp.presentation.theme.ProductAvatar

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    viewModel: ProductViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenImport: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var formState by remember { mutableStateOf<ProductFormState?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var productPendingDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var variantManagerProductId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ProductEvent.SavedSuccessfully -> {
                    val wasNewWithVariants = formState?.productId == null && formState?.hasVariants == true
                    formState = null
                    snackbarHostState.showSnackbar("Produk tersimpan")
                    if (wasNewWithVariants) variantManagerProductId = event.productId
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Manajemen Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Import Produk Massal (v16) -- sudah dibangun lengkap sebelumnya (parser,
                    // use case, layar preview) tapi belum ada titik masuk di UI mana pun.
                    IconButton(onClick = onOpenImport) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import Produk Massal")
                    }
                    TextButton(onClick = { showCategoryDialog = true }) { Text("Kategori") }
                }
            )
        },
        floatingActionButton = {
            // navigationBarsPadding() wajib di sini — tanpa ini FAB bisa tertutup/terpotong
            // oleh system navigation bar saat perangkat dalam mode lanskap (nav bar geser ke
            // tepi kanan/kiri layar karena enableEdgeToEdge() di MainActivity).
            FloatingActionButton(
                onClick = { formState = ProductFormState() },
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari nama atau SKU...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.height(8.dp))

            if (uiState.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada produk. Tap tombol + untuk menambahkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Kartu bulat + avatar produk senada dengan tampilan Kasir (bukan daftar
                // rata bergaris pembatas), supaya semua layar produk terasa satu desain.
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.products, key = { it.id }) { product ->
                        val categoryName = uiState.categories.find { it.id == product.categoryId }?.name
                        ProductRow(
                            product = product,
                            categoryName = categoryName,
                            onEdit = {
                                formState = ProductFormState(
                                    productId = product.id,
                                    name = product.name,
                                    sku = product.sku,
                                    categoryId = product.categoryId,
                                    supplierId = product.supplierId,
                                    purchasePrice = product.purchasePrice.toString(),
                                    sellPrice = product.sellPrice.toString(),
                                    stock = product.stock.toString(),
                                    unit = product.unit,
                                    lowStockThreshold = product.lowStockThreshold.toString(),
                                    discountPercent = product.discountPercent.toString(),
                                    variantName = product.variantName.orEmpty(),
                                    hasVariants = product.hasVariants,
                                    photoPath = product.photoPath
                                )
                            },
                            onManageVariants = { variantManagerProductId = product.id },
                            onDelete = { productPendingDelete = product }
                        )
                    }
                }
            }
        }
    }

    formState?.let { form ->
        ProductFormDialog(
            form = form,
            categories = uiState.categories,
            suppliers = uiState.suppliers,
            isSaving = uiState.isSaving,
            onDismiss = { formState = null },
            onSave = { viewModel.saveProduct(it) }
        )
    }

    if (showCategoryDialog) {
        CategoryManagerDialog(
            categories = uiState.categories,
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory,
            onDismiss = { showCategoryDialog = false }
        )
    }

    variantManagerProductId?.let { productId ->
        val product = uiState.products.find { it.id == productId }
        VariantManagerDialog(
            productId = productId,
            productName = product?.name ?: "",
            viewModel = viewModel,
            onDismiss = { variantManagerProductId = null }
        )
    }

    productPendingDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productPendingDelete = null },
            title = { Text("Hapus produk?") },
            text = { Text("\"${product.name}\" akan disembunyikan dari daftar produk aktif.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(product)
                    productPendingDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { productPendingDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ProductRow(
    product: ProductEntity,
    categoryName: String?,
    onEdit: () -> Unit,
    onManageVariants: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = !product.hasVariants && product.stock <= product.lowStockThreshold

    // Kartu bulat dengan avatar foto/ikon kategori — komponen & gaya yang sama persis
    // dengan kartu produk di Kasir (lihat ProductAvatar), supaya identitas produk
    // (warna & ikon) konsisten di semua layar.
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductAvatar(photoPath = product.photoPath, name = product.name, categoryName = categoryName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "SKU: ${product.sku}" + (categoryName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    Text(rupiah.format(product.sellPrice), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    if (product.hasVariants) {
                        Text("Punya varian", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        val stockColor = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        Text("Stok: ${product.stock} ${product.unit}", style = MaterialTheme.typography.bodyMedium, color = stockColor)
                    }
                }
            }
            if (product.hasVariants) {
                IconButton(onClick = onManageVariants) { Icon(Icons.Default.Style, contentDescription = "Kelola Varian") }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(
    form: ProductFormState,
    categories: List<CategoryEntity>,
    suppliers: List<com.example.posapp.data.local.entity.SupplierEntity> = emptyList(),
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductFormState) -> Unit
) {
    var state by remember(form.productId) { mutableStateOf(form) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var supplierMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var isCustomUnit by remember(form.productId) {
        mutableStateOf(com.example.posapp.data.local.entity.ProductUnits.isCustom(form.unit))
    }
    var showScanner by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current

    // File tujuan sementara untuk hasil jepretan kamera — dibuat sebelum kamera dibuka,
    // lalu Uri-nya (via FileProvider) diberikan ke aplikasi Kamera untuk ditulisi langsung.
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun newPhotoDestination(): File {
        val photoDir = File(context.filesDir, "product_photos").apply { mkdirs() }
        return File(photoDir, "product_${System.currentTimeMillis()}.jpg")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val destFile = newPhotoDestination()
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            state = state.copy(photoPath = destFile.absolutePath)
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            state = state.copy(photoPath = file.absolutePath)
        }
        pendingCameraFile = null
    }

    fun launchCamera() {
        val destFile = newPhotoDestination()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        pendingCameraFile = destFile
        autoLockManager.expectExternalActivityReturn()
        cameraCaptureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    fun requestCameraCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCamera()
        } else {
            // PERBAIKAN BUG: dialog izin runtime (GrantPermissionsActivity) juga memicu
            // onStop/onStart MainActivity di banyak perangkat, sama seperti benar-benar
            // membuka app kamera -- tanpa expectExternalActivityReturn() di sini, percobaan
            // PERTAMA kali memotret produk (sebelum izin kamera pernah diberikan) langsung
            // kena auto-lock dan lempar ke Login begitu dialog izin ditutup.
            autoLockManager.expectExternalActivityReturn()
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (form.productId == null) "Tambah Produk" else "Edit Produk",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.photoPath != null) {
                        AsyncImage(
                            model = state.photoPath,
                            contentDescription = "Foto produk",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { showPhotoSourceSheet = true }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.photoPath != null) "Ganti Foto" else "Tambah Foto")
                    }
                    if (state.photoPath != null) {
                        TextButton(onClick = { state = state.copy(photoPath = null) }) {
                            Text("Hapus Foto")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state = state.copy(name = it) },
                    label = { Text("Nama Produk") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.sku,
                    onValueChange = { state = state.copy(sku = it) },
                    label = { Text("SKU / Barcode") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showScanner = true }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == state.categoryId }?.name ?: "Tanpa kategori",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tanpa kategori") },
                            onClick = { state = state.copy(categoryId = null); categoryMenuExpanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { state = state.copy(categoryId = category.id); categoryMenuExpanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Pemasok (v13) — opsional, dipakai untuk mengelompokkan produk saat membuat
                // draf Pesanan Pembelian dari daftar stok tipis (lihat StockScreen). Dropdown
                // hanya berguna kalau ada minimal satu pemasok terdaftar (dikelola dari
                // Pengaturan > Pemasok); kalau kosong, ditampilkan tapi cuma berisi "Tanpa pemasok".
                ExposedDropdownMenuBox(
                    expanded = supplierMenuExpanded,
                    onExpandedChange = { supplierMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = suppliers.find { it.id == state.supplierId }?.name ?: "Tanpa pemasok",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pemasok") },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = supplierMenuExpanded,
                        onDismissRequest = { supplierMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tanpa pemasok") },
                            onClick = { state = state.copy(supplierId = null); supplierMenuExpanded = false }
                        )
                        suppliers.forEach { supplier ->
                            DropdownMenuItem(
                                text = { Text(supplier.name) },
                                onClick = { state = state.copy(supplierId = supplier.id); supplierMenuExpanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = state.purchasePrice,
                        onValueChange = { state = state.copy(purchasePrice = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Harga Beli") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.sellPrice,
                        onValueChange = { state = state.copy(sellPrice = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Harga Jual") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Produk Punya Varian", fontWeight = FontWeight.Medium)
                        Text(
                            "Stok dikelola per kombinasi (mis. Ukuran x Warna)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.hasVariants,
                        onCheckedChange = { state = state.copy(hasVariants = it) }
                    )
                }

                if (!state.hasVariants) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = state.stock,
                            onValueChange = { state = state.copy(stock = it.filter { c -> c.isDigit() }) },
                            label = { Text("Stok") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = state.lowStockThreshold,
                            onValueChange = { state = state.copy(lowStockThreshold = it.filter { c -> c.isDigit() }) },
                            label = { Text("Alert Stok Tipis") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = unitMenuExpanded,
                        onExpandedChange = { unitMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (isCustomUnit) "Lainnya" else state.unit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Satuan") },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            com.example.posapp.data.local.entity.ProductUnits.PRESETS.forEach { presetUnit ->
                                DropdownMenuItem(
                                    text = { Text(presetUnit) },
                                    onClick = {
                                        state = state.copy(unit = presetUnit)
                                        isCustomUnit = false
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Lainnya (ketik sendiri)") },
                                onClick = {
                                    isCustomUnit = true
                                    unitMenuExpanded = false
                                }
                            )
                        }
                    }
                    if (isCustomUnit) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.unit,
                            onValueChange = { state = state.copy(unit = it) },
                            label = { Text("Satuan Kustom (mis. galon, roll, rim)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (form.productId == null) "Anda bisa menambahkan kombinasi varian setelah produk disimpan."
                        else "Kelola stok tiap kombinasi lewat tombol ikon varian di daftar produk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = state.discountPercent,
                        onValueChange = { state = state.copy(discountPercent = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Diskon Produk (%)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.variantName,
                        onValueChange = { state = state.copy(variantName = it) },
                        label = { Text("Label (opsional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(state) }, enabled = !isSaving) {
                        Text(if (isSaving) "Menyimpan..." else "Simpan")
                    }
                }
            }
        }
    }

    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BarcodeScannerScreen(
                onBarcodeDetected = { code ->
                    state = state.copy(sku = code)
                    showScanner = false
                },
                onBack = { showScanner = false }
            )
        }
    }

    if (showPhotoSourceSheet) {
        Dialog(onDismissRequest = { showPhotoSourceSheet = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("Foto Produk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ambil foto langsung dari kamera atau pilih dari galeri",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    PhotoSourceOption(
                        icon = Icons.Default.CameraAlt,
                        label = "Ambil Foto",
                        description = "Buka kamera untuk memotret produk",
                        onClick = {
                            showPhotoSourceSheet = false
                            requestCameraCapture()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    PhotoSourceOption(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Pilih dari Galeri",
                        description = "Gunakan foto yang sudah ada",
                        onClick = {
                            showPhotoSourceSheet = false
                            autoLockManager.expectExternalActivityReturn()
                            photoPickerLauncher.launch("image/*")
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPhotoSourceSheet = false }) { Text("Batal") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<CategoryEntity>,
    onAdd: (String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Kategori") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(category) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus kategori")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Kategori baru") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(newCategoryName)
                newCategoryName = ""
            }) { Text("Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

/** Dialog kelola varian (matrix Ukuran x Warna) untuk satu produk: tambah/edit/hapus kombinasi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantManagerDialog(
    productId: Long,
    productName: String,
    viewModel: ProductViewModel,
    onDismiss: () -> Unit
) {
    val variants by viewModel.observeVariants(productId).collectAsState(initial = emptyList())
    var editingVariant by remember { mutableStateOf<ProductVariantEntity?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 500.dp)) {
                Text("Varian: $productName", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Setiap kombinasi punya SKU & stok sendiri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (variants.isEmpty() && !showAddForm) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada varian", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(variants, key = { it.id }) { variant ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(variant.variantLabel, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "SKU: ${variant.sku} · Stok: ${variant.stock}" +
                                            (variant.priceOverride?.let { " · ${rupiah.format(it)}" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingVariant = variant }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit varian")
                                }
                                IconButton(onClick = { viewModel.deleteVariant(variant.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus varian")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (showAddForm) {
                    VariantForm(
                        initial = null,
                        onCancel = { showAddForm = false },
                        onSave = { newVariant ->
                            viewModel.saveVariant(productId, newVariant)
                            showAddForm = false
                        }
                    )
                } else {
                    OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Kombinasi Varian")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
            }
        }
    }

    editingVariant?.let { variant ->
        Dialog(onDismissRequest = { editingVariant = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("Edit Varian", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    VariantForm(
                        initial = variant,
                        onCancel = { editingVariant = null },
                        onSave = { updated ->
                            viewModel.saveVariant(productId, updated)
                            editingVariant = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantForm(
    initial: ProductVariantEntity?,
    onCancel: () -> Unit,
    onSave: (ProductVariantEntity) -> Unit
) {
    var label by remember { mutableStateOf(initial?.variantLabel ?: "") }
    var sku by remember { mutableStateOf(initial?.sku ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var priceOverride by remember { mutableStateOf(initial?.priceOverride?.toString() ?: "") }
    var showVariantScanner by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Label (mis. Merah / L)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sku, onValueChange = { sku = it },
            label = { Text("SKU / Barcode") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showVariantScanner = true }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it.filter { c -> c.isDigit() } },
                label = { Text("Stok") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = priceOverride,
                onValueChange = { priceOverride = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Harga Khusus (opsional)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Batal") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave(
                        ProductVariantEntity(
                            id = initial?.id ?: 0L,
                            productId = initial?.productId ?: 0L,
                            variantLabel = label.trim(),
                            sku = sku.trim(),
                            stock = stock.toIntOrNull() ?: 0,
                            priceOverride = priceOverride.toDoubleOrNull()
                        )
                    )
                },
                enabled = label.isNotBlank() && sku.isNotBlank()
            ) { Text("Simpan") }
        }
    }

    if (showVariantScanner) {
        Dialog(
            onDismissRequest = { showVariantScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BarcodeScannerScreen(
                onBarcodeDetected = { code ->
                    sku = code
                    showVariantScanner = false
                },
                onBack = { showVariantScanner = false }
            )
        }
    }
}
