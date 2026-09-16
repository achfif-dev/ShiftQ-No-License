package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Transaksi yang "ditahan" kasir (v15) — dipakai saat pelanggan belum selesai memilih barang
 * atau masih mencari uang, supaya kasir bisa langsung melayani pelanggan lain tanpa kehilangan
 * keranjang yang sedang disusun. Kasir "Lanjutkan" nanti untuk memuat ulang ke kasir aktif.
 *
 * [itemsData] menyimpan baris keranjang dalam format ringkas manual (bukan JSON — proyek ini
 * sengaja tidak menambah dependency library JSON baru hanya untuk ini): satu baris per item,
 * dipisah `;`, tiap item `productId:variantId:quantity:discount` (variantId=0 = tidak ada
 * varian). Saat "Lanjutkan" dipanggil, setiap productId/variantId di-lookup ULANG ke database
 * produk yang sekarang (lihat ParkedSaleRepository.restore) — supaya harga/stok yang dipakai
 * selalu yang terbaru, bukan snapshot lama yang mungkin sudah berubah sejak ditahan.
 *
 * TANPA foreign key ke customers (pola sama dengan ProductEntity.supplierId) — [customerName]
 * disimpan sebagai snapshot supaya tetap terbaca di daftar tahan walau pelanggan itu nanti
 * dihapus/dinonaktifkan sebelum sempat dilanjutkan.
 */
@Entity(tableName = "parked_sales")
data class ParkedSaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemsData: String,
    val note: String? = null,
    val customerId: Long? = null,
    val customerName: String? = null,
    val tableTag: String? = null,
    val transactionDiscount: Double = 0.0,
    /** Denormalisasi supaya daftar "Transaksi Tertahan" bisa ditampilkan cepat tanpa perlu
     * lookup produk dulu — dihitung ulang presisi saat benar-benar di-"Lanjutkan". */
    val itemCount: Int = 0,
    val estimatedTotal: Double = 0.0,
    val createdByName: String,
    val createdAt: Long = System.currentTimeMillis()
)
