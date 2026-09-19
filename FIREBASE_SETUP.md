# Setup Firebase untuk Sinkronisasi Cloud & Multi-Cabang

Ada 3 fitur di **Pengaturan > Multi-Cabang** yang memakai setup di file ini:

- **Sinkronisasi Cloud** — kirim ringkasan omzet harian (total omzet + jumlah transaksi, BUKAN
  detail transaksi/produk/pelanggan) cabang ini ke [Firestore](https://firebase.google.com/docs/firestore).
- **Ringkasan Semua Cabang** — lihat gabungan omzet hari ini dari semua cabang yang sudah
  Sinkronisasi Cloud, dari satu HP.
- **Cek Stok Semua Cabang** — kirim & lihat katalog produk + stok + harga jual cabang lain
  secara *read-only* (bukan omzet), disinkron tiap beberapa jam.

Semua fitur ini **nonaktif secara default** dan aplikasi tetap 100% offline-first tanpa langkah
di bawah ini — hanya perlu dilakukan kalau kamu memang ingin memakai salah satu fitur multi-cabang
di atas. Aplikasi ini **tidak memakai sistem lisensi/aktivasi apa pun** — ketiga fitur di atas
langsung tersedia begitu Firebase disiapkan, tanpa kode aktivasi.

> **Kode Grup Sinkronisasi menggantikan lisensi sebagai kunci pengelompokan cabang.** Supaya dua
> toko yang tidak saling terkait tidak bisa saling membaca data satu sama lain, tiap instalasi
> otomatis mendapat **Kode Grup Sinkronisasi** (10 karakter acak, dibuat sekali, terlihat di
> **Pengaturan > Multi-Cabang > Sinkronisasi Cloud**). Cabang-cabang yang memakai kode YANG SAMA
> PERSIS akan tergabung dalam satu grup dan bisa saling melihat data lewat fitur-fitur di atas —
> cukup salin kode dari cabang utama ke cabang lain (mirip membagikan password Wi-Fi), tidak
> perlu aktivasi/pembayaran apa pun. Lihat bagian "Kenapa perlu kode grup" di bawah.

## 1. Buat proyek Firebase

1. Buka [console.firebase.google.com](https://console.firebase.google.com), klik **Add project**.
2. Beri nama bebas (mis. "POS App Toko Saya"), lanjutkan sampai selesai (Google Analytics boleh
   dimatikan, tidak dipakai fitur ini).

> Kalau kamu **sudah** setup Firebase project untuk Payment Gateway Otomatis (lihat
> `PAYMENT_GATEWAY_SETUP.md`), pakai project Firebase yang SAMA — jangan buat project baru.
> Fitur cloud sync sengaja dibuat menumpang di satu project yang sama supaya `mintSyncToken`
> (langkah 5) tidak butuh setup ganda.

## 2. Daftarkan aplikasi Android

1. Di dashboard proyek, klik ikon Android untuk **Add app**.
2. **Android package name**: isi persis sesuai `applicationId` di `app/build.gradle.kts`
   (repo ini sekarang pakai: `id.shiftq.posapp` — cek `app/build.gradle.kts` kalau sudah diganti lagi).
3. Nickname app & SHA-1 boleh dikosongkan (tidak dipakai fitur ini).
4. **Download `google-services.json`**.

## 3. Taruh file konfigurasi

Upload file `google-services.json` yang barusan didownload ke folder **`app/`** di repo ini
(sejajar dengan `app/build.gradle.kts`), lewat GitHub web UI (Add file > Upload files).

> Build sengaja dibuat mendeteksi keberadaan file ini secara otomatis (lihat komentar di
> `app/build.gradle.kts`) — kalau file belum ada, build tetap sukses dan fitur cloud sync
> otomatis nonaktif (bukan error).

> **Penting:** plugin `com.google.gms.google-services` yang memproses file ini HARUS sudah
> terdaftar di `build.gradle.kts` (root project, bukan `app/build.gradle.kts`). Kalau repo ini
> kamu dapat dari sebelum perbaikan ini ditambahkan, build akan gagal dengan error
> `Plugin with id 'com.google.gms.google-services' not found` begitu `google-services.json`
> di-upload — root `build.gradle.kts` sudah diperbaiki untuk mendaftarkan plugin ini di baris
> `plugins { ... }`, jadi cukup pastikan repo kamu sudah pakai versi terbaru.

## 4. Aktifkan Firestore & Authentication

Di Firebase Console, proyek yang tadi dibuat:

1. **Build > Firestore Database > Create database** — pilih mode **production**, lokasi server
   terdekat (mis. `asia-southeast2` untuk Indonesia — HARUS sama dengan region yang dipakai
   Cloud Functions, lihat `FUNCTIONS_REGION` di `functions/index.js`).
2. **Build > Authentication > Get started > Sign-in method > Anonymous** — aktifkan.
   (Dipakai `PaymentGatewayRepository` untuk sign-in anonim biasa. Sesi Cloud Sync/Cek Stok
   Semua Cabang sendiri memakai custom token — lihat langkah 5 — BUKAN anonim, tapi provider
   Anonymous tetap harus aktif untuk fitur Payment Gateway.)

## 4b. Aktifkan App Check — WAJIB, DAN HARUS SEBELUM DEPLOY FUNCTIONS

Sejak v16, semua Cloud Function callable (`mintSyncToken`, `saveGatewayCredentials`,
`createQrisCharge`, `attachInvoiceToOrder`) dideploy dengan `enforceAppCheck: true`. Tanpa App
Check, siapa pun yang mengekstrak `google-services.json` dari APK bisa memanggil fungsi-fungsi
itu langsung dari skrip.

**Urutannya tidak boleh dibalik.** Kalau functions dideploy sebelum App Check terdaftar, seluruh
fitur Cloud Sync, Cek Stok Semua Cabang, dan QRIS Otomatis akan DITOLAK server di semua device
sampai pendaftaran selesai. Fitur POS inti (kasir, stok, laporan, cetak struk) tetap jalan
normal karena semuanya offline.

1. Firebase Console > **App Check** > tab **Apps** > pilih aplikasi Android ini.
2. Pilih provider **Play Integrity**, lalu daftarkan.
3. Masih di App Check, tab **APIs**: biarkan **Cloud Functions** dalam mode *Unenforced* dulu,
   pantau tab Metrics beberapa jam sampai terlihat permintaan yang "Verified" masuk dari device
   nyata. Baru setelah itu ubah ke *Enforced*.
4. Untuk uji coba dengan build debug: token debug muncul di Logcat saat app pertama dibuka —
   daftarkan manual di App Check > Apps > menu titik tiga > **Manage debug tokens**.

Kalau device lama pernah sinkron sebelum v16, permintaan pertamanya akan mendaftarkan ulang
identitas device secara otomatis (tidak ada yang perlu dilakukan pemilik toko). Kalau muncul
pesan *"device sudah terdaftar di instalasi lain"* — itu terjadi kalau data aplikasi pernah
dihapus — gunakan tombol **Buat Ulang ID Cabang** di Pengaturan > Sinkronisasi Cloud.

## 5. Deploy Cloud Functions & Firestore Rules lewat GitHub Actions

Rules TIDAK ditempel manual lewat Firebase Console — sekarang jadi satu paket dengan
`functions/index.js` (berisi `mintSyncToken`, dipanggil `TenantAuthProvider.kt` untuk
mengeluarkan custom token bergrup dari Kode Grup Sinkronisasi) dan `firestore.rules` (sudah
disiapkan di repo ini, mensyaratkan custom claim `customerGroupId` yang cuma didapat dari token
hasil `mintSyncToken` — lihat komentar "TEMUAN KEAMANAN" di `firestore.rules` untuk detail
lengkap kenapa perlu serumit ini).

1. Kalau belum pernah, siapkan 2 GitHub Secrets di **Settings → Secrets and variables →
   Actions**: `FIREBASE_SERVICE_ACCOUNT_JSON` (isi lengkap file JSON service account — Firebase
   Console > Project Settings > Service Accounts > Generate new private key, role minimal
   "Firebase Admin") dan `FIREBASE_PROJECT_ID` (Project ID Firebase, bukan nama tampilan).
2. Jalankan lewat tab **Actions → Deploy Cloud Functions & Firestore Rules → Run workflow** —
   workflow `deploy_firebase.yml` ini men-deploy `functions/index.js` & `firestore.rules`
   sekaligus dari browser, tanpa perlu Node/firebase-tools/terminal di komputer sendiri. Otomatis
   jalan lagi tiap kali ada push ke `main` yang mengubah folder `functions/` atau
   `firestore.rules`.
3. Deploy ini akan menerbitkan semua Cloud Functions (termasuk `mintSyncToken`) DAN
   `firestore.rules` sekaligus — tidak perlu menyalin rule manual ke Firebase Console.

### Kenapa perlu Kode Grup Sinkronisasi (ringkas)

Tanpa pengelompokan apa pun, semua pengguna app ini (dari toko manapun) yang sign-in ke fitur
Cloud Sync akan setara tanpa identitas kepemilikan — artinya dua toko yang sama sekali tidak
berhubungan tapi sama-sama mengaktifkan Sinkronisasi Cloud/Cek Stok Semua Cabang bisa saling
membaca omzet dan katalog satu sama lain. Untuk mencegah ini, setiap device meminta token ke
`mintSyncToken` dengan membawa Kode Grup Sinkronisasi-nya, dan token itu membawa
`customerGroupId` (hash dari kode tersebut) yang mengunci cabang-cabang HANYA bisa saling lihat
data cabang lain dengan kode grup yang SAMA. Tidak ada verifikasi pembelian/lisensi di langkah
ini — kode grup berfungsi seperti password Wi-Fi, bukan seperti lisensi berbayar.

## 6. Build & jalankan

Push/upload perubahan (termasuk `google-services.json` yang baru ditambahkan) ke GitHub, jalankan
Actions build APK seperti biasa. Setelah APK terinstall di tiap cabang:

1. Buka **Pengaturan > Multi-Cabang > Sinkronisasi Cloud** di device cabang utama, salin
   **Kode Grup Sinkronisasi** yang tampil di sana (dibuat otomatis).
2. Di device cabang lain, buka layar yang sama, tempel kode yang sama persis lalu simpan —
   sekarang cabang itu tergabung ke grup yang sama.
3. Isi **Nama Cabang** yang berbeda-beda per device (mis. "Cabang Kelapa Gading", "Cabang Bekasi").
4. Nyalakan toggle **Aktifkan Sinkronisasi Cloud** di tiap device.
5. Setelah ada transaksi, cek **Pengaturan > Multi-Cabang > Ringkasan Semua Cabang** — omzet hari
   itu dari semua cabang dengan kode grup yang sama yang sudah sinkron akan muncul digabung.
6. Kalau juga mau ikut membagikan & melihat stok cabang lain, buka **Cek Stok Semua Cabang** dan
   nyalakan toggle di sana (terpisah dari toggle Sinkronisasi Cloud).

## Batasan versi ini

- Yang disinkronkan **hanya ringkasan omzet harian** (total omzet + jumlah transaksi per hari per
  cabang) lewat Sinkronisasi Cloud, dan **katalog+stok+harga jual** (bukan pelanggan/piutang) lewat
  Cek Stok Semua Cabang. Manajemen produk, stok, dan piutang tetap sepenuhnya per-device/per-cabang
  sebagai sumber kebenaran utama — data cloud murni salinan untuk dilihat, bukan pusat kendali.
- Ringkasan Semua Cabang menampilkan data **hari ini** saja (belum ada pilihan rentang tanggal).
- Cabang-cabang dengan **Kode Grup Sinkronisasi** yang SAMA otomatis dianggap satu grup/toko
  (bisa saling lihat data) — kalau kamu punya beberapa toko yang benar-benar terpisah (bukan
  cabang dari satu toko yang sama), pastikan masing-masing memakai kode yang BERBEDA (biarkan
  kode acak bawaan, jangan disamakan) supaya datanya tidak tercampur.
- Ini fondasi awal — kalau ke depan kamu butuh sinkronisasi penuh (produk/stok terpusat, riwayat
  multi-hari, dsb.), itu pengembangan lanjutan di atas fondasi ini.
