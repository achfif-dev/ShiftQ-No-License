package com.example.posapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PosPrimaryLight,
    onPrimary = PosOnPrimaryLight,
    primaryContainer = PosPrimaryContainerLight,
    onPrimaryContainer = PosOnPrimaryContainerLight,
    secondary = PosSecondaryLight,
    onSecondary = PosOnSecondaryLight,
    secondaryContainer = PosSecondaryContainerLight,
    onSecondaryContainer = PosOnSecondaryContainerLight,
    tertiary = PosTertiaryLight,
    onTertiary = PosOnTertiaryLight,
    tertiaryContainer = PosTertiaryContainerLight,
    onTertiaryContainer = PosOnTertiaryContainerLight,
    error = PosErrorLight,
    onError = PosOnErrorLight,
    errorContainer = PosErrorContainerLight,
    onErrorContainer = PosOnErrorContainerLight,
    background = PosBackgroundLight,
    onBackground = PosOnBackgroundLight,
    surface = PosSurfaceLight,
    onSurface = PosOnSurfaceLight,
    surfaceVariant = PosSurfaceVariantLight,
    onSurfaceVariant = PosOnSurfaceVariantLight,
    outline = PosOutlineLight,
    surfaceContainer = PosSurfaceContainerLight,
    surfaceContainerHigh = PosSurfaceContainerHighLight,
    surfaceContainerLow = PosSurfaceContainerLowLight
)

private val DarkColors = darkColorScheme(
    primary = PosPrimaryDark,
    onPrimary = PosOnPrimaryDark,
    primaryContainer = PosPrimaryContainerDark,
    onPrimaryContainer = PosOnPrimaryContainerDark,
    secondary = PosSecondaryDark,
    onSecondary = PosOnSecondaryDark,
    secondaryContainer = PosSecondaryContainerDark,
    onSecondaryContainer = PosOnSecondaryContainerDark,
    tertiary = PosTertiaryDark,
    onTertiary = PosOnTertiaryDark,
    tertiaryContainer = PosTertiaryContainerDark,
    onTertiaryContainer = PosOnTertiaryContainerDark,
    error = PosErrorDark,
    onError = PosOnErrorDark,
    errorContainer = PosErrorContainerDark,
    onErrorContainer = PosOnErrorContainerDark,
    background = PosBackgroundDark,
    onBackground = PosOnBackgroundDark,
    surface = PosSurfaceDark,
    onSurface = PosOnSurfaceDark,
    surfaceVariant = PosSurfaceVariantDark,
    onSurfaceVariant = PosOnSurfaceVariantDark,
    outline = PosOutlineDark,
    surfaceContainer = PosSurfaceContainerDark,
    surfaceContainerHigh = PosSurfaceContainerHighDark,
    surfaceContainerLow = PosSurfaceContainerLowDark
)

/**
 * Tema Material 3 modern minimalis untuk seluruh aplikasi.
 * Mengikuti tema gelap sistem secara otomatis (isSystemInDarkTheme).
 *
 * @param customPrimaryHex Warna aksen aplikasi custom dari Pengaturan > Tampilan Aplikasi,
 * format "#RRGGBB". Bila null atau tidak valid, dipakai palet default (oranye).
 * @param fontChoice Key font dari [PosFontOption] (mis. "inter", "poppins"). Bila null atau
 * tidak dikenal, dipakai font default Plus Jakarta Sans.
 */
@Composable
fun PosAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customPrimaryHex: String? = null,
    fontChoice: String? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val colorScheme = remember(baseScheme, customPrimaryHex, darkTheme) {
        val customColor = customPrimaryHex?.let { parseHexColorOrNull(it) }
        if (customColor != null) applyCustomPrimaryColor(customColor, darkTheme, baseScheme) else baseScheme
    }
    val typography = remember(fontChoice) { posTypography(PosFontOption.fromKey(fontChoice).family) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = PosShapes,
        content = content
    )
}

/** Parse string hex ("#RRGGBB", "RRGGBB", atau dengan alpha) jadi [Color], null bila tidak valid. */
fun parseHexColorOrNull(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    return try {
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Menurunkan warna primary/primaryContainer (+ pasangan "on") dari satu warna aksen yang
 * dipilih pemilik toko, lalu menimpanya di atas skema warna dasar (light/dark) agar seluruh
 * elemen ber-aksen di aplikasi (tombol bayar, FAB, highlight, dsb.) ikut berubah otomatis.
 */
private fun applyCustomPrimaryColor(base: Color, darkTheme: Boolean, fallback: ColorScheme): ColorScheme {
    val primary = if (darkTheme) blend(base, Color.White, 0.30f) else base
    val onPrimary = contrastingOnColor(primary)
    val primaryContainer = if (darkTheme) blend(base, Color.Black, 0.55f) else blend(base, Color.White, 0.78f)
    val onPrimaryContainer = if (darkTheme) blend(base, Color.White, 0.75f) else blend(base, Color.Black, 0.62f)
    return fallback.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer
    )
}

private fun blend(from: Color, to: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = from.red * inv + to.red * ratio,
        green = from.green * inv + to.green * ratio,
        blue = from.blue * inv + to.blue * ratio,
        alpha = 1f
    )
}

/** Putih atau hitam, dipilih berdasarkan luminansi relatif agar teks/ikon di atasnya tetap terbaca. */
private fun contrastingOnColor(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.55f) Color.Black else Color.White
}

/** Preset warna aksen yang ditawarkan di layar Profil Toko > Tampilan Aplikasi. */
val PosAccentPresets: List<Pair<String, Color>> = listOf(
    "Oranye (Default)" to Color(0xFFE8590C),
    "Merah" to Color(0xFFD32F2F),
    "Merah Muda" to Color(0xFFC2185B),
    "Ungu" to Color(0xFF6750A4),
    "Biru" to Color(0xFF1565C0),
    "Biru Muda" to Color(0xFF0288D1),
    "Hijau Tosca" to Color(0xFF00796B),
    "Hijau" to Color(0xFF2E7D32),
    "Kuning" to Color(0xFFF9A825),
    "Coklat" to Color(0xFF5D4037),
    "Abu Gelap" to Color(0xFF37474F)
)
