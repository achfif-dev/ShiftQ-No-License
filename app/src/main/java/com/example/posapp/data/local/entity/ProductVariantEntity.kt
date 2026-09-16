package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Satu kombinasi varian produk (mis. "Merah / L") dengan SKU & stok terpisah.
 * Produk induk tetap punya harga jual default di [ProductEntity.sellPrice]; [priceOverride]
 * bila diisi akan menggantikan harga default khusus untuk kombinasi ini (mis. ukuran XL lebih mahal).
 */
@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("sku", unique = true)]
)
data class ProductVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val variantLabel: String,   // mis. "Merah / L"
    val sku: String,            // SKU/barcode unik khusus kombinasi ini
    val stock: Int,
    val priceOverride: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
