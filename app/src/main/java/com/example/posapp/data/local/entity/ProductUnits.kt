package com.example.posapp.data.local.entity

/**
 * Preset satuan produk yang paling umum dipakai toko/gudang (retail & grosir campuran).
 * Dipakai di dropdown form Produk ([com.example.posapp.presentation.product.ProductScreen])
 * — pengguna tetap bisa mengetik satuan bebas lewat opsi "Lainnya".
 */
object ProductUnits {
    val PRESETS: List<String> = listOf(
        "pcs", "kg", "gram", "liter", "ml", "dus", "box", "pack", "lusin", "meter", "botol", "karung"
    )

    /** Satuan default untuk produk baru. */
    const val DEFAULT: String = "pcs"

    /** true bila satuan bukan salah satu preset (berarti diketik manual oleh pengguna). */
    fun isCustom(unit: String): Boolean = unit.isNotBlank() && PRESETS.none { it.equals(unit, ignoreCase = true) }
}
