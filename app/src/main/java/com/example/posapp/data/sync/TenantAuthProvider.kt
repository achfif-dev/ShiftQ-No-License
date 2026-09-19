package com.example.posapp.data.sync

import android.content.Context
import com.example.posapp.data.settings.StoreProfileRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TENANT_APP_NAME = "posapp_tenant_sync"
private const val FUNCTIONS_REGION = "asia-southeast2"

/** Sesi tenant yang berhasil sign-in: [uid] dipakai sebagai `ownerUid` saat menulis dokumen,
 * [customerGroupId] dipakai sebagai field wajib di tiap dokumen DAN sebagai filter query — lihat
 * pemanggil di [CloudSyncRepository]/[OutletCatalogSyncRepository]. */
data class TenantSession(val uid: String, val customerGroupId: String)

/**
 * TEMUAN KEAMANAN (audit ulang — kebocoran data lintas-pelanggan/cross-tenant di Cloud Sync):
 * sebelumnya CloudSyncRepository & OutletCatalogSyncRepository sign-in ANONIM biasa
 * (`auth.signInAnonymously()`) lewat `FirebaseAuth.getInstance()` default — SEMUA pengguna app
 * ini, dari toko mana pun, mendapat token Firebase Auth yang SETARA dan tidak membawa info
 * kepemilikan grup pelanggan apa pun. Karena backend Cloud Sync hidup di SATU proyek Firebase
 * milik developer (dipakai bersama semua pelanggan, lihat functions/index.js), firestore.rules
 * untuk outlet_summaries/outlet_catalog cuma bisa mensyaratkan `request.auth != null` untuk READ
 * — bukan kepemilikan grup pelanggan — sehingga dua toko yang tidak saling terkait tapi
 * sama-sama mengaktifkan toggle "Sinkronisasi Cloud"/"Cek Stok Semua Cabang" bisa saling membaca
 * omzet harian, katalog produk, stok, dan harga jual satu sama lain.
 *
 * Perbaikan: sign-in sekarang memakai CUSTOM TOKEN (bukan anonim polos) yang di-mint oleh Cloud
 * Function `mintSyncToken` (functions/index.js) dengan custom claim `customerGroupId` =
 * SHA-256(syncGroupCode) — [StoreProfile.syncGroupCode], kode 8-karakter yang dibuat otomatis
 * sekali per instalasi (lihat StoreProfileRepository.ensureSyncGroupCode) dan bisa disalin manual
 * ke cabang lain supaya masuk grup yang sama (tidak ada aktivasi/pembayaran/verifikasi server —
 * aplikasi ini TIDAK PAKAI sistem lisensi). Satu grup per kode, jadi kalau satu toko punya
 * beberapa cabang dengan kode yang sama, cabang-cabang itu MEMANG dimaksudkan saling bisa lihat
 * data cabang lain di grup mereka sendiri (tujuan awal fitur ini), tapi TIDAK BISA melihat grup
 * toko lain (selama kode tidak dibagikan ke pihak luar). firestore.rules lalu mensyaratkan
 * `request.auth.token.customerGroupId` dokumen yang dibaca/ditulis sama dengan token pemanggil.
 *
 * Sesi custom-token ini SENGAJA dijalankan di FirebaseApp KEDUA ([TENANT_APP_NAME], config sama
 * persis dari google-services.json yang sama, hanya nama instance beda) — BUKAN di
 * `FirebaseAuth.getInstance()` default yang masih dipakai PaymentGatewayRepository untuk sign-in
 * anonim. Kalau dipaksa satu instance yang sama, sign-in di sini akan MENIMPA currentUser default
 * dan berisiko merusak `ownerUid` yang sudah tersimpan di `gateway_credentials` untuk toko yang
 * sudah pernah mengonfigurasi Payment Gateway sebelum perbaikan ini — pemisahan instance ini
 * murni untuk menghindari efek samping itu, TIDAK mengubah perilaku payment gateway sama sekali.
 *
 * customerGroupId WAJIB disertakan sebagai field di tiap dokumen yang ditulis, dan sebagai
 * filter `.whereEqualTo` di tiap query koleksi (list) — Firestore mewajibkan query itu sendiri
 * "provably" cocok dengan rule read untuk operasi list saat rule membaca `resource.data`
 * langsung, bukan cukup benar per-dokumen saja.
 */
@Singleton
class TenantAuthProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var cachedGroupId: String? = null

    private fun tenantApp(): FirebaseApp? = runCatching {
        FirebaseApp.getApps(context).firstOrNull { it.name == TENANT_APP_NAME }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, TENANT_APP_NAME)
    }.getOrNull()

    /** Instance Firestore yang TERIKAT ke sesi Auth tenant di atas — WAJIB dipakai (bukan
     * `FirebaseFirestore.getInstance()` default) supaya `request.auth` di firestore.rules berisi
     * custom claim `customerGroupId` yang benar. */
    fun firestoreOrNull(): FirebaseFirestore? =
        tenantApp()?.let { runCatching { FirebaseFirestore.getInstance(it) }.getOrNull() }

    private fun authOrNull(): FirebaseAuth? =
        tenantApp()?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }

    private fun functionsOrNull(): FirebaseFunctions? =
        tenantApp()?.let { runCatching { FirebaseFunctions.getInstance(it, FUNCTIONS_REGION) }.getOrNull() }

    fun isConfigured(): Boolean = firestoreOrNull() != null && authOrNull() != null

    /**
     * Pastikan sudah sign-in ke sesi tenant ini, dikunci ke [StoreProfile.syncGroupCode] device
     * ini (dibuat otomatis kalau belum ada — lihat StoreProfileRepository.ensureSyncGroupCode).
     * Kembalikan null kalau gagal (offline, dst.) — SEMUA fail-soft, tidak pernah melempar
     * exception ke pemanggil, konsisten dengan filosofi fitur Cloud Sync yang murni opsional dan
     * tidak boleh mengganggu alur kasir/checkout normal.
     */
    suspend fun ensureTenantSignedIn(storeProfileRepository: StoreProfileRepository): TenantSession? {
        val auth = authOrNull() ?: return null
        val functions = functionsOrNull() ?: return null
        val groupCode = storeProfileRepository.ensureSyncGroupCode()
        if (groupCode.isBlank()) return null
        val groupId = sha256Hex(groupCode)

        val existingUid = auth.currentUser?.uid
        if (existingUid != null && cachedGroupId == groupId) {
            return TenantSession(existingUid, groupId)
        }

        val deviceId = storeProfileRepository.ensureOutletId() // ID stabil per instalasi, sudah ada untuk keperluan Cloud Sync lain
        return try {
            // v16 (audit — pemalsuan identitas cabang lain): deviceId SAJA tidak lagi cukup untuk
            // mendapatkan token. Server mendaftarkan deviceId pada permintaan PERTAMA dan
            // mengembalikan rahasia acak yang disimpan lokal; permintaan berikutnya wajib
            // membuktikan rahasia itu. Sebelumnya uid dibentuk langsung dari deviceId kiriman
            // client — dan deviceId = outletId yang bisa dibaca anggota grup mana pun lewat
            // outlet_catalog, sehingga satu cabang bisa menimpa data cabang lain.
            val storedSecret = storeProfileRepository.getSyncDeviceSecret()
            val data = hashMapOf(
                "groupCode" to groupCode,
                "deviceId" to deviceId,
                "deviceSecret" to (storedSecret ?: "")
            )
            val result = functions.getHttpsCallable("mintSyncToken").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any?> ?: return null
            val customToken = map["customToken"] as? String ?: return null
            (map["deviceSecret"] as? String)?.takeIf { it.isNotBlank() && it != storedSecret }
                ?.let { storeProfileRepository.setSyncDeviceSecret(it) }
            val authResult = auth.signInWithCustomToken(customToken).await()
            val uid = authResult.user?.uid ?: return null
            cachedGroupId = groupId
            TenantSession(uid, groupId)
        } catch (e: Exception) {
            null
        }
    }

    private fun sha256Hex(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}
