package com.example.posapp.data.sync

import com.example.posapp.data.settings.StoreProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Ringkasan omzet SATU cabang untuk SATU tanggal — satu-satunya data yang dikirim ke cloud
 * (bukan detail transaksi/produk/pelanggan), supaya tetap ringan & aman secara privasi. */
data class OutletSalesSummary(
    val outletId: String,
    val outletName: String,
    val dateKey: String, // format "yyyy-MM-dd"
    val totalRevenue: Double,
    val totalTransactions: Int,
    val updatedAt: Long
)

sealed class CloudSyncStatus {
    object Idle : CloudSyncStatus()
    /** google-services.json belum ada / Firebase belum disiapkan — lihat FIREBASE_SETUP.md. */
    object NotConfigured : CloudSyncStatus()
    object Syncing : CloudSyncStatus()
    data class Success(val at: Long) : CloudSyncStatus()
    data class Error(val message: String) : CloudSyncStatus()
}

/**
 * Mengirim & mengambil ringkasan omzet lintas cabang lewat Firestore. SELURUH fungsi di sini
 * fail-soft: kalau Firebase belum dikonfigurasi (tidak ada google-services.json), device offline,
 * atau lisensi belum aktivasi, status berubah jadi
 * [CloudSyncStatus.NotConfigured]/[CloudSyncStatus.Error] — TIDAK PERNAH melempar exception ke
 * pemanggil, supaya fitur ini murni opsional dan tidak pernah mengganggu alur kasir/checkout
 * normal yang sepenuhnya offline-first.
 *
 * TEMUAN KEAMANAN (audit ulang — kebocoran data lintas-pelanggan): lihat dokumentasi lengkap di
 * [TenantAuthProvider]. Setiap dokumen sekarang WAJIB membawa field `customerGroupId` (diisi dari
 * [TenantSession], bukan dari input pengguna) dan setiap query WAJIB memfilter dengan field yang
 * sama — tanpa filter query ini, Firestore menolak seluruh query karena rule read membaca
 * `resource.data.customerGroupId` langsung (lihat firestore.rules untuk penjelasan "provably
 * compliant query").
 */
@Singleton
class CloudSyncRepository @Inject constructor(
    private val tenantAuth: TenantAuthProvider,
    private val storeProfileRepository: StoreProfileRepository,
) {

    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    suspend fun pushDailySummary(summary: OutletSalesSummary) {
        val db = tenantAuth.firestoreOrNull()
        if (db == null) {
            _status.value = CloudSyncStatus.NotConfigured
            return
        }
        _status.value = CloudSyncStatus.Syncing
        try {
            val session = tenantAuth.ensureTenantSignedIn(storeProfileRepository)
            if (session == null) {
                _status.value = CloudSyncStatus.Error(
                    "Gagal autentikasi cloud (cek koneksi internet)"
                )
                return
            }
            val docId = "${summary.outletId}_${summary.dateKey}"
            val data = hashMapOf(
                "outletId" to summary.outletId,
                "outletName" to summary.outletName,
                "dateKey" to summary.dateKey,
                "totalRevenue" to summary.totalRevenue,
                "totalTransactions" to summary.totalTransactions,
                "updatedAt" to summary.updatedAt,
                "ownerUid" to session.uid,
                "customerGroupId" to session.customerGroupId,
            )
            db.collection("outlet_summaries").document(docId).set(data).await()
            _status.value = CloudSyncStatus.Success(System.currentTimeMillis())
        } catch (e: Exception) {
            _status.value = CloudSyncStatus.Error(e.message ?: "Gagal sinkronisasi ke cloud")
        }
    }

    /** Ambil ringkasan SEMUA cabang (di grup pelanggan/lisensi yang sama) untuk satu tanggal
     * tertentu — dipakai Ringkasan Semua Cabang.
     *
     * `.whereEqualTo("customerGroupId", ...)` di sini BUKAN sekadar optimisasi — ini WAJIB supaya
     * query provably cocok dengan rule read di firestore.rules (lihat [TenantAuthProvider]);
     * tanpa filter ini Firestore menolak seluruh query kalau ada dokumen grup pelanggan lain yang
     * cocok dengan `dateKey` yang sama. */
    suspend fun fetchSummariesForDate(dateKey: String): List<OutletSalesSummary> {
        val db = tenantAuth.firestoreOrNull() ?: return emptyList()
        val session = tenantAuth.ensureTenantSignedIn(storeProfileRepository) ?: return emptyList()
        return try {
            val snapshot = db.collection("outlet_summaries")
                .whereEqualTo("dateKey", dateKey)
                .whereEqualTo("customerGroupId", session.customerGroupId)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val outletId = doc.getString("outletId") ?: return@mapNotNull null
                OutletSalesSummary(
                    outletId = outletId,
                    outletName = doc.getString("outletName") ?: outletId,
                    dateKey = dateKey,
                    totalRevenue = doc.getDouble("totalRevenue") ?: 0.0,
                    totalTransactions = (doc.getLong("totalTransactions") ?: 0L).toInt(),
                    updatedAt = doc.getLong("updatedAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** true kalau Firebase terlihat terkonfigurasi (bukan jaminan kredensial valid, hanya bahwa
     * FirebaseApp berhasil di-inisialisasi dari google-services.json yang ada). */
    fun isConfigured(): Boolean = tenantAuth.isConfigured()
}
