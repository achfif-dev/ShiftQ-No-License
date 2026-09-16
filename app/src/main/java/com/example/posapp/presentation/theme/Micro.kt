package com.example.posapp.presentation.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Kit micro-interaction & loading kecil yang dipakai lintas layar (Dashboard, Kasir, dsb.) supaya
 * angka/kartu tidak "meloncat" tiba-tiba dan status loading terasa lebih hidup dibanding
 * `CircularProgressIndicator` polos di tengah layar kosong — bagian dari peningkatan UI/UX yang
 * sebelumnya disarankan di README ("Saran UI/UX").
 */

/**
 * Menampilkan [value] dengan animasi menghitung naik/turun dari nilai sebelumnya, bukan langsung
 * loncat — dipakai untuk angka omzet/total yang sering berubah reaktif (checkout baru masuk,
 * filter tanggal diganti, dsb.). [format] mengonversi nilai akhir (mis. ke Rupiah) di setiap
 * frame animasi.
 */
@Composable
fun CountUpText(
    value: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalDefaultCountUpStyle,
    color: Color = Color.Unspecified,
) {
    val animated = animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "count_up",
    )
    Text(format(animated.value.toDouble()), modifier = modifier, style = style, color = color, maxLines = 1)
}

/** Sama seperti [CountUpText] tapi untuk nilai bulat (jumlah transaksi, jumlah produk, dst.). */
@Composable
fun CountUpIntText(
    value: Int,
    format: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    style: TextStyle = LocalDefaultCountUpStyle,
    color: Color = Color.Unspecified,
) {
    val animated = animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "count_up_int",
    )
    Text(format(animated.value.toInt()), modifier = modifier, style = style, color = color, maxLines = 1)
}

private val LocalDefaultCountUpStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium

/**
 * Efek shimmer (kilau bergeser) untuk placeholder skeleton — dipakai lewat [ShimmerBox] pada
 * kotak berbentuk kartu/teks yang sedang menunggu data, jadi layar terasa "sedang memuat sesuatu
 * yang nyata" alih-alih spinner kosong di tengah layar.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 300f),
    )
}

/** Satu kotak placeholder shimmer — dipakai sebagai building block [SkeletonSummaryCardRow] dkk. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Column(modifier = modifier.clip(shape).background(shimmerBrush())) {}
}

/**
 * Skeleton untuk sepasang kartu ringkasan ala Dashboard, dipakai selama `isLoading` alih-alih
 * `CircularProgressIndicator` di tengah layar kosong — bentuknya sengaja meniru layout kartu asli
 * (ikon bulat kecil + 2 baris teks) supaya transisi ke data asli terasa mulus, bukan "lompat".
 */
@Composable
fun SkeletonSummaryCardRow(modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        repeat(2) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    ShimmerBox(modifier = Modifier.size(32.dp), shape = CircleShape)
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(modifier = Modifier.width(48.dp).height(12.dp))
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(modifier = Modifier.width(80.dp).height(18.dp))
                }
            }
        }
    }
}

/** Skeleton baris list generik (mis. produk terlaris) — [lines] baris, masing-masing avatar+2 teks. */
@Composable
fun SkeletonListRows(lines: Int = 3, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            repeat(lines) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    ShimmerBox(modifier = Modifier.size(36.dp), shape = CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp))
                    }
                }
            }
        }
    }
}

/** Skeleton kartu grafik tren — dipakai selama Dashboard memuat data tren omzet 7 hari. */
@Composable
fun SkeletonChartCard(height: Dp = 168.dp, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(height))
        }
    }
}

/**
 * Overlay ringan berisi centang animasi (scale-in dengan efek pantul + fade) untuk konfirmasi
 * checkout sukses — menggantikan Snackbar teks polos yang mudah terlewat/tertutup keyboard.
 * Auto-hilang sendiri setelah [autoDismissMillis], atau bisa ditutup lebih awal dengan tap di
 * mana saja. Dipakai di PosScreen begitu event `PosEvent.CheckoutSuccess` diterima.
 */
@Composable
fun CheckoutSuccessOverlay(
    visible: Boolean,
    invoiceNumber: String,
    subtitle: String,
    onDismissRequest: () -> Unit,
    autoDismissMillis: Long = 1600L,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        LaunchedEffect(Unit) {
            delay(autoDismissMillis)
            onDismissRequest()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }
                    val iconScale by animateFloatAsState(
                        targetValue = if (shown) 1f else 0.4f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "checkmark_scale",
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(iconScale)
                            .clip(CircleShape)
                            .background(PosSuccess.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = PosSuccess, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Transaksi Berhasil", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(invoiceNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
