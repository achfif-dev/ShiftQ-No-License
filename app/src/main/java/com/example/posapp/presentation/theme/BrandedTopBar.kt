package com.example.posapp.presentation.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Top app bar bermerek yang dipakai konsisten di SEMUA layar, menggantikan TopAppBar
 * polos bawaan Material yang flat & tidak ada identitas. Bedanya dari TopAppBar biasa:
 * - Latar tetap bersih (putih di atas kanvas abu) supaya tetap enak dibaca lama,
 * - tapi diberi garis aksen tipis berwarna primary (oranye brand) persis di bawahnya,
 *   jadi setiap layar punya penanda visual yang sama & langsung terasa "satu produk",
 *   bukan kumpulan layar Material default yang berdiri sendiri-sendiri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosBrandedTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    androidx.compose.foundation.layout.Column {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Logo toko untuk header — dipakai foto logo tersimpan (Pengaturan > Profil Toko) bila
 * ada, atau ikon toko default dengan warna aksen brand. Dipakai di Dashboard & Kasir,
 * dua layar utama yang paling sering dilihat kasir/pemilik toko.
 */
@Composable
fun StoreLogo(logoPath: String?, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (logoPath != null) {
            AsyncImage(
                model = logoPath,
                contentDescription = "Logo toko",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
