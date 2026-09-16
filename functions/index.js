/**
 * Cloud Functions backend untuk aplikasi Kasir POS ini.
 *
 * INI DIDEPLOY SEKALI OLEH DEVELOPER (bukan per pelanggan) ke SATU proyek Firebase milik
 * developer/platform. Semua toko/pelanggan yang pakai aplikasi ini terhubung ke Cloud Functions
 * yang SAMA, tapi datanya terisolasi per outletId/customerGroupId masing-masing lewat Firestore
 * (bukan file konfigurasi terpisah per pelanggan) — jadi developer TIDAK PERNAH perlu build APK
 * khusus atau setting manual per pelanggan.
 *
 * Aplikasi ini TIDAK PAKAI sistem lisensi/aktivasi — semua fitur terbuka begitu app terpasang,
 * dijual sekali bayar (lewat Play Store/APK) tanpa mekanisme pengecekan pembelian di sisi server.
 *
 * Fungsi di sini SENGAJA tidak menyentuh data penjualan/produk pelanggan sama sekali (kecuali
 * `outlet_catalog` yang memang didesain untuk fitur "Cek Stok Semua Cabang" opt-in) — payment
 * gateway hanya meneruskan permintaan charge ke Midtrans pakai kredensial milik toko itu sendiri.
 */

const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.firestore();

const REGION = "asia-southeast2";

// ============================================================================================
// CLOUD SYNC — token tenant-scoped untuk fitur "Sinkronisasi Cloud" & "Cek Stok Semua Cabang".
// ============================================================================================

/**
 * TEMUAN KEAMANAN (audit ulang — kebocoran data lintas-pelanggan/cross-tenant di Cloud Sync):
 * sebelumnya CloudSyncRepository.kt/OutletCatalogSyncRepository.kt sign-in ANONIM biasa lewat
 * `auth.signInAnonymously()` — semua pengguna app ini, dari toko mana pun, mendapat token
 * Firebase Auth yang SETARA dan tidak membawa info kepemilikan grup toko apa pun. Karena backend
 * Cloud Sync hidup di SATU proyek Firebase yang dipakai bersama semua pelanggan, firestore.rules
 * untuk outlet_summaries/outlet_catalog cuma bisa mensyaratkan `request.auth != null` untuk READ —
 * bukan kepemilikan grup toko — sehingga dua toko yang tidak saling terkait tapi sama-sama
 * mengaktifkan toggle itu bisa saling membaca omzet harian, katalog produk, stok, dan harga jual
 * satu sama lain.
 *
 * Perbaikan: mem-mint custom token Firebase Auth dengan custom claim `customerGroupId` =
 * SHA-256(groupCode) — [groupCode] adalah kode 8-karakter yang dibuat otomatis sekali per
 * instalasi (lihat StoreProfileRepository.ensureSyncGroupCode di app) dan bisa disalin manual ke
 * cabang lain supaya bergabung ke grup yang sama. TIDAK ADA verifikasi pembelian/lisensi di sini
 * — siapa pun yang tahu kode ini (mis. pemilik toko yang sengaja membagikannya ke cabang sendiri)
 * bisa bergabung ke grupnya, sama seperti kode Wi-Fi. Satu grup per kode, jadi kalau satu toko
 * punya beberapa cabang dengan kode yang sama, cabang-cabang itu MEMANG dimaksudkan saling bisa
 * lihat data cabang lain di grup mereka sendiri (tujuan awal fitur "Cek Stok Semua Cabang"), tapi
 * TIDAK BISA melihat grup toko lain (selama kode tidak dibagikan ke pihak luar).
 * firestore.rules lalu mensyaratkan `request.auth.token.customerGroupId` dokumen yang
 * dibaca/ditulis sama dengan token pemanggil.
 *
 * uid token dibuat DETERMINISTIK dari deviceId (`sync_<deviceId>`, bukan uid anonim acak) supaya
 * `ownerUid` yang tersimpan di outlet_summaries/outlet_catalog tetap konsisten lintas sesi/
 * reinstall app di device yang sama — lihat TenantAuthProvider.kt untuk alasan token ini
 * sengaja dipakai di FirebaseApp KEDUA (terpisah dari sesi anonim default
 * PaymentGatewayRepository).
 */
exports.mintSyncToken = onCall({ region: REGION }, async (request) => {
  const { groupCode, deviceId } = request.data || {};
  if (!groupCode || !deviceId) {
    throw new HttpsError("invalid-argument", "groupCode dan deviceId wajib diisi.");
  }

  const customerGroupId = crypto.createHash("sha256").update(groupCode).digest("hex");
  const uid = `sync_${deviceId}`;
  const customToken = await admin.auth().createCustomToken(uid, { customerGroupId });
  return { customToken };
});

// ============================================================================================
// PAYMENT GATEWAY (MIDTRANS) — kredensial MILIK TOKO SENDIRI, disimpan server-side saja.
// ============================================================================================

/** Simpan/hapus kredensial Midtrans milik satu outlet. Koleksi `gateway_credentials` TIDAK
 * boleh readable/writable langsung dari client — lihat firestore.rules.
 *
 * TEMUAN KEAMANAN PENTING (ditambahkan saat audit ulang): sebelum ada pengecekan `ownerUid` di
 * bawah, SIAPA PUN yang tahu (atau mendapat lewat cara apa pun — log, screenshot dukungan,
 * dsb.) outletId milik toko lain bisa memanggil fungsi ini dan MENIMPA kredensial Midtrans toko
 * tersebut dengan kredensial milik penyerang — akibatnya semua pembayaran QRIS Otomatis toko
 * korban diam-diam mengalir ke akun Midtrans penyerang. outletId memang UUID v4 (praktis tidak
 * bisa ditebak brute-force), tapi tetap bukan rahasia yang didesain untuk menahan kebocoran
 * (mis. tersimpan di log crash, terlihat di URL, dsb.) — jadi TETAP harus diverifikasi
 * kepemilikannya, bukan cukup mengandalkan "sulit ditebak". Sekarang: percobaan PERTAMA
 * menyimpan kredensial untuk suatu outletId mengikat `ownerUid` (identitas akun anonim Firebase
 * Auth device itu — lihat PaymentGatewayRepository.kt yang sekarang wajib sign-in dulu sebelum
 * memanggil fungsi ini); percobaan berikutnya HANYA diterima kalau `request.auth.uid` sama
 * dengan `ownerUid` yang tersimpan. */
exports.saveGatewayCredentials = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sesi tidak valid, coba lagi setelah pastikan internet aktif.");
  }
  const { outletId, merchantId, serverKey, clientKey, isProduction, clear } = request.data || {};
  if (!outletId) throw new HttpsError("invalid-argument", "outletId wajib diisi.");

  const ref = db.collection("gateway_credentials").doc(outletId);
  const existing = await ref.get();
  if (existing.exists && existing.data().ownerUid && existing.data().ownerUid !== request.auth.uid) {
    throw new HttpsError(
      "permission-denied",
      "outletId ini sudah terhubung ke device/akun lain. Kalau ini toko Anda sendiri dan " +
        "berpindah device, hubungi developer untuk melepas ikatan lama."
    );
  }

  if (clear) {
    await ref.delete().catch(() => {});
    return { ok: true };
  }
  if (!merchantId || !serverKey || !clientKey) {
    throw new HttpsError("invalid-argument", "merchantId, serverKey, clientKey wajib diisi.");
  }
  await ref.set({
    ownerUid: request.auth.uid,
    merchantId,
    serverKey,
    clientKey,
    isProduction: !!isProduction,
    updatedAt: Date.now(),
  });
  return { ok: true };
});

function midtransBaseUrl(isProduction) {
  return isProduction ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
}

/** Buat charge QRIS dinamis resmi Midtrans untuk satu order. Lihat dokumentasi Midtrans Core API
 * (https://docs.midtrans.com/reference/qris) untuk field response terbaru — sesuaikan parsing
 * `actions` di bawah kalau Midtrans mengubah format responsnya.
 *
 * Sama seperti saveGatewayCredentials di atas: WAJIB `request.auth` dan WAJIB cocok dengan
 * `ownerUid` tersimpan — tanpa ini siapa pun yang tahu outletId toko lain bisa membuat transaksi
 * QRIS atas nama toko tersebut di dashboard Midtrans mereka (bukan mencuri uang langsung karena
 * tetap butuh orang yang benar-benar scan & bayar, tapi bisa dipakai untuk spam/mengacaukan
 * riwayat transaksi toko korban). */
exports.createQrisCharge = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sesi tidak valid, coba lagi setelah pastikan internet aktif.");
  }
  const { outletId, orderId, amount } = request.data || {};
  if (!outletId || !orderId || !amount) {
    throw new HttpsError("invalid-argument", "outletId, orderId, amount wajib diisi.");
  }
  const credSnap = await db.collection("gateway_credentials").doc(outletId).get();
  if (!credSnap.exists) {
    throw new HttpsError("failed-precondition", "Payment gateway belum dihubungkan untuk toko ini.");
  }
  const cred = credSnap.data();
  if (cred.ownerUid && cred.ownerUid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Device ini tidak terhubung dengan outlet tersebut.");
  }
  const auth = Buffer.from(`${cred.serverKey}:`).toString("base64");

  const response = await fetch(`${midtransBaseUrl(cred.isProduction)}/v2/charge`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Basic ${auth}`,
    },
    body: JSON.stringify({
      payment_type: "qris",
      transaction_details: { order_id: orderId, gross_amount: Math.round(amount) },
      qris: { acquirer: "gopay" },
    }),
  });
  const json = await response.json();
  if (!response.ok) {
    throw new HttpsError("internal", json.status_message || "Midtrans menolak permintaan charge.");
  }

  const qrAction = (json.actions || []).find((a) => a.name === "generate-qr-code");

  // Catat status awal supaya PaymentGatewayRepository.observeChargeStatus langsung punya
  // dokumen untuk didengarkan (dari PENDING), diperbarui lagi oleh webhook di bawah.
  // ownerUid = uid device yang membuat charge ini (SAMA dengan cred.ownerUid, sudah dicek cocok
  // di atas) -- dipakai firestore.rules supaya HANYA device toko ini sendiri yang bisa membaca
  // dokumen ini kembali, menutup celah tebak-order_id lintas toko (lihat TEMUAN KEAMANAN di
  // firestore.rules).
  await db.collection("payment_status").doc(orderId).set({
    outletId,
    amount,
    status: "PENDING",
    createdAt: Date.now(),
    ownerUid: request.auth.uid,
  });

  return {
    qrisImageUrl: qrAction ? qrAction.url : null,
    qrString: json.qr_string || null,
    // TEMUAN (audit ulang, khusus QRIS dinamis): SEBELUMNYA nilai di sini 5 menit, TAPI custom
    // expiry TIDAK PERNAH benar-benar dikirim ke Midtrans (tidak ada field `custom_expiry` di
    // body /v2/charge di atas) -- jadi 5 menit itu HANYA angka yang ditampilkan ke app, sama
    // sekali tidak mencerminkan kapan QR itu SUNGGUH kedaluwarsa di sisi Midtrans. Default resmi
    // Midtrans untuk GoPay/QRIS adalah 15 MENIT (docs.midtrans.com/docs/gopay-qris-pos-integration)
    // -- disamakan di sini supaya timer yang dilihat kasir/pelanggan cocok dengan kenyataan.
    // SENGAJA TIDAK mengirim custom_expiry sendiri ke Midtrans (meski API mendukungnya) karena
    // dokumentasi resmi mereka eksplisit memperingatkan: expiry di bawah 15 menit TIDAK
    // dijamin diproses tepat waktu oleh scheduler internal Midtrans ("not recommended to set
    // expiry below 15 minutes") -- jadi 15 menit default mereka justru pilihan paling aman.
    expiresAtMillis: Date.now() + 15 * 60 * 1000,
  };
});

/**
 * Webhook HTTP yang didaftarkan di dashboard Midtrans (Settings > Configuration > Payment
 * Notification URL) mengarah ke URL Cloud Function ini. Midtrans POST setiap ada perubahan
 * status transaksi. Endpoint ini publik (tidak pakai onCall) karena dipanggil server Midtrans,
 * bukan dari app — verifikasi keaslian notifikasi lewat `signature_key` (SHA512 dari
 * order_id+status_code+gross_amount+ServerKey, lihat docs.midtrans.com/reference/notification).
 */
exports.midtransNotification = onRequest({ region: REGION }, async (req, res) => {
  try {
    const body = req.body || {};
    const { order_id, status_code, gross_amount, signature_key, transaction_status } = body;
    if (!order_id) return res.status(400).send("missing order_id");

    const statusSnap = await db.collection("payment_status").doc(order_id).get();
    if (!statusSnap.exists) return res.status(404).send("unknown order");
    const outletId = statusSnap.data().outletId;
    const credSnap = await db.collection("gateway_credentials").doc(outletId).get();
    if (!credSnap.exists) return res.status(404).send("unknown outlet");
    const serverKey = credSnap.data().serverKey;

    const expectedSignature = crypto
      .createHash("sha512")
      .update(`${order_id}${status_code}${gross_amount}${serverKey}`)
      .digest("hex");
    if (expectedSignature !== signature_key) {
      return res.status(403).send("invalid signature");
    }

    const mapped =
      transaction_status === "settlement" || transaction_status === "capture"
        ? "SETTLED"
        : transaction_status === "expire"
        ? "EXPIRED"
        : transaction_status === "cancel" || transaction_status === "deny"
        ? "CANCELLED"
        : "PENDING";

    // TEMUAN (audit ulang, khusus QRIS dinamis): signature_key di atas membuktikan notifikasi ini
    // ASLI dari Midtrans (tidak bisa dipalsukan tanpa serverKey), TAPI belum ada yang mencocokkan
    // gross_amount di notifikasi dengan `amount` yang dicatat sendiri saat charge dibuat
    // (createQrisCharge). Kalau angkanya berbeda -- untuk sebab apa pun, termasuk kemungkinan
    // integrasi/adjustment di sisi Midtrans di masa depan -- app TIDAK BOLEH diam-diam
    // menganggap transaksi lunas penuh hanya karena status="settlement". Ini murni pertahanan
    // berlapis (defense-in-depth) sesuai rekomendasi resmi Midtrans, BUKAN memperbaiki celah yang
    // sudah terbukti bisa dieksploitasi lewat alur normal (pelanggan tidak bisa mengubah nominal
    // QRIS dinamis merchant-presented secara sepihak).
    const expectedAmount = statusSnap.data().amount;
    const paidAmount = Number(gross_amount);
    const amountMatches = expectedAmount == null || Math.round(expectedAmount) === Math.round(paidAmount);
    const finalStatus = mapped === "SETTLED" && !amountMatches ? "AMOUNT_MISMATCH" : mapped;
    if (mapped === "SETTLED" && !amountMatches) {
      console.error(
        `midtransNotification: gross_amount notifikasi (${paidAmount}) tidak cocok dengan amount tersimpan (${expectedAmount}) untuk order ${order_id}`
      );
    }

    await db.collection("payment_status").doc(order_id).set(
      { status: finalStatus, updatedAt: Date.now(), rawStatus: transaction_status, paidAmount },
      { merge: true }
    );
    res.status(200).send("ok");
  } catch (e) {
    console.error("midtransNotification error", e);
    res.status(500).send("error");
  }
});
