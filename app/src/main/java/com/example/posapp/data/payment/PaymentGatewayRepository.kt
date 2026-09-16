package com.example.posapp.data.payment

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.posapp.data.settings.StoreProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val Context.paymentGatewayDataStore by preferencesDataStore(name = "payment_gateway")

sealed class GatewayResult<out T> {
    data class Success<T>(val data: T) : GatewayResult<T>()
    data class Error(val message: String) : GatewayResult<Nothing>()
}

/**
 * Self-service integrasi Midtrans QRIS. Alur:
 * 1. Admin isi Merchant ID/Server Key/Client Key MILIK TOKO SENDIRI di Pengaturan > Payment
 *    Gateway → [saveCredentials] mengirimkannya SEKALI ke Cloud Function, tidak disimpan di HP.
 * 2. Saat checkout pilih "QRIS Otomatis" → [createCharge] memanggil Cloud Function
 *    `createQrisCharge`, yang di sisi server memanggil Midtrans Core API pakai kredensial toko
 *    tsb, membuat kode QR dinamis resmi dari Midtrans (bukan suntik manual seperti QRIS statis).
 * 3. [observeChargeStatus] mendengarkan Firestore realtime — begitu Midtrans mengirim webhook
 *    settlement ke Cloud Function `midtransNotification`, status berubah SETTLED otomatis di
 *    layar kasir tanpa perlu tekan tombol "cek manual".
 *
 * Fail-soft: kalau belum dikonfigurasi/offline, checkout tetap bisa lanjut pakai QRIS statis biasa
 * (lihat PosScreen) — fitur ini murni opsional tambahan, tidak pernah memblokir alur kasir dasar.
 */
@Singleton
class PaymentGatewayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storeProfileRepository: StoreProfileRepository,
) {
    companion object {
        private val KEY_CONFIGURED = booleanPreferencesKey("gateway_configured")
        private val KEY_MERCHANT_ID_MASKED = stringPreferencesKey("merchant_id_masked")
    }

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance("asia-southeast2") }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // TEMUAN KEAMANAN (ditemukan saat audit ulang): sebelumnya fungsi ini dipanggil TANPA
    // memastikan device sudah sign-in ke Firebase Auth dulu. Cloud Function di sisi server
    // (saveGatewayCredentials/createQrisCharge) SEKARANG mewajibkan `request.auth` terisi untuk
    // mengikat kepemilikan kredensial ke satu identitas (ownerUid) — kalau device belum
    // sign-in, request.auth akan null dan Cloud Function menolak dengan "unauthenticated".
    // ensureSignedIn() dipanggil di setiap fungsi publik di bawah supaya ini transparan bagi
    // pemanggil (PaymentGatewaySettingsViewModel, QrisAutoPaymentViewModel) — tidak perlu tahu
    // detail auth sama sekali.
    private suspend fun ensureSignedIn(): Boolean {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return true
        return try {
            auth.signInAnonymously().await(); true
        } catch (e: Exception) {
            false
        }
    }

    /** Status ringkas TANPA data sensitif — aman disimpan lokal, hanya untuk menampilkan
     * "Payment Gateway: Terhubung ✓" di UI Pengaturan. */
    val isConfigured: Flow<Boolean> = context.paymentGatewayDataStore.data.map { it[KEY_CONFIGURED] ?: false }

    suspend fun saveCredentials(input: GatewayCredentialsInput): GatewayResult<Unit> {
        if (!ensureSignedIn()) {
            return GatewayResult.Error("Gagal menghubungkan sesi aman. Pastikan internet aktif lalu coba lagi.")
        }
        val outletId = storeProfileRepository.ensureOutletId()
        return try {
            val data = hashMapOf(
                "outletId" to outletId,
                "merchantId" to input.merchantId.trim(),
                "serverKey" to input.serverKey.trim(),
                "clientKey" to input.clientKey.trim(),
                "isProduction" to input.isProduction,
            )
            functions.getHttpsCallable("saveGatewayCredentials").call(data).await()
            context.paymentGatewayDataStore.edit {
                it[KEY_CONFIGURED] = true
                it[KEY_MERCHANT_ID_MASKED] = maskMerchantId(input.merchantId)
            }
            GatewayResult.Success(Unit)
        } catch (e: Exception) {
            GatewayResult.Error(e.message ?: "Gagal menyimpan kredensial payment gateway.")
        }
    }

    suspend fun clearCredentials() {
        val outletId = storeProfileRepository.ensureOutletId()
        runCatching {
            functions.getHttpsCallable("saveGatewayCredentials")
                .call(hashMapOf("outletId" to outletId, "clear" to true))
                .await()
        }
        context.paymentGatewayDataStore.edit { it.remove(KEY_CONFIGURED); it.remove(KEY_MERCHANT_ID_MASKED) }
    }

    /** Buat tagihan QRIS dinamis resmi dari Midtrans untuk satu transaksi. [orderId] harus unik
     * (dipakai nomor invoice transaksi lokal supaya mudah dicocokkan). */
    suspend fun createCharge(orderId: String, amountRupiah: Long): GatewayResult<QrisCharge> {
        if (!ensureSignedIn()) {
            return GatewayResult.Error("Gagal menghubungkan sesi aman. Pastikan internet aktif lalu coba lagi.")
        }
        val outletId = storeProfileRepository.ensureOutletId()
        return try {
            val data = hashMapOf(
                "outletId" to outletId,
                "orderId" to orderId,
                "amount" to amountRupiah,
            )
            val result = functions.getHttpsCallable("createQrisCharge").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any?> ?: return GatewayResult.Error("Respons server tidak valid.")
            GatewayResult.Success(
                QrisCharge(
                    orderId = orderId,
                    qrisImageUrl = map["qrisImageUrl"] as? String,
                    qrString = map["qrString"] as? String,
                    amount = amountRupiah,
                    status = QrisChargeStatus.PENDING,
                    expiresAtMillis = (map["expiresAtMillis"] as? Number)?.toLong()
                        ?: (System.currentTimeMillis() + 5 * 60_000),
                )
            )
        } catch (e: Exception) {
            GatewayResult.Error(e.message ?: "Gagal membuat tagihan QRIS. Pastikan internet aktif.")
        }
    }

    /** Dengarkan status pembayaran realtime dari dokumen yang ditulis webhook Midtrans lewat
     * Cloud Function `midtransNotification` — bukan polling, murni Firestore snapshot listener. */
    fun observeChargeStatus(orderId: String): Flow<QrisChargeStatus> = callbackFlow {
        val registration = firestore.collection("payment_status").document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val statusStr = snapshot?.getString("status") ?: "PENDING"
                trySend(runCatching { QrisChargeStatus.valueOf(statusStr) }.getOrDefault(QrisChargeStatus.PENDING))
            }
        awaitClose { registration.remove() }
    }

    private fun maskMerchantId(id: String): String =
        if (id.length <= 4) "****" else "****" + id.takeLast(4)
}
