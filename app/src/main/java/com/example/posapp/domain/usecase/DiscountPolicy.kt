package com.example.posapp.domain.usecase

import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole

/**
 * Kebijakan batas diskon manual (per-baris & per-transaksi) di kasir POS.
 *
 * TEMUAN AUDIT (ditambahkan setelah audit keamanan menyeluruh, 2026-09-14): sebelumnya
 * PosViewModel.updateLineDiscount/updateTransactionDiscount menerima nilai diskon APA PUN tanpa
 * batas atau cek peran sama sekali -- Kasir biasa bisa memberi diskon hingga 100% pada satu
 * baris/transaksi (membuat barang "gratis" secara sah di sistem), dan diskon manual sama sekali
 * tidak tercatat di AuditLogRepository (beda dari Void/Koreksi/Retur yang semuanya tercatat).
 * Ini celah fraud klasik POS ("sweethearting"): kasir bisa beri diskon besar ke pembeli
 * "kenalan"/diri sendiri, tetap menagih penuh secara tunai, lalu mengantongi selisihnya --
 * baru mungkin ketahuan TIDAK LANGSUNG lewat selisih kas saat tutup shift.
 *
 * Kebijakan sekarang:
 * - ADMIN & MANAGER: tidak dibatasi kebijakan ini sama sekali (mereka sudah level pengawasan,
 *   konsisten dengan Permission.canManageProducts/canManagePromos yang juga membolehkan
 *   ADMIN & MANAGER untuk hal-hal yang memengaruhi margin toko).
 * - KASIR: dibatasi ke [StoreProfile.maxKasirDiscountPercent] (dikonfigurasi Admin di
 *   Pengaturan > Profil Toko, default 20%) dari nilai baris/subtotal SEBELUM diskon.
 *   Kalau Kasir mencoba memberi diskon melebihi batas ini, nilainya OTOMATIS DIPANGKAS
 *   (clamp) ke batas maksimum -- bukan ditolak/dibatalkan total, supaya kasir tetap bisa
 *   lanjut melayani pembeli dengan diskon yang wajar, tapi tidak mungkin melebihi batas
 *   tanpa Admin/Manager login sendiri di device itu untuk memberikannya (sesuai model
 *   otorisasi berbasis sesi-login yang sudah dipakai konsisten di seluruh app ini -- lihat
 *   Permission.kt -- bukan pola "PIN approval sekali pakai" yang tidak ada presedennya
 *   di app ini).
 * - Mode single-user (pinLoginEnabled = false): tidak dibatasi sama sekali (tidak ada konsep
 *   "kasir" vs "admin" terpisah kalau fitur PIN login memang tidak dipakai toko ini).
 *
 * Diskon yang BENAR-BENAR diterapkan (setelah clamp) dicatat ke AuditLogRepository saat
 * checkout berhasil -- lihat CheckoutUseCase.
 */
object DiscountPolicy {

    /** true kalau [user] TIDAK dibatasi kebijakan ini (Admin/Manager, atau mode single-user). */
    fun isUnrestricted(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN || user?.role == UserRole.MANAGER
    }

    data class ClampResult(val amount: Double, val wasClamped: Boolean, val maxAllowed: Double)

    /**
     * Pangkas [requestedDiscount] (nominal Rupiah) supaya tidak melebihi
     * [maxKasirDiscountPercent]% dari [baseAmount] (harga baris atau subtotal keranjang
     * SEBELUM diskon), KECUALI [user] tidak dibatasi kebijakan ini (lihat [isUnrestricted]).
     *
     * Toleransi pembulatan 0.5 dipakai konsisten dengan AMOUNT_EPSILON di CheckoutValidator,
     * supaya nilai yang sudah pas di batas (mis. hasil kali persen desimal) tidak keliru
     * dianggap "melebihi" hanya karena selisih floating-point sepersekian rupiah.
     */
    fun clamp(
        requestedDiscount: Double,
        baseAmount: Double,
        user: UserEntity?,
        pinLoginEnabled: Boolean,
        maxKasirDiscountPercent: Int
    ): ClampResult {
        val safeRequested = requestedDiscount.coerceAtLeast(0.0)
        val maxAllowed = (baseAmount.coerceAtLeast(0.0)) * (maxKasirDiscountPercent.coerceIn(0, 100) / 100.0)
        if (isUnrestricted(user, pinLoginEnabled)) {
            return ClampResult(safeRequested, wasClamped = false, maxAllowed = maxAllowed)
        }
        return if (safeRequested > maxAllowed + ROUNDING_EPSILON) {
            ClampResult(maxAllowed, wasClamped = true, maxAllowed = maxAllowed)
        } else {
            ClampResult(safeRequested, wasClamped = false, maxAllowed = maxAllowed)
        }
    }

    private const val ROUNDING_EPSILON = 0.5
}
