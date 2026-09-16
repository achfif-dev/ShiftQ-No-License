package com.example.posapp.data.payment

/**
 * Kredensial payment gateway (Midtrans) MILIK TOKO ITU SENDIRI — bukan milik developer/platform.
 * Setiap toko daftar akun Midtrans sendiri (uang QRIS masuk ke rekening toko itu langsung, bukan
 * lewat developer), lalu tempel Server Key & Client Key di sini secara mandiri lewat
 * Pengaturan > Payment Gateway. Sepenuhnya self-service, tidak perlu developer terlibat sama sekali.
 *
 * PENTING: Server Key TIDAK PERNAH disimpan di device/DataStore/Room. Begitu toko menekan "Simpan
 * & Uji Koneksi", key dikirim SEKALI ke Cloud Function `saveGatewayCredentials` lewat koneksi TLS
 * dan disimpan di Firestore dengan security rules yang menolak SEMUA baca/tulis langsung dari
 * client (hanya bisa dibaca oleh Cloud Function lain yang berjalan dengan Admin SDK). Device hanya
 * menyimpan status ringkas (sudah terhubung / belum) — lihat [PaymentGatewayRepository].
 */
data class GatewayCredentialsInput(
    val merchantId: String,
    val serverKey: String,
    val clientKey: String,
    val isProduction: Boolean,
)

enum class QrisChargeStatus { PENDING, SETTLED, EXPIRED, FAILED, CANCELLED, AMOUNT_MISMATCH }

data class QrisCharge(
    val orderId: String,
    val qrisImageUrl: String?,
    val qrString: String?,
    val amount: Long,
    val status: QrisChargeStatus,
    val expiresAtMillis: Long,
)
