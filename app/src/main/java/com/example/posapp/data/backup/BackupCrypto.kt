package com.example.posapp.data.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi/dekripsi file backup database dengan password, memakai AES-256-GCM. Sebelumnya
 * [BackupRepository] menyalin file SQLite mentah apa adanya — kalau file itu (berisi hash PIN
 * & seluruh riwayat transaksi) tersalin ke HP lain atau HP kasir hilang, siapa pun bisa
 * membukanya langsung. Sekarang backup selalu keluar dalam bentuk terenkripsi.
 *
 * Kunci enkripsi DITURUNKAN dari password lewat PBKDF2WithHmacSHA256 (bukan dipakai langsung),
 * supaya lebih tahan brute-force offline dibanding AES key = password mentah. Password itu
 * sendiri TIDAK PERNAH disimpan oleh aplikasi di mana pun — kalau lupa, backup itu tidak bisa
 * dipulihkan lagi (didesain sengaja begitu; ini trade-off keamanan yang wajar untuk data toko).
 *
 * Format file (.posbak):
 * [4 byte MAGIC "PBK1"] [16 byte salt] [12 byte IV] [ciphertext + 16 byte GCM auth tag]
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    /** Dilempar kalau password salah ATAU file bukan/bukan lagi format .posbak yang valid
     * (rusak/dimodifikasi) — GCM tidak membedakan keduanya, dan memang sebaiknya tidak, supaya
     * tidak membocorkan info ke penyerang lewat pesan error yang berbeda-beda. */
    class WrongPasswordException : Exception("Password backup salah atau file rusak")

    /** Deteksi format lewat isi file (bukan ekstensi nama file, yang bisa saja diganti pengguna
     * saat menyalin/mem-forward file), supaya restore backup lama (mentah, sebelum fitur ini
     * ada) tetap otomatis dikenali dan tidak dipaksa minta password. */
    fun hasMagic(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC.size) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(MAGIC.size)
                input.readFully(header) && header.contentEquals(MAGIC)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(source: File, destination: File, password: String) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        FileOutputStream(destination).use { out ->
            out.write(MAGIC)
            out.write(salt)
            out.write(iv)
            CipherOutputStream(out, cipher).use { cipherOut ->
                FileInputStream(source).use { input -> input.copyTo(cipherOut) }
            }
        }
    }

    /**
     * @throws WrongPasswordException jika password salah atau file bukan .posbak yang valid.
     *
     * PERBAIKAN PENTING (audit): versi sebelumnya memakai [javax.crypto.CipherInputStream].
     * Pada mode GCM, perilaku CipherInputStream saat tag autentikasi TIDAK cocok BERBEDA antara
     * JVM desktop (tempat unit test dijalankan — di situ exception dilempar, jadi test lolos) dan
     * beberapa versi runtime Android, yang pada kasus tertentu hanya MEMOTONG stream tanpa
     * melempar exception sama sekali. Akibat nyatanya fatal: password salah menghasilkan file
     * "berhasil" berisi sampah, lalu BackupRepository.restore menimpa database asli dengan
     * sampah itu. Sekarang dekripsi memakai `cipher.doFinal()` eksplisit di atas buffer —
     * satu-satunya cara yang DIJAMIN melempar AEADBadTagException kalau tag tidak cocok.
     *
     * Konsekuensi yang disengaja: seluruh isi backup dibaca ke memori sekali jalan sebelum
     * ditulis. Untuk database POS satu toko (puluhan MB paling banyak) ini wajar, dan
     * keamanannya jauh lebih penting daripada hemat memori di jalur yang dijalankan sekali
     * seumur restore. Kalau suatu saat ukuran DB benar-benar besar, ganti ke pemrosesan
     * per-chunk dengan `cipher.update()` + satu `cipher.doFinal()` di akhir — TETAP jangan
     * kembali ke CipherInputStream.
     */
    fun decrypt(source: File, destination: File, password: String) {
        val payload: ByteArray
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        FileInputStream(source).use { input ->
            val header = ByteArray(MAGIC.size)
            if (!input.readFully(header) || !header.contentEquals(MAGIC)) {
                throw WrongPasswordException()
            }
            // readFully, bukan read(): InputStream.read(byte[]) BOLEH mengembalikan lebih sedikit
            // byte dari kapasitas buffer tanpa berarti file rusak. Versi lama menganggap hasil
            // baca pendek = file tidak valid (dan sebaliknya bisa memakai salt/IV yang belum
            // terisi penuh) — latent bug yang muncul tidak menentu tergantung sumber file.
            if (!input.readFully(salt) || !input.readFully(iv)) {
                throw WrongPasswordException()
            }
            payload = input.readBytes()
        }

        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(payload)
        } catch (e: Exception) {
            // AEADBadTagException (tag GCM tidak cocok) = password salah ATAU file rusak.
            destination.delete()
            throw WrongPasswordException()
        }

        FileOutputStream(destination).use { output -> output.write(plain) }
    }

    /** Baca tepat sebanyak kapasitas [buffer]; false kalau file habis lebih dulu. */
    private fun java.io.InputStream.readFully(buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }
}
