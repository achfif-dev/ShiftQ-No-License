package com.example.posapp.domain

import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartTest {

    private fun product(
        id: Long = 1,
        sellPrice: Double = 10_000.0,
        stock: Int = 100
    ) = ProductEntity(
        id = id,
        name = "Produk Uji",
        sku = "SKU-$id",
        purchasePrice = sellPrice * 0.6,
        sellPrice = sellPrice,
        stock = stock
    )

    @Test
    fun `empty cart has zero totals`() {
        val cart = Cart()
        assertTrue(cart.isEmpty)
        assertEquals(0.0, cart.subtotal, 0.0)
        assertEquals(0.0, cart.taxAmount, 0.0)
        assertEquals(0.0, cart.total, 0.0)
    }

    @Test
    fun `subtotal sums line totals correctly`() {
        val cart = Cart(
            lines = listOf(
                CartLine(product = product(1, sellPrice = 15_000.0), quantity = 2), // 30.000
                CartLine(product = product(2, sellPrice = 5_000.0), quantity = 3)   // 15.000
            ),
            taxPercent = 0.0
        )
        assertEquals(45_000.0, cart.subtotal, 0.01)
        assertEquals(45_000.0, cart.total, 0.01)
    }

    @Test
    fun `line discount reduces subtotal`() {
        val cart = Cart(
            lines = listOf(
                CartLine(product = product(sellPrice = 10_000.0), quantity = 1, discount = 2_000.0)
            ),
            taxPercent = 0.0
        )
        assertEquals(8_000.0, cart.subtotal, 0.01)
    }

    @Test
    fun `tax is applied after transaction discount, never negative`() {
        val cart = Cart(
            lines = listOf(CartLine(product = product(sellPrice = 10_000.0), quantity = 1)),
            transactionDiscount = 15_000.0, // discount lebih besar dari subtotal
            taxPercent = 11.0
        )
        // subtotal 10.000 - diskon 15.000 -> tidak boleh negatif -> basis pajak 0
        assertEquals(0.0, cart.taxAmount, 0.01)
        assertEquals(0.0, cart.total, 0.01)
    }

    @Test
    fun `total includes tax on top of discounted subtotal`() {
        val cart = Cart(
            lines = listOf(CartLine(product = product(sellPrice = 100_000.0), quantity = 1)),
            transactionDiscount = 10_000.0,
            taxPercent = 11.0
        )
        // (100.000 - 10.000) = 90.000 basis; pajak 11% = 9.900; total = 99.900
        assertEquals(9_900.0, cart.taxAmount, 0.01)
        assertEquals(99_900.0, cart.total, 0.01)
    }

    @Test
    fun `line key differs between plain product and its variant`() {
        val line1 = CartLine(product = product(id = 5))
        assertEquals("5:0", line1.lineKey)
    }
}
