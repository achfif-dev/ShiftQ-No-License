package com.example.posapp.domain.usecase

import com.example.posapp.data.local.entity.PromoEntity
import com.example.posapp.data.local.entity.PromoType
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine

/**
 * Terapkan promo otomatis ke keranjang — dipanggil ulang oleh PosViewModel setiap kali isi
 * keranjang ATAU daftar promo aktif berubah (bukan sekali di checkout), supaya kasir langsung
 * melihat potongannya sebelum bayar, bukan kejutan di struk.
 *
 * Aturan penerapan (sengaja disederhanakan supaya hasilnya predictable buat kasir, bukan
 * "diskon termurah/termahal otomatis" yang membingungkan):
 * - BUY_X_GET_Y_FREE & PERCENT_CATEGORY: per baris keranjang, ambil SATU promo dengan potongan
 *   terbesar yang cocok (produk/kategori match) — tidak ditumpuk sesama tipe ini.
 * - PERCENT_MIN_PURCHASE: dihitung dari subtotal SETELAH potongan per-baris di atas, ambil SATU
 *   promo dengan potongan terbesar yang syarat minimal belanjanya terpenuhi.
 * - Ketiganya BISA jalan bersamaan (satu promo kategori + satu promo min-belanja), tapi tidak
 *   ada dua promo dari tipe yang sama yang saling menumpuk.
 */
object PromoEngine {

    fun apply(cart: Cart, promos: List<PromoEntity>): Cart {
        val now = System.currentTimeMillis()
        val active = promos.filter { promo ->
            promo.isActive &&
                (promo.startAt == null || promo.startAt <= now) &&
                (promo.endAt == null || promo.endAt >= now)
        }
        if (active.isEmpty()) {
            // Tidak ada promo aktif sama sekali — pastikan sisa potongan promo lama (mis. promo
            // baru saja dinonaktifkan admin di tengah transaksi) ikut hilang dari keranjang.
            if (cart.promoDiscount == 0.0 && cart.lines.all { it.promoDiscount == 0.0 }) return cart
            return cart.copy(
                promoDiscount = 0.0,
                appliedPromoNames = emptyList(),
                lines = cart.lines.map { it.copy(promoDiscount = 0.0) }
            )
        }

        val categoryPromos = active.filter { it.type == PromoType.PERCENT_CATEGORY }
        val buyXGetYPromos = active.filter { it.type == PromoType.BUY_X_GET_Y_FREE }
        val minPurchasePromos = active.filter { it.type == PromoType.PERCENT_MIN_PURCHASE }

        val appliedNames = mutableListOf<String>()

        val linesWithItemPromo = cart.lines.map { line ->
            val unitPrice = line.unitPrice
            val grossLine = (unitPrice * line.quantity) - line.discount

            // BUY_X_GET_Y_FREE: dicek per produk (bukan varian), karena stoknya bisa beda
            // varian tapi promonya biasanya per-SKU induk.
            val buyXPromo = buyXGetYPromos
                .filter { it.productId == line.product.id && it.buyQty > 0 }
                .maxByOrNull { promo ->
                    val bundle = promo.buyQty + promo.getFreeQty
                    val freeUnits = (line.quantity / bundle) * promo.getFreeQty
                    freeUnits * unitPrice
                }
            val buyXDiscount = buyXPromo?.let { promo ->
                val bundle = promo.buyQty + promo.getFreeQty
                val freeUnits = (line.quantity / bundle) * promo.getFreeQty
                freeUnits * unitPrice
            } ?: 0.0

            val categoryPromo = if (line.product.categoryId != null) {
                categoryPromos
                    .filter { it.categoryId == line.product.categoryId }
                    .maxByOrNull { it.percent }
            } else null
            val categoryDiscount = categoryPromo?.let { (grossLine * it.percent / 100.0).coerceAtLeast(0.0) } ?: 0.0

            // Tidak ditumpuk sesama baris — ambil yang potongannya paling besar untuk baris ini.
            val (chosenPromo, itemDiscount) = if (buyXDiscount >= categoryDiscount) {
                buyXPromo to buyXDiscount
            } else {
                categoryPromo to categoryDiscount
            }
            val cappedDiscount = itemDiscount.coerceIn(0.0, grossLine.coerceAtLeast(0.0))
            if (chosenPromo != null && cappedDiscount > 0.0 && chosenPromo.name !in appliedNames) {
                appliedNames.add(chosenPromo.name)
            }
            line.copy(promoDiscount = cappedDiscount)
        }

        val subtotalAfterItemPromo = linesWithItemPromo.sumOf { it.lineTotal }
        val bestMinPurchasePromo = minPurchasePromos
            .filter { subtotalAfterItemPromo >= it.minPurchase }
            .maxByOrNull { subtotalAfterItemPromo * (it.percent / 100.0) }
        val transactionPromoDiscount = bestMinPurchasePromo
            ?.let { (subtotalAfterItemPromo * it.percent / 100.0).coerceIn(0.0, subtotalAfterItemPromo.coerceAtLeast(0.0)) }
            ?: 0.0
        if (bestMinPurchasePromo != null && transactionPromoDiscount > 0.0) {
            appliedNames.add(0, bestMinPurchasePromo.name)
        }

        return cart.copy(
            lines = linesWithItemPromo,
            promoDiscount = transactionPromoDiscount,
            appliedPromoNames = appliedNames
        )
    }
}
