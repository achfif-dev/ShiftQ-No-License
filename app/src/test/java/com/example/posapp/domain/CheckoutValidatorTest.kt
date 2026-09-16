package com.example.posapp.domain

import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import com.example.posapp.domain.usecase.CheckoutValidator
import com.example.posapp.domain.usecase.PaymentSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutValidatorTest {

    private fun product(sellPrice: Double = 10_000.0, stock: Int = 5) = ProductEntity(
        id = 1,
        name = "Produk Uji",
        sku = "SKU-1",
        purchasePrice = sellPrice * 0.6,
        sellPrice = sellPrice,
        stock = stock
    )

    @Test
    fun `rejects empty cart`() {
        val result = CheckoutValidator.validate(Cart(), listOf(PaymentSplit(PaymentMethod.CASH, 10_000.0)))
        assertTrue(result is CheckoutValidator.ValidationResult.Invalid)
    }

    @Test
    fun `rejects when no payment provided`() {
        val cart = Cart(lines = listOf(CartLine(product = product())), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, emptyList())
        assertTrue(result is CheckoutValidator.ValidationResult.Invalid)
    }

    @Test
    fun `rejects when payment amount is zero or negative`() {
        val cart = Cart(lines = listOf(CartLine(product = product())), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, listOf(PaymentSplit(PaymentMethod.CASH, 0.0)))
        assertTrue(result is CheckoutValidator.ValidationResult.Invalid)
    }

    @Test
    fun `rejects insufficient payment`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 10_000.0))), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, listOf(PaymentSplit(PaymentMethod.CASH, 5_000.0)))
        assertTrue(result is CheckoutValidator.ValidationResult.Invalid)
    }

    @Test
    fun `accepts exact payment`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 10_000.0))), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, listOf(PaymentSplit(PaymentMethod.CASH, 10_000.0)))
        assertEquals(CheckoutValidator.ValidationResult.Valid, result)
    }

    @Test
    fun `accepts split payment that together covers total`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 20_000.0))), taxPercent = 0.0)
        val payments = listOf(
            PaymentSplit(PaymentMethod.CASH, 12_000.0),
            PaymentSplit(PaymentMethod.QRIS, 8_000.0)
        )
        assertEquals(CheckoutValidator.ValidationResult.Valid, CheckoutValidator.validate(cart, payments))
    }

    @Test
    fun `rejects when quantity exceeds available stock`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 10_000.0, stock = 2), quantity = 5)), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, listOf(PaymentSplit(PaymentMethod.CASH, 50_000.0)))
        assertTrue(result is CheckoutValidator.ValidationResult.Invalid)
        assertTrue((result as CheckoutValidator.ValidationResult.Invalid).message.contains("Stok"))
    }

    @Test
    fun `totalPaid ignores non-positive payment lines`() {
        val payments = listOf(
            PaymentSplit(PaymentMethod.CASH, 5_000.0),
            PaymentSplit(PaymentMethod.QRIS, 0.0),
            PaymentSplit(PaymentMethod.DEBIT_CREDIT, -100.0)
        )
        assertEquals(5_000.0, CheckoutValidator.totalPaid(payments), 0.01)
    }

    @Test
    fun `rejects BON payment without a customer selected`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 10_000.0))), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, listOf(PaymentSplit(PaymentMethod.BON, 10_000.0)), customerId = null)
        assertTrue(result is CheckoutValidator.ValidationResult.Invalid)
        assertTrue((result as CheckoutValidator.ValidationResult.Invalid).message.contains("pelanggan"))
    }

    @Test
    fun `accepts BON payment when a customer is selected`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 10_000.0))), taxPercent = 0.0)
        val result = CheckoutValidator.validate(cart, listOf(PaymentSplit(PaymentMethod.BON, 10_000.0)), customerId = 7L)
        assertEquals(CheckoutValidator.ValidationResult.Valid, result)
    }

    @Test
    fun `accepts partial BON mixed with cash when customer is selected`() {
        val cart = Cart(lines = listOf(CartLine(product = product(sellPrice = 20_000.0))), taxPercent = 0.0)
        val payments = listOf(
            PaymentSplit(PaymentMethod.CASH, 10_000.0),
            PaymentSplit(PaymentMethod.BON, 10_000.0)
        )
        assertEquals(CheckoutValidator.ValidationResult.Valid, CheckoutValidator.validate(cart, payments, customerId = 7L))
    }
}
