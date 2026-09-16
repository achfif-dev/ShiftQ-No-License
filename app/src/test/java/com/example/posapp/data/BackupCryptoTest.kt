package com.example.posapp.data

import com.example.posapp.data.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets

class BackupCryptoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `encrypt then decrypt with correct password restores original bytes`() {
        val source = tempFolder.newFile("db.sqlite")
        val original = "isi database palsu untuk pengujian".toByteArray(StandardCharsets.UTF_8)
        source.writeBytes(original)

        val encrypted = tempFolder.newFile("backup.posbak")
        BackupCrypto.encrypt(source, encrypted, "password-toko-123")
        assertTrue("file terenkripsi harus punya magic header", BackupCrypto.hasMagic(encrypted))

        val restored = tempFolder.newFile("restored.sqlite")
        BackupCrypto.decrypt(encrypted, restored, "password-toko-123")

        assertArrayEquals(original, restored.readBytes())
    }

    @Test
    fun `decrypt with wrong password throws WrongPasswordException`() {
        val source = tempFolder.newFile("db.sqlite")
        source.writeBytes("data rahasia toko".toByteArray(StandardCharsets.UTF_8))

        val encrypted = tempFolder.newFile("backup.posbak")
        BackupCrypto.encrypt(source, encrypted, "password-benar")

        val restored = tempFolder.newFile("restored.sqlite")
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(encrypted, restored, "password-salah")
        }
    }

    @Test
    fun `plain unencrypted file is not detected as having the magic header`() {
        val plain = tempFolder.newFile("old_backup.db")
        plain.writeBytes("SQLite format 3 legacy raw copy".toByteArray(StandardCharsets.UTF_8))
        assertTrue("backup lama (mentah, sebelum enkripsi ada) harus tetap terdeteksi bukan format baru",
            !BackupCrypto.hasMagic(plain))
    }
}
