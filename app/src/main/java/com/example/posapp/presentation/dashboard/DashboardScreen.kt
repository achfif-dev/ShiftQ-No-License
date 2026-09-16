package com.example.posapp.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.presentation.theme.CountUpText
import com.example.posapp.presentation.theme.PosBrandedTopBar
import com.example.posapp.presentation.theme.SkeletonChartCard
import com.example.posapp.presentation.theme.SkeletonListRows
import com.example.posapp.presentation.theme.SkeletonSummaryCardRow
import com.example.posapp.presentation.theme.StoreLogo
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

/**
 * Dashboard ringkasan — halaman pertama setelah login. Menampilkan omzet & jumlah transaksi
 * hari ini, laba kotor, produk terlaris, dan peringatan stok tipis, plus jalan pintas ke
 * modul-modul utama (Kasir, Produk, Stok, Laporan, Pengaturan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenPos: () -> Unit = {},
    onOpenProducts: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenShift: () -> Unit = {},
    onOpenCustomers: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            PosBrandedTopBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StoreLogo(logoPath = uiState.storeLogoPath)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                uiState.storeName,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            uiState.cashierName?.let {
                                Text("Halo, $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPos,
                icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                text = { Text("Buka Kasir") },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            // Skeleton shimmer meniru bentuk kartu asli (bukan spinner polos di tengah layar
            // kosong) — transisi ke data sungguhan jadi terasa mulus begitu selesai dimuat.
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Ringkasan Hari Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SkeletonSummaryCardRow()
                SkeletonSummaryCardRow()
                SkeletonChartCard()
                SkeletonListRows(lines = 3)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Ringkasan Hari Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accent = MaterialTheme.colorScheme.primary,
                        label = "Omzet",
                    ) {
                        CountUpText(
                            value = uiState.todayRevenue,
                            format = { rupiah.format(it) },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Receipt,
                        accent = MaterialTheme.colorScheme.tertiary,
                        label = "Transaksi",
                    ) {
                        com.example.posapp.presentation.theme.CountUpIntText(
                            value = uiState.todayTransactions,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ShoppingCart,
                        accent = MaterialTheme.colorScheme.secondary,
                        label = "Laba Kotor",
                    ) {
                        CountUpText(
                            value = uiState.todayGrossProfit,
                            format = { rupiah.format(it) },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.WarningAmber,
                        accent = MaterialTheme.colorScheme.error,
                        label = "Stok Tipis",
                        onClick = onOpenStock,
                    ) {
                        Text("${uiState.lowStockCount} produk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            if (uiState.revenueTrend.isNotEmpty()) {
                item {
                    Text("Tren Omzet 7 Hari Terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    RevenueTrendChart(trend = uiState.revenueTrend, changePercent = uiState.revenueTrendChangePercent)
                }
            }

            item {
                Text("Produk Terlaris Hari Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (uiState.topProducts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Belum ada transaksi hari ini",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            uiState.topProducts.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.productName, fontWeight = FontWeight.SemiBold)
                                        Text("${item.totalQty} terjual", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(rupiah.format(item.totalRevenue), style = MaterialTheme.typography.bodyMedium)
                                }
                                if (index != uiState.topProducts.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item {
                Text("Menu Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    // "Produk" (harga beli/margin) disembunyikan dari Kasir — lihat
                    // Permission.canManageProducts. Rute "products" tetap digerbang independen
                    // di MainActivity sebagai lapis kedua (audit 2026-09-06).
                    if (uiState.canManageProducts) {
                        QuickMenuButton(Modifier.weight(1f), Icons.Default.Inventory2, "Produk", onOpenProducts)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    QuickMenuButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.TrendingUp, "Laporan", onOpenReports)
                    QuickMenuButton(Modifier.weight(1f), Icons.Default.LockClock, "Shift", onOpenShift)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickMenuButton(Modifier.weight(1f), Icons.Default.Person, "Pelanggan", onOpenCustomers)
                    if (uiState.isAdmin) {
                        QuickMenuButton(Modifier.weight(1f), Icons.Default.Settings, "Pengaturan", onOpenSettings)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(72.dp)) } // ruang untuk FAB
        }
    }
}

/** Logo toko di header Dashboard — implementasi bersama ada di theme/BrandedTopBar.kt. */

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: (() -> Unit)? = null,
    // Konten nilai lewat lambda (bukan String polos) supaya pemanggil bisa memasang
    // CountUpText/CountUpIntText yang animasinya ikut jalan otomatis tiap `uiState` berubah,
    // tanpa SummaryCard perlu tahu tipe datanya (Rupiah, jumlah produk, dst).
    value: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            value()
        }
    }
}

/**
 * Grafik garis tren omzet 7 hari terakhir (garis + area gradasi + titik per hari), digambar
 * langsung dengan Canvas (tanpa menambah library chart eksternal — konsisten dengan pendekatan
 * chart lain di app ini). [changePercent] menampilkan badge naik/turun dibanding 7 hari sebelum
 * periode ini, kalau tersedia (null kalau tidak ada data pembanding).
 */
@Composable
private fun RevenueTrendChart(trend: List<DayRevenue>, changePercent: Double?) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColorTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val fillColorBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxRevenue = (trend.maxOfOrNull { it.revenue } ?: 0.0).coerceAtLeast(1.0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tertinggi: ${rupiah.format(maxRevenue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (changePercent != null) {
                    TrendChangeBadge(changePercent)
                }
            }
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                if (trend.size < 2) return@Canvas
                val stepX = size.width / (trend.size - 1)
                val points = trend.mapIndexed { index, day ->
                    val fraction = (day.revenue / maxRevenue).toFloat().coerceIn(0f, 1f)
                    Offset(index * stepX, size.height - size.height * fraction)
                }

                // Garis dasar (baseline Rp0), referensi visual bawah grafik.
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )

                // Area gradasi di bawah garis tren.
                val areaPath = Path().apply {
                    moveTo(points.first().x, size.height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, size.height)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(listOf(fillColorTop, fillColorBottom))
                )

                // Garis tren utama.
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Titik per hari, hari terakhir (hari ini) ditandai lebih besar.
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = lineColor,
                        radius = if (index == points.lastIndex) 6f else 4f,
                        center = point
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                trend.forEach { day ->
                    Text(
                        day.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Badge kecil "+12,5% vs minggu lalu" / "-8,0% vs minggu lalu" di pojok kartu grafik tren. */
@Composable
private fun TrendChangeBadge(changePercent: Double) {
    val isUp = changePercent >= 0
    val color = if (isUp) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        val sign = if (isUp) "+" else ""
        Text(
            "$sign${"%.1f".format(changePercent)}% vs minggu lalu",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickMenuButton(modifier: Modifier = Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier, onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
