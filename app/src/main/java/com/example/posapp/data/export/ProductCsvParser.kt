package com.example.posapp.data.export

/**
 * Parser & validator CSV untuk Import Produk Massal.
 *
 * Header yang dikenali (urutan kolom BEBAS, dicocokkan lewat nama header — bukan posisi tetap
 * — supaya file hasil [ExcelExporter.exportProductsCsv] yang diedit ulang pengguna, ATAU CSV
 * yang mereka susun sendiri dari Excel, sama-sama bisa dibaca): Nama, SKU, Harga Beli, Harga
 * Jual, Satuan, Stok, Alert Stok Tipis, Diskon (%), Aktif, Kategori (opsional). Header TIDAK
 * peka huruf besar/kecil & spasi di ujung diabaikan.
 */
object ProductCsvParser {

    data class Row(
        val lineNumber: Int,
        val name: String,
        val sku: String,
        val purchasePrice: Double,
        val sellPrice: Double,
        val unit: String,
        val stock: Int,
        val lowStockThreshold: Int,
        val discountPercent: Double,
        val isActive: Boolean,
        val categoryName: String?
    )

    data class RowError(val lineNumber: Int, val reason: String)

    data class ParseResult(
        val validRows: List<Row>,
        val errors: List<RowError>,
        /** null kalau header wajib (Nama/SKU/Harga Jual) tidak ditemukan sama sekali -- di
         * kondisi ini [validRows]/[errors] per-baris tidak relevan, tampilkan pesan ini saja. */
        val headerError: String?
    )

    private val REQUIRED_HEADERS = listOf("nama", "sku", "harga jual")

    fun parse(text: String): ParseResult {
        val lines = splitCsvLines(text).filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return ParseResult(emptyList(), emptyList(), "File CSV kosong")
        }
        val headerCells = parseCsvLine(lines[0]).map { it.trim().lowercase() }
        val missingRequired = REQUIRED_HEADERS.filterNot { it in headerCells }
        if (missingRequired.isNotEmpty()) {
            return ParseResult(
                emptyList(), emptyList(),
                "Kolom wajib tidak ditemukan di baris judul: ${missingRequired.joinToString(", ")}. " +
                    "Pastikan baris pertama file berisi nama kolom (Nama, SKU, Harga Jual, dst)."
            )
        }
        fun colIndex(vararg names: String): Int = names.firstNotNullOfOrNull { n -> headerCells.indexOf(n).takeIf { it >= 0 } } ?: -1
        val idxName = colIndex("nama")
        val idxSku = colIndex("sku")
        val idxPurchase = colIndex("harga beli")
        val idxSell = colIndex("harga jual")
        val idxUnit = colIndex("satuan")
        val idxStock = colIndex("stok")
        val idxLowStock = colIndex("alert stok tipis")
        val idxDiscount = colIndex("diskon (%)", "diskon")
        val idxActive = colIndex("aktif")
        val idxCategory = colIndex("kategori")

        val validRows = mutableListOf<Row>()
        val errors = mutableListOf<RowError>()
        val skusSeenInFile = mutableSetOf<String>()

        for (i in 1 until lines.size) {
            val lineNumber = i + 1 // +1 karena baris 1 = header, manusia menghitung dari 1
            val cells = parseCsvLine(lines[i])
            fun cell(idx: Int): String = cells.getOrNull(idx)?.trim() ?: ""

            val name = cell(idxName)
            val sku = cell(idxSku)
            if (name.isBlank() || sku.isBlank()) {
                errors.add(RowError(lineNumber, "Nama dan SKU wajib diisi"))
                continue
            }
            if (!skusSeenInFile.add(sku.lowercase())) {
                errors.add(RowError(lineNumber, "SKU \"$sku\" duplikat di dalam file ini (baris sebelumnya dipakai)"))
                continue
            }
            val sellPrice = cell(idxSell).toDoubleOrNull()
            if (sellPrice == null || sellPrice < 0) {
                errors.add(RowError(lineNumber, "Harga Jual tidak valid: \"${cell(idxSell)}\""))
                continue
            }
            val purchasePrice = if (idxPurchase >= 0) cell(idxPurchase).toDoubleOrNull() ?: 0.0 else 0.0
            val stock = if (idxStock >= 0) cell(idxStock).toIntOrNull() else null
            if (idxStock >= 0 && stock == null) {
                errors.add(RowError(lineNumber, "Stok tidak valid: \"${cell(idxStock)}\""))
                continue
            }
            val lowStock = if (idxLowStock >= 0) cell(idxLowStock).toIntOrNull() ?: 5 else 5
            val discount = if (idxDiscount >= 0) cell(idxDiscount).toDoubleOrNull() ?: 0.0 else 0.0
            val activeText = cell(idxActive).lowercase()
            val isActive = if (idxActive >= 0) activeText !in setOf("tidak", "no", "false", "0") else true
            val unit = if (idxUnit >= 0) cell(idxUnit).ifBlank { "pcs" } else "pcs"
            val category = if (idxCategory >= 0) cell(idxCategory).ifBlank { null } else null

            validRows.add(
                Row(
                    lineNumber = lineNumber, name = name, sku = sku,
                    purchasePrice = purchasePrice, sellPrice = sellPrice, unit = unit,
                    stock = stock ?: 0, lowStockThreshold = lowStock, discountPercent = discount,
                    isActive = isActive, categoryName = category
                )
            )
        }
        return ParseResult(validRows, errors, headerError = null)
    }

    /** Pisah teks CSV jadi baris logis — TIDAK cukup split per karakter newline biasa, karena
     * newline BISA muncul di dalam field yang dikutip (mis. nama produk multi-baris hasil
     * copy-paste dari Excel). Baris baru di dalam tanda kutip ganda dianggap bagian field, bukan
     * pemisah baris. */
    private fun splitCsvLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (current.isNotBlank()) lines.add(current.toString())
                    current.clear()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) lines.add(current.toString())
        return lines
    }

    /** Parser satu baris CSV RFC 4180: field dipisah koma, field yang mengandung koma/kutip/
     * newline dibungkus tanda kutip ganda, kutip ganda literal di dalam field ditulis dobel (""). */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
