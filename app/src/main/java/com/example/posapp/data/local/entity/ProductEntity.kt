package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    // supplierId SENGAJA tidak dideklarasikan sebagai Room ForeignKey (lihat MIGRATION_12_13):
    // menambah kolom via ALTER TABLE tidak bisa sekaligus menambah FK constraint tanpa membangun
    // ulang seluruh tabel products (berisiko untuk instalasi yang sudah punya data produksi).
    // Konsistensinya cukup dijaga di level repository (SupplierRepository tidak menghapus baris
    // pemasok, hanya menonaktifkan lewat isActive — sama seperti pola CustomerEntity).
    indices = [Index("sku", unique = true), Index("categoryId"), Index("supplierId")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String,               // barcode / SKU, harus unik
    val categoryId: Long? = null,
    /** Pemasok utama produk ini (v13) — opsional, dipakai untuk mengelompokkan draf Pesanan
     * Pembelian per pemasok dari daftar stok tipis. Null = belum diatur / tidak relevan. */
    val supplierId: Long? = null,
    val purchasePrice: Double,      // harga beli
    val sellPrice: Double,          // harga jual
    val stock: Int,
    val unit: String = "pcs",       // satuan produk (pcs, kg, liter, dus, dll) — lihat ProductUnits
    val lowStockThreshold: Int = 5, // ambang batas alert stok tipis
    val photoPath: String? = null,  // path foto lokal di internal storage
    val variantName: String? = null,   // label bebas (dipakai jika produk TIDAK punya matrix varian)
    val hasVariants: Boolean = false,  // true = kelola stok lewat ProductVariantEntity (matrix Ukuran x Warna)
    val discountPercent: Double = 0.0, // diskon produk permanen (%)
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
