package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Pemasok/supplier produk (v13) — dipakai untuk mengelompokkan produk stok tipis saat
 * membuat draf Pesanan Pembelian (lihat SupplierRepository & StockScreen). Penerimaan barang
 * tetap dicatat manual lewat fitur Penyesuaian Stok yang sudah ada — modul ini hanya membantu
 * menyusun & mengirim draf pesanannya, bukan alur terima-barang penuh. */
@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null, // dipakai untuk kirim draf PO via WhatsApp
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
