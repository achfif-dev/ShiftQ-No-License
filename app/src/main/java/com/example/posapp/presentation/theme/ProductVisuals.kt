package com.example.posapp.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// Dipusatkan di sini (bukan didefinisikan ulang per layar) supaya avatar produk di Kasir,
// Produk, dan Stok selalu tampil SENADA — warna aksen & ikon kategori yang sama untuk
// produk yang sama, di layar manapun ia muncul.

// Palet aksen berputar untuk avatar produk, dipilih berdasarkan nama produk supaya daftar
// produk terlihat berwarna & mudah dibedakan sekilas, bukan flat/polos.
private val productAccentPalette = listOf(
    Color(0xFFD9530C), Color(0xFF35415C), Color(0xFF0277BD),
    Color(0xFF2E7D32), Color(0xFFAD1457), Color(0xFFB07A00)
)

fun accentColorFor(name: String): Color =
    productAccentPalette[(name.hashCode().let { if (it < 0) -it else it }) % productAccentPalette.size]

/**
 * Ikon default per kategori produk, dipakai saat produk belum punya foto sendiri.
 * Dicocokkan dari nama kategori (case-insensitive, berbasis kata kunci) supaya tetap
 * bekerja untuk kategori custom buatan toko, bukan daftar tetap yang kaku.
 */
fun iconForCategory(categoryName: String?): ImageVector {
    val n = categoryName?.lowercase().orEmpty()
    return when {
        n.isBlank() -> Icons.Default.Inventory2
        listOf("baju", "pakaian", "fashion", "apparel", "kaos", "kemeja", "hem", "celana", "jaket").any { n.contains(it) } ->
            Icons.Default.Checkroom
        listOf("makanan", "food", "snack", "kue", "roti", "cake", "kuliner").any { n.contains(it) } ->
            Icons.Default.Fastfood
        listOf("minuman", "drink", "kopi", "teh", "jus", "beverage").any { n.contains(it) } ->
            Icons.Default.LocalCafe
        listOf("elektronik", "gadget", "hp", "komputer", "electronic").any { n.contains(it) } ->
            Icons.Default.Devices
        listOf("kosmetik", "kecantikan", "skincare", "beauty").any { n.contains(it) } ->
            Icons.Default.Face
        listOf("obat", "kesehatan", "farmasi", "apotek", "health").any { n.contains(it) } ->
            Icons.Default.MedicalServices
        listOf("mainan", "toys", "toy").any { n.contains(it) } ->
            Icons.Default.Toys
        listOf("rumah tangga", "peralatan rumah", "household", "perabot").any { n.contains(it) } ->
            Icons.Default.Chair
        listOf("atk", "alat tulis", "kantor", "buku", "stationery").any { n.contains(it) } ->
            Icons.Default.Edit
        else -> Icons.Default.Category
    }
}

/**
 * Avatar produk bulat yang dipakai konsisten di Kasir, Produk, dan Stok: foto produk bila
 * ada, atau ikon sesuai kategori dengan warna aksen berbasis nama produk bila belum ada foto.
 */
@Composable
fun ProductAvatar(
    photoPath: String?,
    name: String,
    categoryName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val accent = accentColorFor(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (photoPath != null) {
            AsyncImage(
                model = photoPath,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            Icon(
                iconForCategory(categoryName),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.46f)
            )
        }
    }
}
