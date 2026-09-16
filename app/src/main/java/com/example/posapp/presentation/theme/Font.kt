package com.example.posapp.presentation.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.posapp.R

/**
 * Beberapa pilihan font "profesional" (bukan Roboto bawaan sistem Android) yang bisa dipilih
 * pemilik toko lewat Pengaturan > Profil Toko > Tampilan Aplikasi > Font.
 *
 * Semua font diambil via Downloadable Fonts API Google Play Services (GoogleFont), jadi TIDAK
 * perlu membundel file .ttf di dalam APK. Catatan penting: karena diunduh saat runtime, font
 * butuh koneksi internet + Google Play Services minimal SEKALI (setelah itu di-cache di
 * perangkat & jalan offline seperti biasa). Selama font belum berhasil diunduh (mis. HP tanpa
 * Play Services / belum pernah online), teks otomatis kembali memakai font sistem default —
 * aplikasi tetap jalan normal, tidak crash.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun googleFontFamily(name: String): FontFamily {
    val googleFont = GoogleFont(name)
    return FontFamily(
        Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
    )
}

/**
 * Satu opsi font yang bisa dipilih pengguna: [key] disimpan di DataStore, [label] ditampilkan di UI.
 *
 * PENTING: [family] dibuat LAZY (bukan langsung sebagai constructor param) dengan sengaja.
 * Sebelumnya setiap konstanta enum langsung memanggil googleFontFamily(...) saat class ini
 * di-load, DAN top-level val PosFontFamily di bawah juga langsung mengakses enum ini saat
 * class-nya sendiri (FontKt) di-load. Kombinasi dua inisialisasi "eager" yang saling
 * mereferensikan ini memicu circular class-initialization di JVM/ART: ketika PosFontOption
 * sedang diinisialisasi lalu tidak sengaja memicu load FontKt yang balik mengakses
 * PosFontOption sebelum selesai, JVM/ART tidak mengulang initializer (sesuai spec) sehingga
 * field enum masih null -> NullPointerException -> ExceptionInInitializerError (persis error
 * yang dilaporkan). Dengan membuat family lazy per-konstanta, konstruksi FontFamily baru
 * terjadi saat benar-benar dipakai (di Theme.kt), jauh setelah class PosFontOption selesai
 * diinisialisasi, sehingga tidak ada lagi celah circular init.
 */
enum class PosFontOption(val key: String, val label: String, private val googleFontName: String?) {
    PLUS_JAKARTA_SANS("plus_jakarta_sans", "Plus Jakarta Sans (Default)", "Plus Jakarta Sans"),
    INTER("inter", "Inter", "Inter"),
    POPPINS("poppins", "Poppins", "Poppins"),
    NUNITO_SANS("nunito_sans", "Nunito Sans", "Nunito Sans"),
    SYSTEM_DEFAULT("system_default", "Sistem (Roboto)", null);

    /** Dibangun sekali saat pertama diakses (bukan saat class di-load), lalu di-cache. */
    val family: FontFamily by lazy(LazyThreadSafetyMode.PUBLICATION) {
        googleFontName?.let { googleFontFamily(it) } ?: FontFamily.Default
    }

    companion object {
        fun fromKey(key: String?): PosFontOption = entries.find { it.key == key } ?: PLUS_JAKARTA_SANS
    }
}

/** Font default aplikasi bila belum ada pengaturan tersimpan (dipakai juga oleh preview & pratinjau). */
val PosFontFamily: FontFamily by lazy(LazyThreadSafetyMode.PUBLICATION) { PosFontOption.PLUS_JAKARTA_SANS.family }
