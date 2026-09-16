package com.example.posapp.domain

import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.domain.usecase.DiscountPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountPolicyTest {

    private fun user(role: UserRole) = UserEntity(name = "Test", pinHash = "x", role = role)

    @Test
    fun `kasir discount within limit is not clamped`() {
        val result = DiscountPolicy.clamp(
            requestedDiscount = 10_000.0,
            baseAmount = 100_000.0,
            user = user(UserRole.KASIR),
            pinLoginEnabled = true,
            maxKasirDiscountPercent = 20
        )
        assertEquals(10_000.0, result.amount, 0.01)
        assertFalse(result.wasClamped)
    }

    @Test
    fun `kasir discount above limit is clamped to max allowed`() {
        val result = DiscountPolicy.clamp(
            requestedDiscount = 90_000.0,
            baseAmount = 100_000.0,
            user = user(UserRole.KASIR),
            pinLoginEnabled = true,
            maxKasirDiscountPercent = 20
        )
        assertTrue(result.wasClamped)
        assertEquals(20_000.0, result.amount, 0.01)
    }

    @Test
    fun `admin discount is never clamped even at 100 percent`() {
        val result = DiscountPolicy.clamp(
            requestedDiscount = 100_000.0,
            baseAmount = 100_000.0,
            user = user(UserRole.ADMIN),
            pinLoginEnabled = true,
            maxKasirDiscountPercent = 20
        )
        assertFalse(result.wasClamped)
        assertEquals(100_000.0, result.amount, 0.01)
    }

    @Test
    fun `manager discount is never clamped`() {
        val result = DiscountPolicy.clamp(
            requestedDiscount = 100_000.0,
            baseAmount = 100_000.0,
            user = user(UserRole.MANAGER),
            pinLoginEnabled = true,
            maxKasirDiscountPercent = 20
        )
        assertFalse(result.wasClamped)
    }

    @Test
    fun `single-user mode is never clamped regardless of role`() {
        val result = DiscountPolicy.clamp(
            requestedDiscount = 100_000.0,
            baseAmount = 100_000.0,
            user = null,
            pinLoginEnabled = false,
            maxKasirDiscountPercent = 20
        )
        assertFalse(result.wasClamped)
    }

    @Test
    fun `negative requested discount is coerced to zero`() {
        val result = DiscountPolicy.clamp(
            requestedDiscount = -5_000.0,
            baseAmount = 100_000.0,
            user = user(UserRole.KASIR),
            pinLoginEnabled = true,
            maxKasirDiscountPercent = 20
        )
        assertEquals(0.0, result.amount, 0.01)
        assertFalse(result.wasClamped)
    }

    @Test
    fun `value exactly at limit is not clamped despite floating point rounding`() {
        // 33.33% dari 3 -> potensi selisih floating point sepersekian rupiah di batas 20%
        val result = DiscountPolicy.clamp(
            requestedDiscount = 20_000.00000001,
            baseAmount = 100_000.0,
            user = user(UserRole.KASIR),
            pinLoginEnabled = true,
            maxKasirDiscountPercent = 20
        )
        assertFalse(result.wasClamped)
    }
}
