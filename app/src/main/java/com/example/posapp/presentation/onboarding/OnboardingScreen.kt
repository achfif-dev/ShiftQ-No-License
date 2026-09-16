package com.example.posapp.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.presentation.theme.PosAccentPresets

/**
 * Onboarding Wizard — ditampilkan SATU KALI, tepat setelah aplikasi diinstal & dibuka pertama
 * kali (sebelum layar setup PIN admin), supaya pemilik toko langsung mengisi profil dasar
 * (nama, alamat, jenis usaha, warna aksen, pajak) alih-alih harus mencarinya sendiri nanti di
 * Pengaturan > Profil Toko. Semua langkah bisa dilewati ("Lewati") — nilai defaultnya tetap
 * aman dipakai (bisa diubah lagi kapan saja lewat Pengaturan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinished: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) onFinished()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        if (uiState.step > 0) {
                            IconButton(onClick = viewModel::previousStep) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::skip, enabled = !uiState.isSaving) {
                            Text("Lewati")
                        }
                    }
                )
                LinearProgressIndicator(
                    progress = { (uiState.step + 1) / uiState.totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                val isLastStep = uiState.step == uiState.totalSteps - 1
                Button(
                    onClick = { if (isLastStep) viewModel.finish() else viewModel.nextStep() },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(if (isLastStep) "Selesai, Mulai Pakai App" else "Lanjut")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            when (uiState.step) {
                0 -> WelcomeStep()
                1 -> StoreDataStep(uiState = uiState, viewModel = viewModel)
                2 -> BrandAndTypeStep(uiState = uiState, viewModel = viewModel)
                else -> TaxAndFinishStep(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(32.dp))
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(88.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Selamat Datang di POS App!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        "Sebelum mulai jualan, mari lengkapi beberapa data dasar toko. Cuma butuh waktu " +
            "kurang dari 1 menit — semua bisa diubah lagi nanti kapan saja lewat Pengaturan.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StoreDataStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader("Data Toko", "Nama toko akan tampil di struk, Dashboard, dan invoice PDF")
    OutlinedTextField(
        value = uiState.storeName,
        onValueChange = viewModel::onStoreNameChange,
        label = { Text("Nama Toko") },
        placeholder = { Text("mis. Toko Sumber Rejeki") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = uiState.address,
        onValueChange = viewModel::onAddressChange,
        label = { Text("Alamat (opsional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = uiState.phone,
        onValueChange = viewModel::onPhoneChange,
        label = { Text("No. Telepon (opsional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BrandAndTypeStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader("Jenis Usaha & Warna", "Sesuaikan tampilan app dengan toko Anda")
    Text("Jenis Usaha", fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("GENERAL" to "Umum", "RETAIL" to "Retail/Toko", "FNB" to "Resto/Kafe").forEach { (value, label) ->
            FilterChip(
                selected = uiState.businessType == value,
                onClick = { viewModel.onBusinessTypeChange(value) },
                label = { Text(label) }
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Warna Aksen Aplikasi", fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(PosAccentPresets) { (label, color) ->
            val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
            val isSelected = uiState.appColorHex?.equals(hex, ignoreCase = true) == true
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
                    .clickable { viewModel.onColorChange(hex) },
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
}

@Composable
private fun TaxAndFinishStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader("Pajak Penjualan", "Aktifkan kalau toko Anda memungut PPN/pajak lain")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Aktifkan Pajak", fontWeight = FontWeight.Medium)
                    Text(
                        "Otomatis ditambahkan ke setiap transaksi di Kasir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = uiState.taxEnabled, onCheckedChange = viewModel::onTaxEnabledChange)
            }
            if (uiState.taxEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.taxPercent,
                    onValueChange = viewModel::onTaxPercentChange,
                    label = { Text("Persentase Pajak") },
                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Toko \"${uiState.storeName.ifBlank { "Toko Saya" }}\" siap dipakai. Tekan tombol di bawah untuk mulai jualan.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
