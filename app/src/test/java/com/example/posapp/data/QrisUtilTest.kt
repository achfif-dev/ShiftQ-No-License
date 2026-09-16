package com.example.posapp.data

import com.example.posapp.data.qris.QrisUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrisUtilTest {

    // Payload EMVCo sintetis minimal (bukan dari merchant nyata), valid secara struktur TLV:
    // tag00=payload indicator, tag01=static init, tag53=currency IDR, tag58=country ID,
    // tag63=CRC dummy (isinya diabaikan & dihitung ulang oleh injectAmount).
    private val staticSample = "00020101021153033605802ID6304ABCD"

    /** Independen dari implementasi QrisUtil — dipakai untuk verifikasi silang hasil CRC. */
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

    @Test
    fun `injects amount and switches to dynamic indicator`() {
        val result = QrisUtil.injectAmount(staticSample, 25_000)
        requireNotNull(result)
        assertTrue("harus mengandung tag 54 dengan nominal", result.contains("540525000"))
        assertTrue("indikator inisiasi harus jadi dinamis (12)", result.contains("010212"))
    }

    @Test
    fun `recomputed CRC matches independent implementation`() {
        val result = QrisUtil.injectAmount(staticSample, 10_000)
        requireNotNull(result)
        val body = result.dropLast(4) // seluruh payload sebelum 4 digit CRC akhir
        val expectedCrc = crc16(body)
        assertEquals(expectedCrc, result.takeLast(4))
    }

    @Test
    fun `replaces existing amount field instead of duplicating it`() {
        val withFirstAmount = QrisUtil.injectAmount(staticSample, 10_000)
        requireNotNull(withFirstAmount)
        val withSecondAmount = QrisUtil.injectAmount(withFirstAmount, 50_000)
        requireNotNull(withSecondAmount)
        assertTrue(withSecondAmount.contains("540550000"))
        // Field 54 lama (10000, panjang 5) tidak boleh masih tersisa di payload
        assertTrue(!withSecondAmount.contains("540510000"))
    }

    @Test
    fun `rejects invalid or empty payload`() {
        assertNull(QrisUtil.injectAmount("", 10_000))
        assertNull(QrisUtil.injectAmount("bukan-qris", 10_000))
    }

    @Test
    fun `rejects non-positive amount`() {
        assertNull(QrisUtil.injectAmount(staticSample, 0))
        assertNull(QrisUtil.injectAmount(staticSample, -5_000))
    }
}
