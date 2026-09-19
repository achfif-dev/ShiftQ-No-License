package com.example.posapp.data.auth

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.loginAttemptDataStore by preferencesDataStore(name = "login_attempts")

/**
 * Melacak percobaan login PIN yang GAGAL secara PERSISTEN (DataStore, bukan in-memory di
 * LoginViewModel) dan menerapkan lockout progresif.
 *
 * TEMUAN KEAMANAN (audit ulang): sebelumnya tidak ada batasan sama sekali pada jumlah percobaan
 * PIN yang gagal berturut-turut. PIN minimal cuma 4 digit (10.000 kombinasi) dan tidak ada
 * lockout/backoff -- siapa pun dengan akses fisik ke device (dalam kondisi menyala/tidak
 * terkunci OS) bisa mencoba brute-force manual atau lewat automation UI dalam waktu yang relatif
 * singkat untuk mendapatkan akses Admin (Void/Koreksi Transaksi/Pengaturan). Disimpan di
 * DataStore (bukan field ViewModel) supaya lockout TIDAK bisa direset begitu saja dengan
 * menutup-buka ulang layar Login / force-stop app -- ViewModel baru dibuat lagi tiap kali layar
 * Login muncul, tapi DataStore ini tetap sama.
 *
 * Tidak dipisah per-user (per PIN/user id) secara sengaja: [com.example.posapp.data.repository.UserRepository.login]
 * mencoba satu PIN yang dimasukkan terhadap SEMUA user aktif sekaligus -- jadi "percobaan gagal"
 * secara alami sudah berbasis per-DEVICE (satu layar Login dipakai bersama, sesuai model kasir/
 * admin berbagi satu HP), bukan per akun individual.
 */
@Singleton
class LoginAttemptRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Percobaan gagal beruntun yang masih ditoleransi (mis. kasir salah pencet) sebelum
         * lockout PERTAMA mulai berlaku. */
        private const val MAX_FREE_ATTEMPTS = 5

        /** Lockout berlipat ganda (30 detik, 60 detik, 120 detik, ...) setiap kegagalan berikutnya sesudah
         * ambang di atas, dibatasi maksimal 5 menit -- cukup untuk membuat brute-force manual
         * atau via skrip/automation tidak praktis (PIN 4 digit = 10.000 kombinasi, dengan
         * lockout ini butuh berhari-hari), tanpa mengunci device kasir secara permanen hanya
         * karena beberapa kali salah ketik. */
        private const val BASE_LOCKOUT_MILLIS = 30_000L
        private const val MAX_LOCKOUT_MILLIS = 5 * 60_000L

        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        // TEMUAN KEAMANAN (audit ulang): SEBELUMNYA disimpan sebagai wall-clock
        // (System.currentTimeMillis()) -- siapa pun bisa memajukan tanggal/jam HP di
        // Pengaturan untuk melewati lockout ini secara instan. SystemClock.elapsedRealtime()
        // adalah jam sejak boot yang TIDAK terpengaruh perubahan tanggal/jam sistem, cuma reset
        // ke 0 kalau device di-reboot (restart device jauh lebih tidak praktis sebagai cara
        // membypass lockout berulang kali dibanding sekadar mengubah jam).
        private val KEY_LOCKED_UNTIL_ELAPSED = longPreferencesKey("locked_until_elapsed_realtime")

        // TEMUAN LANJUTAN (audit v16): elapsedRealtime SAJA masih bisa dilewati dengan
        // ME-REBOOT HP — jam sejak boot kembali ke 0, jadi deadline lama otomatis dianggap
        // sudah lewat. Sekarang deadline disimpan GANDA: satu berbasis elapsedRealtime (tahan
        // terhadap pengubahan jam sistem) dan satu berbasis wall-clock (tahan terhadap reboot).
        // Lockout dianggap masih berjalan selama SALAH SATU dari keduanya belum lewat, jadi
        // penyerang harus mengalahkan kedua sumber waktu sekaligus, bukan salah satunya.
        private val KEY_LOCKED_UNTIL_WALL = longPreferencesKey("locked_until_wall_clock")
    }

    data class LockState(val remainingSeconds: Long)

    /** null kalau sedang TIDAK dalam masa lockout (boleh mencoba PIN lagi sekarang). */
    suspend fun currentLockState(): LockState? {
        val prefs = context.loginAttemptDataStore.data.first()
        val remainingElapsed = (prefs[KEY_LOCKED_UNTIL_ELAPSED] ?: 0L) - SystemClock.elapsedRealtime()
        val remainingWall = (prefs[KEY_LOCKED_UNTIL_WALL] ?: 0L) - System.currentTimeMillis()
        // Ambil yang PALING LAMA di antara kedua sumber waktu: memundurkan jam sistem tidak
        // menolong (elapsedRealtime tidak terpengaruh), me-reboot HP juga tidak (wall-clock
        // tersimpan tetap di masa depan). Batas atas MAX_LOCKOUT_MILLIS mencegah nilai wall-clock
        // yang aneh (mis. pengguna memundurkan jam bertahun-tahun) mengunci device selamanya.
        val remainingMillis = maxOf(remainingElapsed, remainingWall).coerceAtMost(MAX_LOCKOUT_MILLIS)
        if (remainingMillis <= 0) return null
        return LockState(remainingSeconds = (remainingMillis + 999) / 1000)
    }

    /** Panggil setiap kali PIN yang dicoba TERNYATA SALAH. Mengembalikan [LockState] baru kalau
     * percobaan ini yang membuat device masuk (atau tetap berada di) masa lockout, atau null
     * kalau belum melewati ambang [MAX_FREE_ATTEMPTS]. */
    suspend fun recordFailure(): LockState? {
        var result: LockState? = null
        context.loginAttemptDataStore.edit { prefs ->
            val attempts = (prefs[KEY_FAILED_ATTEMPTS] ?: 0) + 1
            prefs[KEY_FAILED_ATTEMPTS] = attempts
            if (attempts > MAX_FREE_ATTEMPTS) {
                val doublings = (attempts - MAX_FREE_ATTEMPTS - 1).coerceIn(0, 10)
                val lockoutMillis = (BASE_LOCKOUT_MILLIS shl doublings).coerceAtMost(MAX_LOCKOUT_MILLIS)
                prefs[KEY_LOCKED_UNTIL_ELAPSED] = SystemClock.elapsedRealtime() + lockoutMillis
                prefs[KEY_LOCKED_UNTIL_WALL] = System.currentTimeMillis() + lockoutMillis
                result = LockState(remainingSeconds = (lockoutMillis + 999) / 1000)
            }
        }
        return result
    }

    /** Panggil setiap kali login BERHASIL -- reset penuh counter, supaya kasir/admin yang
     * memang benar tidak terus dibebani lockout dari kegagalan lama. */
    suspend fun recordSuccess() {
        context.loginAttemptDataStore.edit { prefs ->
            prefs[KEY_FAILED_ATTEMPTS] = 0
            prefs.remove(KEY_LOCKED_UNTIL_ELAPSED)
            prefs.remove(KEY_LOCKED_UNTIL_WALL)
        }
    }
}
