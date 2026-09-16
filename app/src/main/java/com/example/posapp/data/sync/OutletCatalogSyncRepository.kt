package com.example.posapp.data.sync

import com.example.posapp.data.settings.StoreProfileRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Satu baris katalog produk milik SATU cabang, dipakai untuk fitur "Cek Stok Semua Cabang". */
data class OutletProductRow(
    val outletId: String,
    val outletName: String,
    val sku: String,
    val name: String,
    val stock: Int,
    val sellPrice: Double,
    val updatedAt: Long,
)

/**
 * LINGKUP FITUR (baca dulu sebelum menambah): ini SATU ARAH (push-only) dan READ-ONLY di sisi
 * baca — setiap cabang mengirim snapshot katalognya sendiri ke Firestore, lalu admin bisa
 * MELIHAT (bukan mengedit) katalog+stok seluruh cabang (di grup toko yang sama) dari
 * satu HP untuk kebutuhan seperti "cabang lain masih ada stok produk ini?" sebelum menyarankan
 * pelanggan pindah cabang, atau sebelum membuat mutasi stok manual antar cabang.
 *
 * SENGAJA TIDAK melakukan merge dua arah ke Room lokal (menulis balik produk cabang lain ke
 * database sendiri) — itu berisiko konflik ID/SKU dan bisa merusak sumber kebenaran data toko
 * yang selama ini murni per-device. Kalau ke depan dibutuhkan katalog terpusat sungguhan (satu
 * sumber harga/stok dipakai bersama), itu perubahan arsitektur besar di atas fondasi ini
 * (idealnya pusat data pindah ke Firestore sepenuhnya, bukan Room lokal per device).
 *
 * TEMUAN KEAMANAN (audit ulang — kebocoran data lintas-pelanggan): lihat dokumentasi lengkap di
 * [TenantAuthProvider]. Setiap dokumen `outlet_catalog/{outletId}` sekarang WAJIB membawa field
 * `customerGroupId` (diisi dari [TenantSession], bukan input pengguna) dan [listKnownOutlets]
 * WAJIB memfilter query dengan field yang sama, supaya toko dari grup lain tidak lagi ikut
 * muncul di dropdown pemilihan cabang.
 */
@Singleton
class OutletCatalogSyncRepository @Inject constructor(
    private val tenantAuth: TenantAuthProvider,
    private val storeProfileRepository: StoreProfileRepository,
) {

    fun isConfigured(): Boolean = tenantAuth.isConfigured()

    suspend fun pushCatalog(outletId: String, outletName: String, rows: List<OutletProductRow>): Boolean {
        val db = tenantAuth.firestoreOrNull() ?: return false
        val session = tenantAuth.ensureTenantSignedIn(storeProfileRepository) ?: return false
        return try {
            // Klaim/perbarui dokumen induk LEBIH DULU (sebelum menulis produk) — rule Firestore
            // untuk subkoleksi `products` mengecek ownerUid & customerGroupId di dokumen induk ini
            // lewat get(), jadi kalau urutannya dibalik, sinkronisasi PERTAMA KALI (dokumen induk
            // belum ada) akan selalu ditolak rule karena get() belum menemukan field apa pun.
            db.collection("outlet_catalog").document(outletId)
                .set(
                    hashMapOf(
                        "outletName" to outletName,
                        "lastSyncedAt" to System.currentTimeMillis(),
                        "ownerUid" to session.uid,
                        "customerGroupId" to session.customerGroupId,
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()

            rows.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { row ->
                    val docRef = db.collection("outlet_catalog").document(outletId)
                        .collection("products").document(row.sku)
                    batch.set(
                        docRef,
                        hashMapOf(
                            "outletName" to outletName,
                            "sku" to row.sku,
                            "name" to row.name,
                            "stock" to row.stock,
                            "sellPrice" to row.sellPrice,
                            "updatedAt" to row.updatedAt,
                        )
                    )
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Dengarkan katalog SATU cabang lain secara realtime (dipakai layar "Cek Stok Semua Cabang"
     * saat admin memilih salah satu cabang untuk dilihat detailnya). Rule read subkoleksi
     * `products` memverifikasi kepemilikan grup lewat get() ke dokumen induk outletId ini — tidak
     * butuh filter query tambahan di sini karena outletId sudah jadi bagian tetap dari path,
     * bukan field yang difilter dari hasil query. */
    fun observeOutletCatalog(outletId: String): Flow<List<OutletProductRow>> = callbackFlow {
        val db = tenantAuth.firestoreOrNull()
        if (db == null) { trySend(emptyList()); close(); return@callbackFlow }
        val session = tenantAuth.ensureTenantSignedIn(storeProfileRepository)
        if (session == null) { trySend(emptyList()); close(); return@callbackFlow }
        val registration = db.collection("outlet_catalog").document(outletId).collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) { trySend(emptyList()); return@addSnapshotListener }
                val rows = snapshot.documents.mapNotNull { doc ->
                    val sku = doc.getString("sku") ?: return@mapNotNull null
                    OutletProductRow(
                        outletId = outletId,
                        outletName = doc.getString("outletName") ?: outletId,
                        sku = sku,
                        name = doc.getString("name") ?: sku,
                        stock = (doc.getLong("stock") ?: 0L).toInt(),
                        sellPrice = doc.getDouble("sellPrice") ?: 0.0,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                    )
                }
                trySend(rows)
            }
        awaitClose { registration.remove() }
    }

    /** Daftar cabang yang pernah sinkron DI GRUP PELANGGAN/LISENSI YANG SAMA (untuk dropdown
     * pemilihan cabang di UI). `.whereEqualTo("customerGroupId", ...)` WAJIB ada supaya query ini
     * provably cocok dengan rule read (lihat [TenantAuthProvider]) — sebelumnya `.get()` polos di
     * sini mengambil literasi SELURUH cabang dari SEMUA pelanggan tanpa filter kepemilikan sama
     * sekali, yaitu temuan utama audit ulang. */
    suspend fun listKnownOutlets(): List<Pair<String, String>> {
        val db = tenantAuth.firestoreOrNull() ?: return emptyList()
        val session = tenantAuth.ensureTenantSignedIn(storeProfileRepository) ?: return emptyList()
        return try {
            db.collection("outlet_catalog")
                .whereEqualTo("customerGroupId", session.customerGroupId)
                .get()
                .await()
                .documents.map { doc ->
                    doc.id to (doc.getString("outletName") ?: doc.id)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
