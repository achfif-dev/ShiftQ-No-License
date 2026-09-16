package com.example.posapp.domain.model

import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity

data class CartLine(
    val product: ProductEntity,
    val variant: ProductVariantEntity? = null, // diisi bila produk punya matrix varian
    val quantity: Int = 1,
    val discount: Double = 0.0,
    val note: String? = null,
    /** Potongan dari promo otomatis (CATEGORY_PERCENT / BUY_X_GET_Y_FREE) — v15, DIHITUNG ULANG
     * oleh [com.example.posapp.domain.usecase.PromoEngine] setiap kali isi keranjang/daftar promo
     * aktif berubah, bukan diinput manual kasir. Terpisah dari [discount] (diskon manual per
     * baris) supaya keduanya tidak saling menimpa, tapi diperlakukan SAMA seperti [discount] di
     * [lineTotal]/[Cart.subtotal] (mengurangi "net" baris, bukan masuk ke [Cart.totalDiscount]) —
     * konsisten dengan cara diskon manual per baris sudah bekerja sebelum promo ada. */
    val promoDiscount: Double = 0.0
) {
    /** Kunci unik baris di keranjang: kombinasi produk + varian (bila ada). */
    val lineKey: String get() = "${product.id}:${variant?.id ?: 0}"

    val unitPrice: Double get() = variant?.priceOverride ?: product.sellPrice

    val lineTotal: Double
        get() = ((unitPrice * quantity) - discount - promoDiscount).coerceAtLeast(0.0)

    /** Stok yang relevan untuk validasi keranjang: stok varian jika ada, atau stok produk. */
    val availableStock: Int get() = variant?.stock ?: product.stock
}

data class Cart(
    val lines: List<CartLine> = emptyList(),
    val transactionDiscount: Double = 0.0,
    val taxPercent: Double = 11.0, // default PPN Indonesia
    /** Jumlah poin loyalitas yang ditukar pelanggan pada transaksi ini (v13) — 0 jika fitur
     * loyalitas nonaktif atau pelanggan tidak menukar poin. Dikurangi dari saldo poin
     * pelanggan setelah checkout berhasil, lihat CheckoutUseCase. */
    val loyaltyPointsRedeemed: Long = 0,
    /** Nilai Rupiah dari [loyaltyPointsRedeemed] (points * StoreProfile.loyaltyPointValueRupiah,
     * sudah dibatasi PosViewModel agar tidak melebihi saldo poin maupun subtotal belanja). */
    val loyaltyDiscount: Double = 0.0,
    /** Nomor meja atau nama pemesan (v13) — hanya relevan saat StoreProfile.tableTaggingEnabled
     * aktif (mode Resto/Kafe). Disimpan ke TransactionEntity.note saat checkout, lihat
     * PosViewModel.checkout(). Null/kosong = tidak dipakai (mode Retail/Umum). */
    val tableTag: String? = null,
    /** Potongan TRANSAKSI (bukan per-baris) dari promo tipe PERCENT_MIN_PURCHASE (v15) — DIHITUNG
     * ULANG oleh [com.example.posapp.domain.usecase.PromoEngine], bukan diset manual dari UI.
     * Diperlakukan sama seperti [transactionDiscount]/[loyaltyDiscount]: masuk ke [totalDiscount],
     * BUKAN ke [subtotal] (beda dari promo per-baris di [CartLine.promoDiscount] yang sudah
     * "tersembunyi" di dalam [CartLine.lineTotal] masing-masing, sama seperti diskon manual
     * per-baris) — supaya tidak dihitung dua kali. */
    val promoDiscount: Double = 0.0,
    /** Nama promo yang sedang aktif diterapkan ke keranjang ini, untuk ditampilkan ke kasir
     * (mis. "Diskon 10% belanja min. Rp100rb") — murni informasi tampilan, tidak memengaruhi
     * perhitungan total. Diisi oleh PromoEngine bersamaan dengan potongan promonya. */
    val appliedPromoNames: List<String> = emptyList()
) {
    /** Sudah bersih dari diskon manual PER BARIS & promo PER BARIS (lihat [CartLine.lineTotal])
     * — pola ini sudah begitu sejak sebelum promo ada, dipertahankan supaya tidak dihitung ganda. */
    val subtotal: Double
        get() = lines.sumOf { it.lineTotal }

    /** Total potongan TRANSAKSI (bukan per-baris) sebelum pajak: diskon manual transaksi +
     * penukaran poin loyalitas + promo min-belanja otomatis. */
    val totalDiscount: Double
        get() = transactionDiscount + loyaltyDiscount + promoDiscount

    val taxAmount: Double
        get() = ((subtotal - totalDiscount).coerceAtLeast(0.0)) * (taxPercent / 100.0)

    val total: Double
        get() = (subtotal - totalDiscount + taxAmount).coerceAtLeast(0.0)

    val isEmpty: Boolean get() = lines.isEmpty()
}
