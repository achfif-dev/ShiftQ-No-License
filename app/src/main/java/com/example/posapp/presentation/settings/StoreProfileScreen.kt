package com.example.posapp.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.posapp.data.settings.quickCashAmountList
import com.example.posapp.presentation.theme.PosAccentPresets
import com.example.posapp.presentation.theme.PosFontOption
import com.example.posapp.presentation.theme.parseHexColorOrNull
import java.io.File
import com.example.posapp.presentation.theme.PosBrandedTopBar

/** Format persentase pajak tanpa desimal berlebih, mis. 11.0 -> "11", 8.5 -> "8.5". */
private fun formatTaxPercent(percent: Double): String =
    if (percent == percent.toLong().toDouble()) percent.toLong().toString() else percent.toString()

/** Format nominal cash tanpa desimal, mis. 50000 -> "Rp50.000". */
private fun formatQuickCash(amount: Long): String =
    "Rp" + amount.toString().reversed().chunked(3).joinToString(".").reversed()

/** Layar Profil Toko — nama, alamat, telepon, catatan struk, dan gambar QRIS statis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileScreen(
    viewModel: StoreProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current
    val profile by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var address by remember(profile.address) { mutableStateOf(profile.address) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }
    var footer by remember(profile.receiptFooter) { mutableStateOf(profile.receiptFooter) }
    var bonDueDaysInput by remember(profile.bonDueDays) { mutableStateOf(profile.bonDueDays.toString()) }
    var receiptHeaderNoteInput by remember(profile.receiptHeaderNote) { mutableStateOf(profile.receiptHeaderNote) }

    val qrisPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val qrisDir = File(context.filesDir, "qris").apply { mkdirs() }
            val destFile = File(qrisDir, "qris_image.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.setQrisImagePath(destFile.absolutePath)
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val logoDir = File(context.filesDir, "logo").apply { mkdirs() }
            val destFile = File(logoDir, "store_logo.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.setLogoImagePath(destFile.absolutePath)
        }
    }

    var customHexInput by remember(profile.appColorHex) { mutableStateOf(profile.appColorHex ?: "") }
    var taxPercentInput by remember(profile.taxPercent) { mutableStateOf(formatTaxPercent(profile.taxPercent)) }
    var maxDiscountInput by remember(profile.maxKasirDiscountPercent) { mutableStateOf(profile.maxKasirDiscountPercent.toString()) }
    var loyaltyRupiahPerPointInput by remember(profile.loyaltyRupiahPerPoint) { mutableStateOf(profile.loyaltyRupiahPerPoint.toString()) }
    var loyaltyPointValueInput by remember(profile.loyaltyPointValueRupiah) { mutableStateOf(profile.loyaltyPointValueRupiah.toString()) }
    var newQuickCashInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoreProfileEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Profil Toko") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Informasi Toko", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = address, onValueChange = { address = it },
                label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Nomor Telepon") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = footer, onValueChange = { footer = it },
                label = { Text("Catatan Kaki Struk") }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.save(name, address, phone, footer) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan Profil Toko") }

            Spacer(Modifier.height(28.dp))
            Text("Pajak (PPN)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Nonaktifkan bila toko tidak memungut pajak ke pelanggan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pungut Pajak", fontWeight = FontWeight.Medium)
                            Text(
                                if (profile.taxEnabled) "Pajak ${formatTaxPercent(profile.taxPercent)}% ditambahkan ke setiap transaksi"
                                else "Transaksi tidak dikenakan pajak",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.taxEnabled,
                            onCheckedChange = { checked ->
                                viewModel.setTaxSettings(checked, taxPercentInput.toDoubleOrNull() ?: profile.taxPercent)
                            }
                        )
                    }
                    if (profile.taxEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = taxPercentInput,
                                onValueChange = { taxPercentInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Persentase Pajak (%)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    viewModel.setTaxSettings(true, taxPercentInput.toDoubleOrNull() ?: 11.0)
                                },
                                enabled = taxPercentInput.toDoubleOrNull() != null
                            ) { Text("Simpan") }
                        }
                    }
                }
            }

            // TEMUAN KEAMANAN (audit ulang): sebelumnya diskon manual Kasir tidak dibatasi sama
            // sekali (bisa sampai 100%, tanpa jejak audit) -- celah fraud "sweethearting" klasik
            // di POS. Sekarang Admin bisa atur batasnya di sini; lihat DiscountPolicy.kt untuk
            // penjelasan lengkap. Batas ini TIDAK berlaku untuk Admin/Manager sendiri.
            Spacer(Modifier.height(28.dp))
            Text("Batas Diskon Manual Kasir", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Batas maksimum diskon (per-item atau per-transaksi) yang boleh diberikan Kasir " +
                    "biasa tanpa Admin/Manager login sendiri di device ini. Diskon di atas batas " +
                    "ini otomatis dipangkas ke batas maksimum. Tidak berlaku untuk Admin & Manager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = maxDiscountInput,
                            onValueChange = { maxDiscountInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Batas Diskon Kasir (%)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                viewModel.setMaxKasirDiscountPercent(maxDiscountInput.toIntOrNull() ?: 20)
                            },
                            enabled = maxDiscountInput.toIntOrNull() != null
                        ) { Text("Simpan") }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Tipe Bisnis & Fitur", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sesuaikan fitur yang tampil dengan jenis usaha toko — nonaktifkan yang tidak relevan supaya tampilan tetap ringkas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tipe Bisnis", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GENERAL" to "Umum", "RETAIL" to "Retail/Toko", "FNB" to "Resto/Kafe").forEach { (value, label) ->
                            FilterChip(
                                selected = profile.businessType == value,
                                onClick = { viewModel.setBusinessType(value) },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    FeatureToggleRow(
                        title = "Tag Nomor Meja/Pesanan",
                        description = "Tambah kolom nomor meja atau nama pemesan di layar Kasir (mode Resto/Kafe)",
                        checked = profile.tableTaggingEnabled,
                        onCheckedChange = viewModel::setTableTaggingEnabled
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    FeatureToggleRow(
                        title = "Struk Digital via WhatsApp",
                        description = "Tampilkan tombol kirim struk ke WhatsApp pelanggan setelah transaksi",
                        checked = profile.whatsappReceiptEnabled,
                        onCheckedChange = viewModel::setWhatsappReceiptEnabled
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    FeatureToggleRow(
                        title = "Pemasok & Pesanan Pembelian",
                        description = "Aktifkan modul Pemasok dan draf PO dari daftar stok tipis",
                        checked = profile.supplierPoEnabled,
                        onCheckedChange = viewModel::setSupplierPoEnabled
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    FeatureToggleRow(
                        title = "Notifikasi Stok Menipis",
                        description = "Kirim notifikasi sistem Android kalau ada produk stok tipis (dicek tiap beberapa jam)",
                        checked = profile.lowStockNotificationsEnabled,
                        onCheckedChange = viewModel::setLowStockNotificationsEnabled
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Poin Loyalitas Pelanggan", fontWeight = FontWeight.Medium)
                            Text(
                                if (profile.loyaltyEnabled)
                                    "Tiap Rp${profile.loyaltyRupiahPerPoint} belanja = 1 poin, ditukar seharga Rp${profile.loyaltyPointValueRupiah}/poin"
                                else "Nonaktif — pelanggan tidak mengumpulkan poin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.loyaltyEnabled,
                            onCheckedChange = { checked ->
                                viewModel.setLoyaltySettings(checked, profile.loyaltyRupiahPerPoint, profile.loyaltyPointValueRupiah)
                            }
                        )
                    }
                    if (profile.loyaltyEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = loyaltyRupiahPerPointInput,
                                onValueChange = { loyaltyRupiahPerPointInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Rp per 1 poin") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = loyaltyPointValueInput,
                                onValueChange = { loyaltyPointValueInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Nilai 1 poin (Rp)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.setLoyaltySettings(
                                    true,
                                    loyaltyRupiahPerPointInput.toLongOrNull() ?: profile.loyaltyRupiahPerPoint,
                                    loyaltyPointValueInput.toLongOrNull() ?: profile.loyaltyPointValueRupiah
                                )
                            },
                            enabled = loyaltyRupiahPerPointInput.toLongOrNull() != null && loyaltyPointValueInput.toLongOrNull() != null
                        ) { Text("Simpan Pengaturan Poin") }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Piutang (BON)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Berapa hari setelah transaksi BON dianggap jatuh tempo — dipakai layar Piutang Jatuh Tempo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bonDueDaysInput,
                        onValueChange = { bonDueDaysInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Jatuh tempo (hari)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.updateBonDueDays(bonDueDaysInput.toIntOrNull() ?: profile.bonDueDays) },
                        enabled = bonDueDaysInput.toIntOrNull() != null
                    ) { Text("Simpan") }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Kustomisasi Struk", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    FeatureToggleRow(
                        title = "Tampilkan SKU per Item",
                        description = "Cetak SKU/barcode di bawah nama tiap item pada struk & PDF invoice",
                        checked = profile.receiptShowSku,
                        onCheckedChange = { viewModel.updateReceiptCustomization(it, receiptHeaderNoteInput) }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    OutlinedTextField(
                        value = receiptHeaderNoteInput,
                        onValueChange = { receiptHeaderNoteInput = it },
                        label = { Text("Catatan di Bawah Alamat Toko") },
                        placeholder = { Text("Contoh: Buka 08:00 - 21:00") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.updateReceiptCustomization(profile.receiptShowSku, receiptHeaderNoteInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Simpan Catatan Struk") }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Nominal Cepat Cash", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tombol pintasan uang yang muncul di layar Pembayaran saat metode Cash dipilih, " +
                    "supaya kasir tinggal tap nominal uang yang diberikan pelanggan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val quickCashAmounts = profile.quickCashAmountList()
                    if (quickCashAmounts.isEmpty()) {
                        Text(
                            "Belum ada nominal cepat. Tambahkan lewat kolom di bawah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickCashAmounts.forEach { amount ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(formatQuickCash(amount)) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Hapus nominal ${formatQuickCash(amount)}",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.removeQuickCashAmount(amount) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newQuickCashInput,
                            onValueChange = { newQuickCashInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Nominal baru") },
                            placeholder = { Text("Contoh: 50000") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                newQuickCashInput.toLongOrNull()?.let { viewModel.addQuickCashAmount(it) }
                                newQuickCashInput = ""
                            },
                            enabled = newQuickCashInput.toLongOrNull()?.let { it > 0 } == true
                        ) { Text("Tambah") }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Gambar QRIS", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ditampilkan otomatis di layar pembayaran saat pelanggan memilih QRIS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (profile.qrisImagePath != null) {
                        AsyncImage(
                            model = profile.qrisImagePath,
                            contentDescription = "Gambar QRIS",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        val dynamicActive = profile.qrisRawContent != null
                        AssistChip(
                            onClick = {},
                            label = { Text(if (dynamicActive) "QRIS Dinamis aktif (nominal otomatis)" else "Nominal manual (kode QR tidak terbaca)") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (dynamicActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { autoLockManager.expectExternalActivityReturn(); qrisPickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ganti Gambar")
                            }
                            OutlinedButton(onClick = { viewModel.setQrisImagePath(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hapus")
                            }
                        }
                    } else {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada gambar QRIS", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { autoLockManager.expectExternalActivityReturn(); qrisPickerLauncher.launch("image/*") }) {
                            Text("Upload Gambar QRIS")
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Logo Toko", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Dicetak di bagian atas struk Bluetooth dan invoice PDF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (profile.logoImagePath != null) {
                        AsyncImage(
                            model = profile.logoImagePath,
                            contentDescription = "Logo toko",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { autoLockManager.expectExternalActivityReturn(); logoPickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ganti Logo")
                            }
                            OutlinedButton(onClick = { viewModel.setLogoImagePath(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hapus")
                            }
                        }
                    } else {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada logo toko", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { autoLockManager.expectExternalActivityReturn(); logoPickerLauncher.launch("image/*") }) {
                            Text("Upload Logo Toko")
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Tampilan Aplikasi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ubah warna aksen aplikasi (tombol, highlight, ikon aktif)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(PosAccentPresets) { (label, color) ->
                            val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
                            val isSelected = profile.appColorHex?.equals(hex, ignoreCase = true) == true
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        customHexInput = hex
                                        viewModel.setAppColor(hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = label,
                                        tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Atau masukkan kode warna sendiri", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(parseHexColorOrNull(customHexInput) ?: MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { customHexInput = it },
                            label = { Text("Kode Hex (mis. #E8590C)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val parsed = parseHexColorOrNull(customHexInput)
                                if (parsed != null) {
                                    val normalized = String.format("#%06X", 0xFFFFFF and parsed.toArgb())
                                    customHexInput = normalized
                                    viewModel.setAppColor(normalized)
                                }
                            },
                            enabled = parseHexColorOrNull(customHexInput) != null
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Terapkan Warna")
                        }
                        OutlinedButton(onClick = {
                            customHexInput = ""
                            viewModel.setAppColor(null)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset ke Default")
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Font Aplikasi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Butuh koneksi internet sekali untuk mengunduh font baru, setelah itu tersimpan di HP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PosFontOption.entries.forEach { option ->
                            val isSelected = profile.fontChoice == option.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setFontChoice(option.key) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { viewModel.setFontChoice(option.key) })
                                Spacer(Modifier.width(4.dp))
                                Text(option.label, fontFamily = option.family)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Bahasa Struk & Invoice", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bahasa yang dipakai di label struk cetak & PDF invoice (mis. Subtotal/Total)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = profile.receiptLanguage == "id",
                            onClick = { viewModel.setReceiptLanguage("id") },
                            label = { Text("Indonesia") }
                        )
                        FilterChip(
                            selected = profile.receiptLanguage == "en",
                            onClick = { viewModel.setReceiptLanguage("en") },
                            label = { Text("English") }
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Printer Struk", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pilih jenis koneksi printer thermal yang dipakai toko ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    var connType by remember(profile.printerConnectionType) { mutableStateOf(profile.printerConnectionType) }
                    var lanIp by remember(profile.printerLanIp) { mutableStateOf(profile.printerLanIp) }
                    var lanPort by remember(profile.printerLanPort) { mutableStateOf(profile.printerLanPort.toString()) }
                    var paperWidth by remember(profile.printerPaperWidthMm) { mutableStateOf(profile.printerPaperWidthMm) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = connType == "BLUETOOTH",
                            onClick = {
                                connType = "BLUETOOTH"
                                viewModel.setPrinterConnection(connType, lanIp, lanPort.toIntOrNull() ?: 9100, paperWidth)
                            },
                            label = { Text("Bluetooth") }
                        )
                        FilterChip(
                            selected = connType == "LAN",
                            onClick = {
                                connType = "LAN"
                                viewModel.setPrinterConnection(connType, lanIp, lanPort.toIntOrNull() ?: 9100, paperWidth)
                            },
                            label = { Text("LAN/WiFi") }
                        )
                        FilterChip(
                            selected = connType == "USB",
                            onClick = {
                                connType = "USB"
                                viewModel.setPrinterConnection(connType, lanIp, lanPort.toIntOrNull() ?: 9100, paperWidth)
                            },
                            label = { Text("USB") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = paperWidth < 60f,
                            onClick = {
                                paperWidth = 48f
                                viewModel.setPrinterConnection(connType, lanIp, lanPort.toIntOrNull() ?: 9100, paperWidth)
                            },
                            label = { Text("Kertas 58mm") }
                        )
                        FilterChip(
                            selected = paperWidth >= 60f,
                            onClick = {
                                paperWidth = 72f
                                viewModel.setPrinterConnection(connType, lanIp, lanPort.toIntOrNull() ?: 9100, paperWidth)
                            },
                            label = { Text("Kertas 80mm") }
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    when (connType) {
                        "LAN" -> {
                            OutlinedTextField(
                                value = lanIp,
                                onValueChange = { lanIp = it },
                                label = { Text("Alamat IP Printer") },
                                placeholder = { Text("mis. 192.168.1.50") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = lanPort,
                                onValueChange = { lanPort = it.filter { c -> c.isDigit() } },
                                label = { Text("Port (default 9100)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.setPrinterConnection(connType, lanIp, lanPort.toIntOrNull() ?: 9100, paperWidth) }) {
                                Text("Simpan Alamat Printer")
                            }
                        }
                        "USB" -> {
                            var usbPrinters by remember { mutableStateOf(viewModel.listUsbPrinters()) }
                            if (usbPrinters.isEmpty()) {
                                Text(
                                    "Belum ada printer USB terdeteksi. Sambungkan lewat kabel OTG lalu muat ulang.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                usbPrinters.forEach { name -> Text("• $name", style = MaterialTheme.typography.bodySmall) }
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { usbPrinters = viewModel.listUsbPrinters() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Muat Ulang Daftar Printer USB")
                            }
                        }
                        else -> {
                            var pairedPrinters by remember { mutableStateOf(viewModel.listPairedPrinters()) }
                            if (pairedPrinters.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Belum ada printer Bluetooth yang di-pairing. Pairing dulu lewat Pengaturan Bluetooth Android.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { pairedPrinters = viewModel.listPairedPrinters() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Muat Ulang")
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    pairedPrinters.forEach { printerName ->
                                        val isSelected = profile.selectedPrinterName == printerName
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.setSelectedPrinter(printerName) }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = isSelected, onClick = { viewModel.setSelectedPrinter(printerName) })
                                            Spacer(Modifier.width(4.dp))
                                            Text(printerName)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = { pairedPrinters = viewModel.listPairedPrinters() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Muat Ulang Daftar Printer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Baris toggle fitur generik dipakai untuk semua switch di seksi "Tipe Bisnis & Fitur". */
@Composable
private fun FeatureToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
