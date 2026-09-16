package com.example.posapp.data.export

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Penulis file .xlsx minimal TANPA Apache POI.
 *
 * KENAPA TIDAK PAKAI APACHE POI:
 * Apache POI (poi-ooxml) bergantung pada API XML (a.l. javax.xml.stream / StAX dan beberapa
 * fitur parser XML "secure processing") yang TIDAK tersedia lengkap di runtime Android — ini
 * masalah kompatibilitas yang sudah lama & terdokumentasi luas, bukan sekadar warning build.
 * Menambahkan `-dontwarn` di ProGuard hanya membungkam peringatan saat kompilasi; class yang
 * memang tidak ada di perangkat tetap akan membuat app crash (NoClassDefFoundError /
 * ParserConfigurationException) begitu Workbook dibuat. Ini akar penyebab tombol "Export ...
 * (Excel)" menutup aplikasi.
 *
 * Solusinya: file .xlsx sebenarnya cuma file ZIP berisi beberapa file XML sederhana (format
 * OOXML/SpreadsheetML). Untuk kebutuhan export tabel sederhana (header + baris data, tanpa
 * formula/pivot/chart), kita bisa susun sendiri dengan `java.util.zip` bawaan Android — 100%
 * kompatibel, tanpa dependency tambahan, dan ukuran APK jauh lebih kecil.
 */
object XlsxWriter {

    data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Any?>>)

    fun write(file: File, sheet: Sheet) = write(file, listOf(sheet))

    fun write(file: File, sheets: List<Sheet>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypesXml(sheets.size))
            entry(zip, "_rels/.rels", rootRelsXml())
            entry(zip, "xl/workbook.xml", workbookXml(sheets))
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            entry(zip, "xl/styles.xml", stylesXml())
            sheets.forEachIndexed { index, sheet ->
                entry(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet))
            }
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("") { i ->
            "<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$overrides
</Types>"""
    }

    private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(sheets: List<Sheet>): String {
        val sheetTags = sheets.mapIndexed { index, sheet ->
            "<sheet name=\"${escape(sheet.name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$sheetTags</sheets>
</workbook>"""
    }

    private fun workbookRelsXml(sheetCount: Int): String {
        val rels = (1..sheetCount).joinToString("") { i ->
            "<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>"
        }
        val stylesRel = "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels$stylesRel
</Relationships>"""
    }

    /** style index 0 = normal, style index 1 = header (bold). */
    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
</styleSheet>"""

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        // Baris header (style 1 = bold)
        sb.append("<row r=\"1\">")
        sheet.headers.forEachIndexed { col, h ->
            sb.append(cellXml(col, 1, h, styleIndex = 1))
        }
        sb.append("</row>")

        // Baris data
        sheet.rows.forEachIndexed { rIndex, row ->
            val rowNum = rIndex + 2
            sb.append("<row r=\"$rowNum\">")
            row.forEachIndexed { col, value ->
                sb.append(cellXml(col, rowNum, value, styleIndex = 0))
            }
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun cellXml(colIndex: Int, rowNum: Int, value: Any?, styleIndex: Int): String {
        val ref = "${columnLetter(colIndex)}$rowNum"
        return when (value) {
            null -> """<c r="$ref" s="$styleIndex"/>"""
            is Int, is Long, is Double, is Float -> {
                """<c r="$ref" s="$styleIndex"><v>$value</v></c>"""
            }
            else -> {
                // inline string: tidak butuh sharedStrings.xml terpisah, lebih simpel & aman.
                """<c r="$ref" s="$styleIndex" t="inlineStr"><is><t xml:space="preserve">${escape(sanitizeForSpreadsheet(value.toString()))}</t></is></c>"""
            }
        }
    }

    /** Cegah CSV/Formula Injection: kalau nilai sel (mis. nama produk/SKU dari input pengguna)
     * DIAWALI karakter yang bisa dibaca Excel/Google Sheets sebagai AWAL FORMULA ('=', '+', '-',
     * '@') atau karakter kontrol tab/carriage-return, beri prefiks tanda kutip tunggal supaya
     * sel itu SELALU dibuka sebagai teks literal, bukan dieksekusi sebagai formula begitu file
     * ini dibuka orang lain di Excel/Sheets. Tanpa ini, produk yang diberi nama semacam
     * "=HYPERLINK(...)" atau "=cmd|'/c calc'!A1" bisa tereksekusi otomatis saat file dibuka. */
    private fun sanitizeForSpreadsheet(text: String): String {
        val first = text.firstOrNull() ?: return text
        return if (first in FORMULA_TRIGGER_CHARS) "'$text" else text
    }

    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    private fun columnLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
