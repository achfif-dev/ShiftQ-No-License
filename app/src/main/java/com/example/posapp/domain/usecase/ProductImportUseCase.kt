package com.example.posapp.domain.usecase

import androidx.room.withTransaction
import com.example.posapp.data.export.ProductCsvParser
import com.example.posapp.data.local.AppDatabase
import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.AuditLogRepository
import javax.inject.Inject

data class ProductImportSummary(
    val added: Int,
    val updated: Int,
    val rowErrors: List<ProductCsvParser.RowError>
)

/**
 * Eksekusi Import Produk Massal (v16) — dipanggil SETELAH pengguna melihat pratinjau hasil
 * [ProductCsvParser.parse] dan menekan konfirmasi (lihat ProductImportScreen), bukan langsung
 * begitu file dipilih, supaya kesalahan format/isi ketahuan dulu sebelum benar-benar menulis
 * ratusan baris ke database.
 *
 * Pencocokan produk: berdasarkan SKU (unik, sama seperti pemindaian barcode di kasir).
 * - SKU SUDAH ada -> UPDATE nama/harga/satuan/kategori/diskon/status aktif.
 *   STOK SENGAJA TIDAK IKUT DIUBAH di jalur ini -- kalau file CSV berisi angka stok lama/basi
 *   (mis. diekspor beberapa hari lalu lalu diedit harganya saja), meng-overwrite stok bisa
 *   menghapus riwayat penjualan yang sudah terjadi sejak file itu diekspor. Perubahan stok
 *   massal yang disengaja tetap harus lewat Stok Opname (tercatat & diaudit khusus di sana).
 * - SKU BELUM ada -> INSERT produk baru, stok dari file dipakai sebagai stok awal (wajar,
 *   tidak ada riwayat sebelumnya yang bisa "terhapus").
 *
 * Kategori dicocokkan/DIBUAT OTOMATIS dari teks bebas kolom Kategori (case-insensitive) --
 * konsisten dengan cara CategoryManagerDialog di layar Produk bekerja (kategori memang bukan
 * daftar tertutup di app ini).
 *
 * Seluruh baris diproses dalam SATU transaksi database ([AppDatabase.withTransaction]) supaya
 * kalau ada kegagalan tak terduga di tengah jalan (bukan validasi baris -- itu sudah difilter
 * duluan oleh parser -- tapi mis. error I/O), tidak ada import "setengah jalan" yang tersimpan.
 */
class ProductImportUseCase @Inject constructor(
    private val appDatabase: AppDatabase,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val auditLogRepository: AuditLogRepository
) {
    suspend fun import(
        rows: List<ProductCsvParser.Row>,
        parseErrors: List<ProductCsvParser.RowError>,
        actorName: String?,
        actorRole: UserRole?
    ): ProductImportSummary {
        var added = 0
        var updated = 0
        val rowErrors = mutableListOf<ProductCsvParser.RowError>()
        rowErrors.addAll(parseErrors)
        val categoryIdByName = mutableMapOf<String, Long>()

        appDatabase.withTransaction {
            for (row in rows) {
                try {
                    val categoryId = row.categoryName?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                        categoryIdByName.getOrPut(name.lowercase()) {
                            categoryDao.findByName(name)?.id ?: categoryDao.insert(CategoryEntity(name = name))
                        }
                    }
                    val existing = productDao.findBySku(row.sku)
                    if (existing != null) {
                        productDao.update(
                            existing.copy(
                                name = row.name,
                                categoryId = categoryId ?: existing.categoryId,
                                purchasePrice = row.purchasePrice,
                                sellPrice = row.sellPrice,
                                unit = row.unit,
                                lowStockThreshold = row.lowStockThreshold,
                                discountPercent = row.discountPercent,
                                isActive = row.isActive,
                                updatedAt = System.currentTimeMillis()
                                // stock SENGAJA tidak disertakan -- lihat dokumentasi kelas di atas.
                            )
                        )
                        updated++
                    } else {
                        productDao.insert(
                            ProductEntity(
                                name = row.name,
                                sku = row.sku,
                                categoryId = categoryId,
                                purchasePrice = row.purchasePrice,
                                sellPrice = row.sellPrice,
                                stock = row.stock,
                                unit = row.unit,
                                lowStockThreshold = row.lowStockThreshold,
                                discountPercent = row.discountPercent,
                                isActive = row.isActive
                            )
                        )
                        added++
                    }
                } catch (e: Exception) {
                    rowErrors.add(ProductCsvParser.RowError(row.lineNumber, e.message ?: "Gagal disimpan ke database"))
                }
            }
        }

        if (added > 0 || updated > 0) {
            auditLogRepository.log(
                actorName = actorName ?: "Admin",
                actorRole = actorRole,
                action = "IMPORT_PRODUK",
                description = "Import CSV: $added produk baru, $updated produk diperbarui" +
                    if (rowErrors.isNotEmpty()) ", ${rowErrors.size} baris dilewati" else ""
            )
        }
        return ProductImportSummary(added, updated, rowErrors)
    }
}
