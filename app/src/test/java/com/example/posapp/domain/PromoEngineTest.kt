package com.example.posapp.domain

import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.PromoEntity
import com.example.posapp.data.local.entity.PromoType
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import com.example.posapp.domain.usecase.PromoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromoEngine memutuskan potongan harga otomatis di kasir — salah satu zona paling rawan di
 * aplikasi ini (menyentuh uang, jalan otomatis tanpa konfirmasi kasir) dan sebelumnya SAMA
 * SEKALI tidak punya test. Yang diuji di sini bukan cuma "angkanya benar", tapi aturan yang
 * mudah rusak tanpa sadar saat kode diubah: promo sesama tipe TIDAK menumpuk, promo per-baris
 * tidak pernah melebihi nilai barisnya, dan promo min-belanja dihitung SETELAH potongan
 * per-baris (bukan dari harga kotor — kalau terbalik, toko memberi diskon lebih besar dari
 * yang dimaksud pemiliknya).
 */
class PromoEngineTest {

    private fun product(id: Long, price: Double, categoryId: Long? = null) = ProductEntity(
        id = id,
        name = "Produk $id",
        sku = "SKU$id",
        categoryId = categoryId,
        purchasePrice = price / 2,
        sellPrice = price,
        stock = 100
    )

    private fun cartOf(vararg lines: CartLine) = Cart(lines = lines.toList(), taxPercent = 0.0)

    @Test
    fun `tanpa promo aktif potongan lama ikut dibersihkan`() {
        val line = CartLine(product = product(1, 10_000.0), quantity = 2, promoDiscount = 5_000.0)
        val result = PromoEngine.apply(cartOf(line), emptyList())

        assertEquals(0.0, result.lines.first().promoDiscount, 0.001)
        assertEquals(0.0, result.promoDiscount, 0.001)
        assertTrue(result.appliedPromoNames.isEmpty())
    }

    @Test
    fun `promo nonaktif dan promo kedaluwarsa tidak diterapkan`() {
        val line = CartLine(product = product(1, 10_000.0, categoryId = 7), quantity = 1)
        val kedaluwarsa = PromoEntity(
            name = "Sudah lewat", type = PromoType.PERCENT_CATEGORY, percent = 50.0,
            categoryId = 7, endAt = System.currentTimeMillis() - 60_000
        )
        val dimatikan = PromoEntity(
            name = "Dimatikan", type = PromoType.PERCENT_CATEGORY, percent = 50.0,
            categoryId = 7, isActive = false
        )

        val result = PromoEngine.apply(cartOf(line), listOf(kedaluwarsa, dimatikan))

        assertEquals(0.0, result.lines.first().promoDiscount, 0.001)
    }

    @Test
    fun `promo kategori memotong sesuai persen dari nilai baris`() {
        val line = CartLine(product = product(1, 10_000.0, categoryId = 3), quantity = 4)
        val promo = PromoEntity(
            name = "Diskon Snack 10%", type = PromoType.PERCENT_CATEGORY, percent = 10.0, categoryId = 3
        )

        val result = PromoEngine.apply(cartOf(line), listOf(promo))

        assertEquals(4_000.0, result.lines.first().promoDiscount, 0.001)
        assertEquals(36_000.0, result.subtotal, 0.001)
        assertTrue(result.appliedPromoNames.contains("Diskon Snack 10%"))
    }

    @Test
    fun `dua promo kategori tidak menumpuk - hanya yang terbesar dipakai`() {
        val line = CartLine(product = product(1, 10_000.0, categoryId = 3), quantity = 1)
        val kecil = PromoEntity(name = "A", type = PromoType.PERCENT_CATEGORY, percent = 10.0, categoryId = 3)
        val besar = PromoEntity(name = "B", type = PromoType.PERCENT_CATEGORY, percent = 25.0, categoryId = 3)

        val result = PromoEngine.apply(cartOf(line), listOf(kecil, besar))

        assertEquals(2_500.0, result.lines.first().promoDiscount, 0.001)
        assertEquals(listOf("B"), result.appliedPromoNames)
    }

    @Test
    fun `beli 2 gratis 1 memotong satu unit setiap tiga unit`() {
        val line = CartLine(product = product(9, 12_000.0), quantity = 7)
        val promo = PromoEntity(
            name = "Beli 2 Gratis 1", type = PromoType.BUY_X_GET_Y_FREE,
            productId = 9, buyQty = 2, getFreeQty = 1
        )

        val result = PromoEngine.apply(cartOf(line), listOf(promo))

        // 7 unit = 2 bundel penuh (6 unit) -> 2 unit gratis; sisa 1 unit tidak dapat apa-apa.
        assertEquals(24_000.0, result.lines.first().promoDiscount, 0.001)
    }

    @Test
    fun `potongan per-baris tidak pernah melebihi nilai baris`() {
        val line = CartLine(product = product(1, 10_000.0, categoryId = 5), quantity = 1, discount = 9_000.0)
        val promo = PromoEntity(name = "Gila 90%", type = PromoType.PERCENT_CATEGORY, percent = 90.0, categoryId = 5)

        val result = PromoEngine.apply(cartOf(line), listOf(promo))

        assertTrue(result.lines.first().promoDiscount <= 1_000.0)
        assertTrue(result.subtotal >= 0.0)
    }

    @Test
    fun `promo min belanja dihitung dari subtotal SETELAH potongan per-baris`() {
        val line = CartLine(product = product(1, 100_000.0, categoryId = 2), quantity = 1)
        val kategori = PromoEntity(
            name = "Kategori 20%", type = PromoType.PERCENT_CATEGORY, percent = 20.0, categoryId = 2
        )
        val minBelanja = PromoEntity(
            name = "Belanja min 50rb diskon 10%", type = PromoType.PERCENT_MIN_PURCHASE,
            percent = 10.0, minPurchase = 50_000.0
        )

        val result = PromoEngine.apply(cartOf(line), listOf(kategori, minBelanja))

        // Kategori: 20% x 100rb = 20rb -> subtotal 80rb. Min-belanja: 10% x 80rb = 8rb
        // (BUKAN 10% x 100rb = 10rb, yang berarti toko memberi 2rb lebih banyak dari maksudnya).
        assertEquals(20_000.0, result.lines.first().promoDiscount, 0.001)
        assertEquals(8_000.0, result.promoDiscount, 0.001)
        assertEquals(72_000.0, result.total, 0.001)
    }

    @Test
    fun `promo min belanja tidak jalan kalau syarat belum terpenuhi`() {
        val line = CartLine(product = product(1, 20_000.0), quantity = 1)
        val promo = PromoEntity(
            name = "Min 50rb", type = PromoType.PERCENT_MIN_PURCHASE, percent = 10.0, minPurchase = 50_000.0
        )

        val result = PromoEngine.apply(cartOf(line), listOf(promo))

        assertEquals(0.0, result.promoDiscount, 0.001)
    }
}
