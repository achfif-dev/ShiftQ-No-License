package com.example.posapp.data.export

import android.content.Context
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export data ke file .xlsx.
 *
 * CATATAN: sebelumnya pakai Apache POI, tapi diganti ke [XlsxWriter] (native, tanpa dependency)
 * karena Apache POI tidak kompatibel dengan runtime Android dan menyebabkan app crash setiap
 * tombol export Excel ditekan. Lihat komentar di XlsxWriter.kt untuk detail akar masalahnya.
 */
@Singleton
class ExcelExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun exportProducts(products: List<ProductEntity>): File {
        val headers = listOf("Nama", "SKU", "Harga Beli", "Harga Jual", "Satuan", "Stok", "Alert Stok Tipis", "Diskon (%)", "Aktif")
        val rows = products.map { p ->
            listOf(p.name, p.sku, p.purchasePrice, p.sellPrice, p.unit, p.stock, p.lowStockThreshold, p.discountPercent, if (p.isActive) "Ya" else "Tidak")
        }
        val file = exportFile("Produk-${System.currentTimeMillis()}.xlsx")
        XlsxWriter.write(file, XlsxWriter.Sheet("Produk", headers, rows))
        return file
    }

    fun exportTransactions(transactions: List<TransactionEntity>): File {
        val headers = listOf("No. Invoice", "Tanggal", "Subtotal", "Diskon", "Pajak", "Total", "Metode Bayar", "Dibayar", "Kembalian")
        val rows = transactions.map { tx ->
            listOf(
                tx.invoiceNumber,
                dateFormat.format(Date(tx.createdAt)),
                tx.subtotal,
                tx.discountAmount,
                tx.taxAmount,
                tx.total,
                tx.paymentMethod.name,
                tx.amountPaid,
                tx.changeAmount
            )
        }
        val file = exportFile("Laporan-Transaksi-${System.currentTimeMillis()}.xlsx")
        XlsxWriter.write(file, XlsxWriter.Sheet("Transaksi", headers, rows))
        return file
    }

    /** Export CSV ringan — cocok untuk dataset besar / kompatibilitas maksimal.
     *
     * TEMUAN KEAMANAN (audit ulang): sebelumnya field teks (nama, SKU, satuan) ditulis langsung
     * di antara tanda kutip tanpa di-escape sama sekali. Dua masalah:
     * 1) Kalau nilainya sendiri mengandung tanda kutip ganda, hasil CSV rusak/bergeser kolom.
     * 2) CSV/FORMULA INJECTION: kalau nama produk/SKU DIAWALI '=', '+', '-', atau '@' (mis.
     *    "=HYPERLINK(...)"), Excel/Google Sheets akan MENGEKSEKUSINYA sebagai formula begitu
     *    file ini dibuka orang lain — bisa dipakai untuk phishing/exfiltrasi data dari siapa pun
     *    yang membuka hasil export ini. Sekarang setiap field teks di-escape lewat [csvField].
     */
    fun exportProductsCsv(products: List<ProductEntity>): File {
        val file = exportFile("Produk-${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("Nama,SKU,Harga Beli,Harga Jual,Satuan,Stok,Alert Stok Tipis,Diskon (%),Aktif\n")
            products.forEach { p ->
                writer.write(
                    "${csvField(p.name)},${csvField(p.sku)},${p.purchasePrice},${p.sellPrice},${csvField(p.unit)},${p.stock},${p.lowStockThreshold},${p.discountPercent},${if (p.isActive) "Ya" else "Tidak"}\n"
                )
            }
        }
        return file
    }

    /** Bungkus satu nilai teks jadi field CSV yang aman: escape kutip ganda dengan
     * menggandakannya (standar RFC 4180), lalu — kalau nilainya diawali karakter pemicu formula
     * ('=', '+', '-', '@', tab, atau carriage-return) — beri prefiks tanda kutip tunggal supaya
     * Excel/Sheets memperlakukannya sebagai teks literal, bukan formula. Lihat [sanitizeFormulaPrefix]. */
    private fun csvField(raw: String): String =
        "\"${sanitizeFormulaPrefix(raw).replace("\"", "\"\"")}\""

    private fun sanitizeFormulaPrefix(raw: String): String {
        val first = raw.firstOrNull() ?: return raw
        return if (first in CSV_FORMULA_TRIGGER_CHARS) "'$raw" else raw
    }

    private fun exportFile(fileName: String): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        return File(exportDir, fileName)
    }

    private companion object {
        val CSV_FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')
    }
}
