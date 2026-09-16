package com.example.posapp.data.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
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
                input.read(header) == MAGIC.size && header.contentEquals(MAGIC)
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

    /** @throws WrongPasswordException jika password salah atau file bukan .posbak yang valid. */
    fun decrypt(source: File, destination: File, password: String) {
        FileInputStream(source).use { input ->
            val header = ByteArray(MAGIC.size)
            if (input.read(header) != MAGIC.size || !header.contentEquals(MAGIC)) {
                throw WrongPasswordException()
            }
            val salt = ByteArray(SALT_LENGTH)
            val iv = ByteArray(IV_LENGTH)
            if (input.read(salt) != SALT_LENGTH || input.read(iv) != IV_LENGTH) {
                throw WrongPasswordException()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
                CipherInputStream(input, cipher).use { cipherIn ->
                    FileOutputStream(destination).use { output -> cipherIn.copyTo(output) }
                }
            } catch (e: Exception) {
                // AEADBadTagException (tag GCM tidak cocok) = password salah ATAU file rusak.
                destination.delete()
                throw WrongPasswordException()
            }
        }
    }
}
