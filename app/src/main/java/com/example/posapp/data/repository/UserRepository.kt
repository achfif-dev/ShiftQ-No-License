package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.UserDao
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun observeAll(): Flow<List<UserEntity>> = userDao.observeAll()

    suspend fun hasAnyUser(): Boolean = userDao.countActive() > 0

    /** Dipakai UserManagementViewModel.deleteUser sebelum menghapus — lihat [deleteUser]. */
    suspend fun countActiveAdmins(): Int = userDao.countActiveAdmins()

    /**
     * Mencoba login dengan PIN. Mengembalikan user jika PIN cocok dengan salah satu user aktif.
     *
     * PIN baru diverifikasi dengan PBKDF2+salt per-user (tidak bisa dicari via WHERE di SQL
     * karena tiap user punya salt berbeda, jadi dicek satu-satu -- aman untuk jumlah user kecil
     * seperti kasir/admin satu toko). Akun lama yang masih pakai skema SHA-256 polos (pinSalt
     * null) tetap bisa login seperti biasa, lalu otomatis di-upgrade ke PBKDF2+salt di sini juga
     * supaya makin lama makin sedikit akun yang masih rentan.
     */
    suspend fun login(pin: String): UserEntity? {
        val users = userDao.getAllActive()
        for (user in users) {
            val salt = user.pinSalt
            if (salt != null) {
                if (hashWithSalt(pin, salt) == user.pinHash) return user
            } else if (legacyHash(pin) == user.pinHash) {
                val newSalt = generateSalt()
                val upgraded = user.copy(pinHash = hashWithSalt(pin, newSalt), pinSalt = newSalt)
                userDao.update(upgraded)
                return upgraded
            }
        }
        return null
    }

    suspend fun createUser(name: String, pin: String, role: UserRole): Long {
        val salt = generateSalt()
        return userDao.insert(
            UserEntity(name = name.trim(), pinHash = hashWithSalt(pin, salt), pinSalt = salt, role = role)
        )
    }

    suspend fun updatePin(user: UserEntity, newPin: String) {
        val salt = generateSalt()
        userDao.update(user.copy(pinHash = hashWithSalt(newPin, salt), pinSalt = salt))
    }

    suspend fun renameUser(user: UserEntity, newName: String) {
        userDao.update(user.copy(name = newName.trim()))
    }

    /** Soft-delete (isActive=false, bukan hapus baris) supaya riwayat transaksi/audit log lama
     * yang merujuk user ini tetap utuh. PENTING: pemanggil (UserManagementViewModel) WAJIB
     * memastikan dulu ini bukan Admin aktif terakhir sebelum memanggil fungsi ini — repository
     * ini sendiri tidak menolak, supaya tetap fleksibel dipakai dari tempat lain (mis. skrip
     * migrasi) tanpa terkunci aturan UI. Lihat [countActiveAdmins]. */
    suspend fun deleteUser(id: Long) = userDao.softDelete(id)

    private fun generateSalt(): String {
        val bytes = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashWithSalt(pin: String, saltHex: String): String {
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    /** Skema lama (sebelum v10): SHA-256 polos tanpa salt. Dipertahankan HANYA untuk mengenali
     * & meng-upgrade akun lama saat login -- tidak dipakai lagi untuk PIN baru/yang diubah. */
    private fun legacyHash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SALT_LENGTH_BYTES = 16
        const val PBKDF2_ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
    }
}
