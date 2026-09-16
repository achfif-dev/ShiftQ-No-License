package com.example.posapp.domain

import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.domain.auth.Permission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit menyeluruh (2026-09-08) menemukan ReportViewModel.voidTransaction() dan
 * saveTransactionCorrection() HANYA digerbang di UI (tombol disembunyikan untuk non-Admin),
 * tanpa verifikasi ulang di ViewModel — padahal rute "reports" tempat keduanya dipanggil TIDAK
 * digerbang admin-only (Kasir memang boleh membuka Riwayat Penjualan untuk retur). Tes ini
 * mengunci perilaku [Permission.canVoidTransaction] & [Permission.canCorrectTransaction] supaya
 * celah yang sama tidak diam-diam kembali di masa depan.
 */
class PermissionTest {

    private fun user(role: UserRole) = UserEntity(name = "Test", pinHash = "x", role = role)

    @Test
    fun `void and correct transaction are admin-only when PIN login enabled`() {
        assertTrue(Permission.canVoidTransaction(user(UserRole.ADMIN), pinLoginEnabled = true))
        assertFalse(Permission.canVoidTransaction(user(UserRole.MANAGER), pinLoginEnabled = true))
        assertFalse(Permission.canVoidTransaction(user(UserRole.KASIR), pinLoginEnabled = true))
        assertFalse(Permission.canVoidTransaction(null, pinLoginEnabled = true))

        assertTrue(Permission.canCorrectTransaction(user(UserRole.ADMIN), pinLoginEnabled = true))
        assertFalse(Permission.canCorrectTransaction(user(UserRole.MANAGER), pinLoginEnabled = true))
        assertFalse(Permission.canCorrectTransaction(user(UserRole.KASIR), pinLoginEnabled = true))
    }

    @Test
    fun `everything allowed when PIN login is disabled (single-user mode)`() {
        assertTrue(Permission.canVoidTransaction(null, pinLoginEnabled = false))
        assertTrue(Permission.canCorrectTransaction(null, pinLoginEnabled = false))
        assertTrue(Permission.canManageProducts(null, pinLoginEnabled = false))
        assertTrue(Permission.canPerformStockOpname(null, pinLoginEnabled = false))
    }

    @Test
    fun `manager can manage products and expenses but not opname, settings, void or correction`() {
        val manager = user(UserRole.MANAGER)
        assertTrue(Permission.canManageProducts(manager, pinLoginEnabled = true))
        assertTrue(Permission.canAccessExpenses(manager, pinLoginEnabled = true))
        assertFalse(Permission.canPerformStockOpname(manager, pinLoginEnabled = true))
        assertFalse(Permission.canAccessSettings(manager, pinLoginEnabled = true))
        assertFalse(Permission.canVoidTransaction(manager, pinLoginEnabled = true))
        assertFalse(Permission.canCorrectTransaction(manager, pinLoginEnabled = true))
    }

    @Test
    fun `kasir can process returns regardless of PIN login setting`() {
        assertTrue(Permission.canProcessReturn(user(UserRole.KASIR), pinLoginEnabled = true))
        assertTrue(Permission.canProcessReturn(null, pinLoginEnabled = false))
    }
}
