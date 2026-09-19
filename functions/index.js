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
exports.mintSyncToken = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  const { groupCode, deviceId, deviceSecret } = request.data || {};
  if (!groupCode || !deviceId) {
    throw new HttpsError("invalid-argument", "groupCode dan deviceId wajib diisi.");
  }
  if (typeof deviceId !== "string" || deviceId.length < 8 || deviceId.length > 128) {
    throw new HttpsError("invalid-argument", "deviceId tidak valid.");
  }

  const customerGroupId = sha256Hex(groupCode);

  // TEMUAN KEAMANAN (audit) — PEMALSUAN IDENTITAS CABANG LAIN:
  // Versi sebelumnya membentuk uid LANGSUNG dari deviceId yang dikirim client
  // (`sync_<deviceId>`), tanpa request.auth dan tanpa App Check. Sementara deviceId itu sendiri
  // = outletId = doc ID di outlet_catalog, yang BISA DIBACA setiap anggota grup. Jadi siapa pun
  // yang tahu kode grup (atau satu cabang nakal di dalam grup) bisa meminta token dengan uid
  // cabang lain, lalu menimpa katalog/stok/omzet cabang tersebut — `ownerUid` yang dipakai
  // firestore.rules praktis kehilangan makna.
  //
  // Dua lapis perbaikan:
  // 1. `enforceAppCheck: true` — hanya build resmi aplikasi ini yang boleh memanggil fungsi ini,
  //    bukan siapa pun yang mengekstrak google-services.json dari APK lalu memanggil langsung.
  //    WAJIB: daftarkan App Check (Play Integrity) di Firebase Console sebelum deploy ini,
  //    kalau tidak SEMUA client akan ditolak. Lihat FIREBASE_SETUP.md.
  // 2. deviceId sekarang DIDAFTARKAN sekali: pada permintaan PERTAMA, SERVER yang membuat
  //    rahasia acak 32 byte dan menyimpan hash-nya; permintaan berikutnya untuk deviceId yang
  //    sama WAJIB membuktikan rahasia itu. Client tidak lagi bebas menentukan identitas —
  //    deviceId yang bocor tanpa rahasianya tidak bisa dipakai apa-apa.
  const ref = db.collection("sync_devices").doc(deviceId);
  const snap = await ref.get();

  let effectiveSecret;
  if (!snap.exists) {
    effectiveSecret = crypto.randomBytes(32).toString("hex");
    try {
      // create() (bukan set()) supaya dua pendaftaran bersamaan untuk deviceId yang sama tidak
      // saling menimpa — yang kalah masuk ke cabang "sudah terdaftar" di bawah.
      await ref.create({
        secretHash: sha256Hex(effectiveSecret),
        customerGroupId,
        createdAt: Date.now(),
      });
    } catch (e) {
      throw new HttpsError("aborted", "Pendaftaran device bentrok, coba lagi.");
    }
  } else {
    const data = snap.data();
    // MIGRASI DEVICE LAMA (penting saat rilis perbaikan ini): dokumen sync_devices baru ada
    // sejak v16. Device yang SUDAH pernah sinkron sebelum ini tidak memiliki dokumen sama
    // sekali, jadi mereka masuk ke cabang "belum terdaftar" di atas dan mendaftar mulus.
    // Yang di bawah ini menangani kasus dokumen ADA TAPI belum punya secretHash (mis. dibuat
    // oleh versi transisi) — perlakukan sebagai pendaftaran pertama, jangan kunci pemiliknya
    // sendiri di luar. Dokumen yang SUDAH punya secretHash tetap wajib membuktikan rahasianya.
    if (!data.secretHash) {
      effectiveSecret = crypto.randomBytes(32).toString("hex");
      await ref.set(
        { secretHash: sha256Hex(effectiveSecret), customerGroupId, createdAt: Date.now() },
        { merge: true }
      );
      const uidMigrated = `sync_${deviceId}`;
      const tokenMigrated = await admin.auth().createCustomToken(uidMigrated, { customerGroupId });
      return { customToken: tokenMigrated, deviceSecret: effectiveSecret };
    }
    if (!deviceSecret || sha256Hex(String(deviceSecret)) !== data.secretHash) {
      throw new HttpsError(
        "permission-denied",
        "Device ini sudah terdaftar di instalasi lain. Kalau ini device Anda sendiri dan data " +
          "aplikasi pernah dihapus, buat ulang ID cabang dari Pengaturan > Sinkronisasi Cloud."
      );
    }
    if (data.customerGroupId !== customerGroupId) {
      // deviceId tidak boleh berpindah grup diam-diam: kalau bisa, satu device yang keluar dari
      // grup A masih membawa uid yang sama ke grup B dan bisa menimpa dokumen lama grup A.
      throw new HttpsError("permission-denied", "Device ini terdaftar di grup sinkronisasi lain.");
    }
    effectiveSecret = deviceSecret;
  }

  const uid = `sync_${deviceId}`;
  const customToken = await admin.auth().createCustomToken(uid, { customerGroupId });
  // deviceSecret dikembalikan HANYA supaya client bisa menyimpannya setelah pendaftaran pertama;
  // di panggilan berikutnya nilainya sama dengan yang sudah dipegang client.
  return { customToken, deviceSecret: effectiveSecret };
});

function sha256Hex(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

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
exports.saveGatewayCredentials = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sesi tidak valid, coba lagi setelah pastikan internet aktif.");
  }
  const { outletId, merchantId, serverKey, clientKey, isProduction, clear } = request.data || {};
  if (!outletId) throw new HttpsError("invalid-argument", "outletId wajib diisi.");

  const ref = db.collection("gateway_credentials").doc(outletId);
  const existing = await ref.get();
  if (existing.exists && existing.data().ownerUid && existing.data().ownerUid !== request.auth.uid) {
    // TEMUAN (audit) — TOKO TERKUNCI DARI KREDENSIALNYA SENDIRI: PaymentGatewayRepository
    // sign-in ANONIM, dan uid anonim HILANG setiap kali data app dibersihkan / app dipasang
    // ulang / HP diganti. Setelah itu ownerUid lama tidak akan pernah cocok lagi dan pesan
    // errornya sendiri menyuruh "hubungi developer" — tiket dukungan yang pasti datang untuk
    // produk yang dijual ke banyak toko.
    //
    // Jalur rebind mandiri: pemilik toko yang memang memegang Server Key Midtrans yang SAMA
    // boleh mengambil alih ikatan outletId-nya sendiri. Pembuktian kepemilikan = Server Key,
    // karena itulah rahasia yang hanya ada di dashboard Midtrans milik toko tersebut.
    // Dibandingkan dengan timingSafeEqual supaya tidak bocor lewat perbedaan waktu respons.
    const sameServerKey =
      !clear &&
      typeof serverKey === "string" &&
      typeof existing.data().serverKey === "string" &&
      serverKey.length === existing.data().serverKey.length &&
      crypto.timingSafeEqual(Buffer.from(serverKey), Buffer.from(existing.data().serverKey));
    if (!sameServerKey) {
      throw new HttpsError(
        "permission-denied",
        "outletId ini sudah terhubung ke device/akun lain. Kalau ini toko Anda sendiri dan " +
          "berpindah device, masukkan ulang Server Key Midtrans yang sama persis untuk " +
          "memindahkan ikatannya ke device ini."
      );
    }
    console.log(`saveGatewayCredentials: rebind outlet ${outletId} ke uid baru ${request.auth.uid}`);
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
exports.createQrisCharge = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
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
    // TEMUAN (audit): dokumen payment_status TIDAK PERNAH dihapus, jadi biaya penyimpanan
    // Firestore naik terus selamanya untuk dokumen yang tidak berguna lagi setelah beberapa
    // menit. `expireAt` di bawah dipakai Firestore TTL policy — AKTIFKAN sekali di Firebase
    // Console (Firestore > TTL > koleksi `payment_status`, field `expireAt`), lihat
    // PAYMENT_GATEWAY_SETUP.md. 7 hari, cukup lama untuk penelusuran sengketa pembayaran.
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + 7 * 24 * 60 * 60 * 1000),
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
 * TEMUAN (audit) — orderId QRIS Otomatis tidak terhubung ke invoice: orderId dibuat SEBELUM
 * checkout selesai (`TEMP-<millis>-<rand>`), sementara nomor invoice final baru lahir setelahnya.
 * Akibatnya merekonsiliasi dashboard Midtrans dengan Riwayat Penjualan harus dilakukan manual
 * satu per satu berdasarkan jam & nominal. Fungsi ini dipanggil app SETELAH checkout berhasil
 * untuk menempelkan nomor invoice final ke dokumen payment_status yang bersangkutan.
 *
 * Fail-soft di sisi client: kegagalan di sini tidak boleh membatalkan transaksi yang sudah
 * tersimpan — paling buruk rekonsiliasinya kembali manual seperti sebelumnya.
 */
exports.attachInvoiceToOrder = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sesi tidak valid.");
  }
  const { orderId, invoiceNumber } = request.data || {};
  if (!orderId || !invoiceNumber) {
    throw new HttpsError("invalid-argument", "orderId dan invoiceNumber wajib diisi.");
  }
  const ref = db.collection("payment_status").doc(orderId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Order tidak ditemukan.");
  if (snap.data().ownerUid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Order ini bukan milik device ini.");
  }
  await ref.set({ invoiceNumber: String(invoiceNumber) }, { merge: true });
  return { ok: true };
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
