package com.example.posapp.data.qris

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Implementasi ringan format EMVCo QR Code (dasar standar QRIS Indonesia) untuk mengubah QRIS
 * statis (gambar yang diunggah pemilik toko, tanpa nominal) menjadi QRIS dinamis yang sudah
 * berisi nominal transaksi — seluruhnya dihitung di perangkat, tanpa perlu koneksi internet
 * atau API pihak ketiga.
 */
object QrisUtil {

    private data class TlvField(val tag: String, val value: String)

    private fun parseTlv(raw: String): List<TlvField> {
        val fields = mutableListOf<TlvField>()
        var i = 0
        while (i + 4 <= raw.length) {
            val tag = raw.substring(i, i + 2)
            val len = raw.substring(i + 2, i + 4).toIntOrNull() ?: break
            val valueStart = i + 4
            val valueEnd = valueStart + len
            if (valueEnd > raw.length) break
            fields.add(TlvField(tag, raw.substring(valueStart, valueEnd)))
            i = valueEnd
        }
        return fields
    }

    private fun buildTlv(fields: List<TlvField>): String = fields.joinToString("") { f ->
        f.tag + f.value.length.toString().padStart(2, '0') + f.value
    }

    /** CRC16-CCITT (poly 0x1021, init 0xFFFF) sesuai spesifikasi tag 63 EMVCo QR Code. */
    private fun crc16(data: String): String {
        var crc = 0xFFFF
        for (b in data.toByteArray(Charsets.US_ASCII)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }

    /** Cek struktur payload EMVCo dasar (bukan cuma "ada barcode yang terbaca") -- lihat
     * dokumentasi kelas & catatan di [com.example.posapp.presentation.settings.StoreProfileViewModel.setQrisImagePath]
     * untuk kenapa ini penting: sebelumnya pemeriksaan hanya "ML Kit berhasil baca SATU barcode
     * apa saja" -- foto QR WiFi/kontak/tautan pun akan lolos dianggap "QRIS Dinamis aktif",
     * padahal bukan payload pembayaran sama sekali, baru ketahuan gagal saat kasir benar-benar
     * coba pakai di depan pelanggan. Di sini: parsing TLV harus selesai bersih sampai akhir
     * (bukan terpotong), field "00" (Payload Format Indicator, wajib ada di SEMUA QR EMVCo) harus
     * ada, dan CRC (tag 63) yang tertanam harus cocok dengan CRC yang dihitung ulang dari isi
     * aslinya -- ini paling kuat membuktikan strukturnya benar-benar EMVCo, bukan teks kebetulan
     * mirip. */
    fun isValidQris(rawQris: String): Boolean {
        val trimmed = rawQris.trim()
        if (trimmed.length < 8) return false
        val fields = parseTlv(trimmed)
        if (fields.isEmpty() || fields.none { it.tag == "00" }) return false
        // Parsing TLV harus menghabiskan SELURUH panjang teks tanpa sisa -- kalau parseTlv
        // berhenti di tengah (length field rusak/tidak masuk akal), total panjang field yang
        // berhasil dibaca akan lebih pendek dari `trimmed` aslinya.
        val consumedLength = fields.sumOf { 4 + it.value.length }
        if (consumedLength != trimmed.length) return false
        val crcField = fields.lastOrNull { it.tag == "63" } ?: return false
        val withoutCrcValue = trimmed.substring(0, trimmed.length - 4) // buang 4 hex CRC di ujung
        return crc16(withoutCrcValue).equals(crcField.value, ignoreCase = true)
    }

    /**
     * Sisipkan/timpa nominal transaksi (tag 54) ke dalam payload QRIS mentah, ubah indikator
     * metode inisiasi (tag 01) dari statis ("11") ke dinamis ("12"), lalu hitung ulang CRC (tag 63).
     * Mengembalikan null bila [rawQris] bukan payload EMVCo QR Code yang valid.
     */
    fun injectAmount(rawQris: String, amountRupiah: Long): String? {
        val trimmed = rawQris.trim()
        if (trimmed.length < 8 || amountRupiah <= 0) return null
        val allFields = parseTlv(trimmed)
        if (allFields.isEmpty()) return null
        val fields = allFields.filterNot { it.tag == "63" }.toMutableList()

        val idx01 = fields.indexOfFirst { it.tag == "01" }
        if (idx01 >= 0) fields[idx01] = TlvField("01", "12") else fields.add(0, TlvField("01", "12"))

        val amountStr = amountRupiah.toString()
        val idx54 = fields.indexOfFirst { it.tag == "54" }
        if (idx54 >= 0) {
            fields[idx54] = TlvField("54", amountStr)
        } else {
            val anchorTag = when {
                fields.any { it.tag == "53" } -> "53"
                fields.any { it.tag == "52" } -> "52"
                else -> "01"
            }
            val insertIdx = fields.indexOfFirst { it.tag == anchorTag } + 1
            fields.add(insertIdx, TlvField("54", amountStr))
        }

        val withoutCrc = buildTlv(fields) + "6304"
        return withoutCrc + crc16(withoutCrc)
    }

    /** Render string payload QR menjadi Bitmap hitam-putih siap tampil. */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    /** Gabungan: nominal + payload mentah -> Bitmap QR dinamis siap tampil. Null bila payload tidak valid. */
    fun generateDynamicQrisBitmap(rawQris: String, amountRupiah: Long, sizePx: Int = 512): Bitmap? =
        injectAmount(rawQris, amountRupiah)?.let { generateQrBitmap(it, sizePx) }
}
