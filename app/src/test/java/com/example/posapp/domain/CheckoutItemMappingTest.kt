package com.example.posapp.domain

import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import com.example.posapp.domain.usecase.CheckoutUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regresi untuk bug paling mahal yang pernah ada di aplikasi ini: potongan promo per-baris
 * (PromoEngine) tidak pernah ikut tersimpan ke `transaction_items`, sehingga
 * Σ(nilai item) ≠ `transactions.total`. Akibat berantainya: laba kotor kelebihan hitung,
 * struk cetak-ulang salah nominal, dan retur bisa me-refund lebih besar dari yang dibayar
 * pelanggan.
 *
 * Invarian yang dijaga di sini sederhana tapi mengikat seluruh lapisan uang:
 * **jumlah nilai bersih seluruh baris transaksi yang disimpan HARUS sama dengan subtotal
 * keranjang yang dilihat kasir di layar.**
 */
class CheckoutItemMappingTest {

    private fun product(id: Long, sell: Double, buy: Double) = ProductEntity(
        id = id, name = "Produk $id", sku = "SKU$id",
        purchasePrice = buy, sellPrice = sell, stock = 100
    )

    @Test
    fun `promo per-baris ikut tersimpan ke item transaksi`() {
        val cart = Cart(
            lines = listOf(
                CartLine(product = product(1, 10_000.0, 6_000.0), quantity = 3, promoDiscount = 10_000.0)
            ),
            taxPercent = 0.0
        )

        val items = CheckoutUseCase.buildTransactionItems(cart)

        assertEquals(10_000.0, items.single().promoDiscount, 0.001)
    }

    @Test
    fun `harga beli dibekukan sebagai snapshot saat checkout`() {
        val cart = Cart(
            lines = listOf(CartLine(product = product(1, 10_000.0, 6_500.0), quantity = 1)),
            taxPercent = 0.0
        )

        val items = CheckoutUseCase.buildTransactionItems(cart)

        assertEquals(6_500.0, items.single().purchasePriceSnapshot, 0.001)
    }

    @Test
    fun `jumlah nilai item sama dengan subtotal keranjang - dengan diskon manual dan promo`() {
        val cart = Cart(
            lines = listOf(
                CartLine(product = product(1, 25_000.0, 15_000.0), quantity = 2, discount = 5_000.0, promoDiscount = 2_500.0),
                CartLine(product = product(2, 7_000.0, 4_000.0), quantity = 5, promoDiscount = 7_000.0),
                CartLine(product = product(3, 3_000.0, 1_000.0), quantity = 1)
            ),
            taxPercent = 0.0
        )

        val items = CheckoutUseCase.buildTransactionItems(cart)

        assertEquals(cart.subtotal, items.sumOf { it.lineTotal }, 0.001)
    }

    @Test
    fun `laba kotor per item memakai snapshot dan dikurangi kedua jenis potongan`() {
        val cart = Cart(
            lines = listOf(
                CartLine(product = product(1, 10_000.0, 6_000.0), quantity = 10, discount = 4_000.0, promoDiscount = 6_000.0)
            ),
            taxPercent = 0.0
        )

        val item = CheckoutUseCase.buildTransactionItems(cart).single()
        // Rumus yang sama persis dengan TransactionDao.getSalesSummary.
        val labaKotor = (item.priceSnapshot - item.purchasePriceSnapshot) * item.quantity -
            item.itemDiscount - item.promoDiscount

        // Margin kotor 4rb x 10 = 40rb, dikurangi 4rb diskon manual & 6rb promo = 30rb.
        assertEquals(30_000.0, labaKotor, 0.001)
    }
}
