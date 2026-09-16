package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PromoType { PERCENT_MIN_PURCHASE, PERCENT_CATEGORY, BUY_X_GET_Y_FREE }

/**
 * Promo/diskon otomatis (v15) — diterapkan otomatis ke keranjang oleh [com.example.posapp.domain.usecase.PromoEngine]
 * begitu syaratnya terpenuhi, TANPA kasir perlu input manual. Beda dari [ProductEntity.discountPercent]
 * (diskon permanen per produk) dan diskon manual per baris di keranjang — promo di sini punya
 * syarat (minimal belanja/kategori/kelipatan beli) dan boleh dibatasi jangka waktu.
 *
 * SENGAJA TIDAK dideklarasikan foreign key ke categories/products (pola sama dengan
 * [ProductEntity.supplierId], lihat MIGRATION_12_13): kalau kategori/produk terkait dihapus,
 * promo itu cukup berhenti nge-match di PromoEngine (dicek null-safe di sana), tidak ikut
 * terhapus CASCADE — riwayat promo tetap terlihat apa adanya di layar Kelola Promo.
 */
@Entity(tableName = "promos")
data class PromoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PromoType,
    /** PERCENT_MIN_PURCHASE & PERCENT_CATEGORY: persentase diskon (0-100). Diabaikan untuk BUY_X_GET_Y_FREE. */
    val percent: Double = 0.0,
    /** PERCENT_MIN_PURCHASE: minimal subtotal keranjang (Rp) supaya promo ini berlaku. */
    val minPurchase: Double = 0.0,
    /** PERCENT_CATEGORY: kategori yang didiskon. Null = tidak relevan untuk tipe lain. */
    val categoryId: Long? = null,
    /** BUY_X_GET_Y_FREE: produk yang dipromokan. Null = tidak relevan untuk tipe lain. */
    val productId: Long? = null,
    /** BUY_X_GET_Y_FREE: beli kelipatan berapa unit produk ini di satu baris keranjang. */
    val buyQty: Int = 1,
    /** BUY_X_GET_Y_FREE: dapat berapa unit gratis per kelipatan [buyQty] yang terpenuhi. */
    val getFreeQty: Int = 1,
    /** Null = tidak dibatasi tanggal mulai (langsung berlaku). */
    val startAt: Long? = null,
    /** Null = tidak dibatasi tanggal berakhir. */
    val endAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
