package com.example.posapp.presentation.theme

import androidx.compose.ui.graphics.Color

// Palet "profesional retail" — bukan palet baseline Material You (ungu 0xFF6750A4 bawaan
// Google dihindari karena itu yang membuat aplikasi langsung terlihat "template Android
// default"). Aksen utama tetap oranye hangat khas kasir/retail, dipadu abu-navy gelap
// (bukan ungu) untuk elemen sekunder agar terasa lebih seperti aplikasi bisnis/finansial.
//
// Kanvas (background) dan permukaan kartu (surface/surfaceContainerLow) sengaja dibuat
// kontras jelas — kanvas abu sejuk, kartu putih bersih — supaya ada kedalaman/layering
// yang terlihat, bukan flat & menyatu seperti tampilan Material default yang polos.

val PosPrimaryLight = Color(0xFFD9530C)      // Aksen utama — tombol bayar, FAB, highlight
val PosOnPrimaryLight = Color(0xFFFFFFFF)
val PosPrimaryContainerLight = Color(0xFFFFE0CC)
val PosOnPrimaryContainerLight = Color(0xFF441800)

val PosSecondaryLight = Color(0xFF35415C)     // Navy-slate, pengganti ungu default M3
val PosOnSecondaryLight = Color(0xFFFFFFFF)
val PosSecondaryContainerLight = Color(0xFFDCE2F0)
val PosOnSecondaryContainerLight = Color(0xFF141D30)

val PosTertiaryLight = Color(0xFF0277BD)      // Info sekunder / chip kategori
val PosOnTertiaryLight = Color(0xFFFFFFFF)
val PosTertiaryContainerLight = Color(0xFFCDE6FA)
val PosOnTertiaryContainerLight = Color(0xFF00344E)

val PosErrorLight = Color(0xFFC22C1F)
val PosOnErrorLight = Color(0xFFFFFFFF)
val PosErrorContainerLight = Color(0xFFFFDAD4)
val PosOnErrorContainerLight = Color(0xFF410E04)

val PosBackgroundLight = Color(0xFFF1F2F6)    // Kanvas abu sejuk — beda jelas dari kartu putih
val PosOnBackgroundLight = Color(0xFF1A2130)
val PosSurfaceLight = Color(0xFFFFFFFF)
val PosOnSurfaceLight = Color(0xFF1A2130)
val PosSurfaceVariantLight = Color(0xFFE3E7EE)
val PosOnSurfaceVariantLight = Color(0xFF5B6472)
val PosOutlineLight = Color(0xFF97A0B1)
val PosSurfaceContainerLight = Color(0xFFF6F7FA)
val PosSurfaceContainerHighLight = Color(0xFFEDEFF4)
val PosSurfaceContainerLowLight = Color(0xFFFFFFFF) // Warna kartu — putih bersih di atas kanvas abu

val PosPrimaryDark = Color(0xFFFFB68C)
val PosOnPrimaryDark = Color(0xFF5A2000)
val PosPrimaryContainerDark = Color(0xFF7F2E00)
val PosOnPrimaryContainerDark = Color(0xFFFFDBC7)

val PosSecondaryDark = Color(0xFFB7C3DE)
val PosOnSecondaryDark = Color(0xFF1E2A42)
val PosSecondaryContainerDark = Color(0xFF344059)
val PosOnSecondaryContainerDark = Color(0xFFDCE2F0)

val PosTertiaryDark = Color(0xFF7FC8F0)
val PosOnTertiaryDark = Color(0xFF00344E)
val PosTertiaryContainerDark = Color(0xFF01507A)
val PosOnTertiaryContainerDark = Color(0xFFCDE6FA)

val PosErrorDark = Color(0xFFFFB4A8)
val PosOnErrorDark = Color(0xFF680E00)
val PosErrorContainerDark = Color(0xFF8F2415)
val PosOnErrorContainerDark = Color(0xFFFFDAD4)

val PosBackgroundDark = Color(0xFF10131A)
val PosOnBackgroundDark = Color(0xFFE4E6ED)
val PosSurfaceDark = Color(0xFF171B23)
val PosOnSurfaceDark = Color(0xFFE4E6ED)
val PosSurfaceVariantDark = Color(0xFF2E333F)
val PosOnSurfaceVariantDark = Color(0xFFA7AFBD)
val PosOutlineDark = Color(0xFF5B6472)
val PosSurfaceContainerDark = Color(0xFF1B1F28)
val PosSurfaceContainerHighDark = Color(0xFF232833)
val PosSurfaceContainerLowDark = Color(0xFF14171E)

// Warna status yang dipakai lintas layar (stok tipis, sukses, dsb.)
val PosSuccess = Color(0xFF1C8A4B)
val PosWarning = Color(0xFFB07A00)
