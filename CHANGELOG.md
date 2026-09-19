# Changelog

Semua perubahan penting pada proyek ini dicatat di file ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/), versioning mengikuti [Semantic Versioning](https://semver.org/).

## [Unrilis]
### Keamanan & Akuntansi (audit lapisan uang, v16 — DB 15 -> 16)
> **URUTAN DEPLOY WAJIB DIBACA.** Cloud Functions di rilis ini memakai `enforceAppCheck: true`.
> Daftarkan aplikasi di **Firebase Console > App Check (provider Play Integrity) LEBIH DULU**,
> baru `firebase deploy --only functions`. Urutan terbalik akan menolak seluruh permintaan
> Cloud Sync & QRIS Otomatis dari semua device sampai pendaftaran selesai. Aktifkan juga TTL
> Firestore untuk koleksi `payment_status` pada field `expireAt`.

#### Kritis
- **Potongan promo per-baris tidak pernah tersimpan.** `CheckoutUseCase` hanya menulis
  `line.discount`; `line.promoDiscount` dari `PromoEngine` hilang total. Akibatnya Σ(nilai item)
  ≠ `transactions.total`, laba kotor kelebihan hitung sebesar nilai promo, struk cetak-ulang
  salah nominal, dan retur bisa me-refund lebih besar dari yang dibayar pelanggan. Ditambahkan
  kolom `transaction_items.promoDiscount` (kolom sendiri, bukan digabung ke `itemDiscount`,
  supaya laporan tetap bisa memisahkan diskon kasir dari promo otomatis). Pemetaan keranjang ->
  item dipisah jadi fungsi murni `CheckoutUseCase.buildTransactionItems` + test regresi.
- **Laba kotor memakai harga beli HIDUP, bukan snapshot.** `getSalesSummary` JOIN ke
  `products.purchasePrice`, sehingga memperbarui harga beli hari ini diam-diam mengubah laba
  bulan-bulan lalu. Ditambahkan `transaction_items.purchasePriceSnapshot` (dibekukan saat
  checkout; baris lama di-backfill dari harga beli saat migrasi berjalan).
- **Laba kotor mengabaikan seluruh diskon tingkat transaksi.** `discountAmount` (diskon manual
  transaksi + penukaran poin loyalitas + promo min-belanja) tidak pernah dikurangkan, jadi Laba
  Bersih SELALU lebih optimis dari kenyataan. Sekarang dikurangkan. `totalRevenue` juga berubah
  jadi **omzet tanpa PPN** (PPN adalah uang titipan negara, bukan pendapatan toko), dengan porsi
  retur dipotong proporsional.
- **`mintSyncToken` bisa dipakai memalsukan identitas cabang lain.** uid dibentuk langsung dari
  `deviceId` kiriman client, tanpa auth & tanpa App Check — sementara `deviceId` = `outletId`
  yang bisa dibaca anggota grup mana pun lewat `outlet_catalog`. Sekarang: `enforceAppCheck`,
  plus `deviceId` didaftarkan sekali dengan rahasia acak buatan SERVER yang wajib dibuktikan di
  permintaan berikutnya, dan `deviceId` tidak boleh berpindah grup. Device lama (belum punya
  dokumen `sync_devices`) mendaftar mulus tanpa terkunci. Disediakan tombol **Buat Ulang ID
  Cabang** di Pengaturan > Sinkronisasi Cloud sebagai pemulihan mandiri.
- **Restore backup bisa menghancurkan data tanpa jalan pulang.** `restore()` langsung menimpa
  file DB tanpa salinan pengaman, tanpa verifikasi hasil dekripsi, dan tanpa cek versi skema
  (restore backup dari APK lebih baru = `fallbackToDestructiveMigrationOnDowngrade` menghapus
  semuanya). Ditambah: salinan `.prerestore` + rollback otomatis, verifikasi header SQLite, dan
  penolakan `user_version` yang lebih tinggi dari skema build ini. `BackupCrypto.decrypt`
  dipindah dari `CipherInputStream` ke `cipher.doFinal()` eksplisit — pada GCM, CipherInputStream
  bisa MEMOTONG stream tanpa exception di sebagian runtime Android (unit test di JVM tetap
  lolos), yang berarti password salah menimpa database asli dengan sampah. `read()` diganti
  `readFully()` untuk salt/IV/magic.

#### Tinggi
- **Void setelah retur sebagian = stok dobel.** `voidTransaction` mengembalikan stok seluruh
  item termasuk yang sudah masuk rak lagi lewat `processReturn(restocked = true)`. Sekarang qty
  yang sudah direstock dikurangi dulu.
- **`refundAmount` tidak divalidasi.** Nominal apa pun diterima; hanya qty yang dicek. Sekarang
  ditolak kalau negatif atau melebihi sisa yang belum diretur (toleransi Rp1 untuk pembulatan).
- **Rekonsiliasi shift bocor di tiga tempat.** Ditambahkan `shiftId` EKSPLISIT di `transactions`,
  `transaction_returns`, dan `debt_payments` (menggantikan penyimpulan dari rentang waktu):
  refund tunai kini DIKURANGKAN dari kas seharusnya, pelunasan piutang tunai DITAMBAHKAN
  (dengan pilihan tunai/non-tunai di dialog Catat Pelunasan), dan transaksi yang terjadi tanpa
  shift terbuka ditampilkan sebagai PERINGATAN di layar tutup shift — bukan diseret paksa ke
  shift orang lain.
- **Kembalian tunai bisa keluar dari pembayaran non-tunai.** Nominal BON/QRIS/Debit bebas
  diketik dan kelebihannya muncul sebagai "Kembalian" — kasir bisa input Bon Rp200rb untuk
  belanja Rp150rb lalu memberi Rp50rb tunai riil. Metode non-tunai kini di-clamp ke sisa tagihan.
- **Toko bisa terkunci dari kredensial Midtrans-nya sendiri** (uid anonim hilang saat clear
  data/reinstall, pesan errornya sendiri berbunyi "hubungi developer"). Ditambahkan jalur rebind
  mandiri: memasukkan ulang Server Key Midtrans yang sama persis memindahkan ikatan ke device
  baru (dibandingkan dengan `timingSafeEqual`).
- **`orderId` QRIS Otomatis tidak terhubung ke invoice.** Ditambahkan callable
  `attachInvoiceToOrder` yang dipanggil setelah checkout berhasil (fail-soft). Dokumen
  `payment_status` kini punya `expireAt` untuk TTL 7 hari — sebelumnya tidak pernah dihapus.

#### Sedang
- Rute `stock` digerbang `Permission.canAccessStock` (ADMIN & MANAGER). Angka bisnis di Laporan
  (omzet, laba kotor, tren harian, produk terlaris) digerbang `canViewSalesAnalytics` **di
  ViewModel** — tidak dimuat sama sekali untuk Kasir. Rute `reports` SENGAJA tidak digerbang
  penuh: Riwayat Penjualan di layar itu adalah tempat Kasir memproses retur.
- Lockout PIN kini menyimpan deadline GANDA (elapsedRealtime + wall-clock). Sebelumnya hanya
  `elapsedRealtime`, yang bisa dilewati instan dengan me-reboot HP.
- App Check (Play Integrity) dipasang di seluruh jalur Firebase.
- Test baru: `PromoEngineTest` (8 kasus), `CheckoutItemMappingTest` (4 kasus, regresi promo
  hilang & snapshot harga beli), `MigrationTest` 15->16 (androidTest — **belum ikut CI**, perlu
  step `connectedDebugAndroidTest` di workflow).
- Perbaikan staleness `observeOverdueDebtors`: `now` dulu dibekukan sekali saat Flow dibuat,
  jadi piutang yang baru lewat tenggat tidak pernah muncul selama layar tetap terbuka.

## [Rilis sebelumnya — belum ditandai versi]
### Keamanan (audit ulang, 2026-09-14)
- **Batas diskon manual Kasir + audit log**: sebelumnya diskon manual per-item/per-transaksi
  bisa diberikan Kasir biasa TANPA batas (sampai 100%, membuat barang "gratis" secara sah di
  sistem) dan TIDAK tercatat di Log Aktivitas sama sekali — beda dari Void/Koreksi/Retur/Shift
  yang semuanya tercatat. Ini celah fraud klasik POS ("sweethearting": kasir beri diskon besar
  ke kenalan/diri sendiri, tetap menagih penuh, lalu mengantongi selisih tunai) yang hanya bisa
  ketahuan tidak langsung lewat selisih kas saat tutup shift. Diperbaiki dengan:
  - `DiscountPolicy` (baru): Kasir dibatasi ke `StoreProfile.maxKasirDiscountPercent` (Admin
    atur di Pengaturan > Profil Toko, default 20%) dari harga baris/subtotal — nilai yang
    diminta melebihi batas otomatis dipangkas (clamp), bukan ditolak total. Admin & Manager
    tidak dibatasi kebijakan ini sama sekali.
  - Setiap transaksi dengan diskon manual (>0) sekarang tercatat ke Log Aktivitas (aksi
    `DISKON_MANUAL`: nominal, persentase dari subtotal, nomor invoice, dan siapa kasirnya).
  - Sekaligus melengkapi UI-nya: fitur diskon manual sebelumnya HANYA ada sebagai fungsi
    ViewModel yang tidak pernah dipanggil dari layar mana pun (dead code) — sekarang ada tombol
    "Diskon" di ringkasan keranjang & tombol diskon per-item di layar Kasir.

### Dihapus (2026-09-16)
- **Sistem Lisensi Anti-Bajakan dihapus total** — lihat entri "Ditambahkan (Fase Bersaing
  Kompetitor)" di bawah untuk desain aslinya. Atas permintaan pemilik project, seluruh sistem
  aktivasi/lisensi dihapus dari client maupun backend (bukan cuma dinonaktifkan) — semua fitur
  yang dulu terkunci (QRIS Otomatis, Sinkronisasi Cloud, Cek Stok Lintas Cabang) sekarang terbuka
  penuh tanpa syarat. Model bisnis sepenuhnya dari cara distribusi app (sekali bayar di Play
  Store/APK), bukan dari mekanisme di dalam kode. Isolasi data multi-cabang di Cloud Sync yang
  semula berbasis `licenseKey` diganti **Kode Grup Sinkronisasi** (dibuat otomatis, disalin
  manual antar cabang) — lihat `README.md` bagian "Model: TANPA LISENSI" & entri "Sistem lisensi
  dihapus total" di riwayat audit README untuk detail lengkap.
- **Bug ikutan (ditemukan & diperbaiki di audit ulang berikutnya)**: workflow
  `deploy_license_functions.yml` sempat ikut terhapus di atas hanya karena namanya mengandung
  "license", padahal isinya men-deploy seluruh `functions/index.js` (termasuk fungsi Cloud
  Sync/Payment Gateway yang masih dipakai) — sempat membuat janji "deploy Cloud Functions tanpa
  terminal" di README jadi tidak valid. Diganti workflow generik `deploy_firebase.yml`.

### Ditambahkan (Fase Bersaing Kompetitor)
- **Sistem Lisensi Anti-Bajakan** *(HISTORIS — sudah DIHAPUS TOTAL, lihat entri "Dihapus" di
  atas; dipertahankan di sini hanya sebagai catatan desain lama)* (self-service): aktivasi lewat kode lisensi yang ditempel
  sendiri oleh pelanggan (tanpa bantuan developer), diverifikasi via tanda tangan RSA-2048 —
  private key hanya ada di Cloud Functions, tidak pernah ikut ke dalam APK. Sekali aktivasi
  online, aplikasi tetap 100% bisa dipakai offline SELAMANYA (lisensi sekali bayar, BUKAN
  langganan — tidak ada tanggal kedaluwarsa atau token yang perlu diperpanjang sama sekali).
  Revalidasi opsional & oportunistik tiap ada internet lewat WorkManager HANYA untuk mendeteksi
  penonaktifan oleh penjual (refund/bajakan), bukan untuk memperpanjang apa pun — device yang
  tidak pernah online lagi tetap aktif selamanya. Developer cukup build SATU APK generik untuk
  semua pelanggan.
- **Payment Gateway QRIS Otomatis (Midtrans)**: setiap toko menghubungkan akun Midtrans sendiri
  (uang masuk langsung ke rekening toko, bukan lewat developer) lewat Pengaturan > Payment
  Gateway — sepenuhnya self-service. Status "Lunas" terkonfirmasi otomatis realtime di layar
  Kasir lewat webhook + Firestore listener, tanpa cek manual. QRIS statis manual tetap tersedia
  sebagai cadangan offline. Lihat `PAYMENT_GATEWAY_SETUP.md`.
- **Printer LAN/WiFi & USB**: `PrinterRepository` kini mendukung 3 jenis koneksi printer thermal
  (Bluetooth/LAN-TCP/USB Host), tidak lagi terbatas Bluetooth saja. Diatur di Pengaturan > Profil
  Toko > Printer Struk, termasuk pilihan lebar kertas 58mm/80mm.
- **Cek Stok Semua Cabang**: perluasan Sinkronisasi Cloud — tiap cabang (opt-in, dari toggle yang
  sama dengan ringkasan omzet) mengirim snapshot katalog produk+stoknya secara berkala, admin
  bisa melihat stok & harga cabang lain secara realtime (read-only, tidak menimpa data lokal
  cabang sendiri) dari Pengaturan > Multi-Cabang > Cek Stok Semua Cabang.
- (Struk digital via WhatsApp sudah ada sejak sebelumnya — lihat entri Ditambahkan di bawah.)

## [Unrilis - riwayat sebelumnya]
### Ditambahkan
- **Retur/Refund & Void Transaksi**: tabel baru `transaction_returns` & `transaction_return_items`
  (migrasi v10 -> v11). Retur boleh diproses Kasir maupun Admin (alasan wajib, per-item bisa
  ditandai layak jual lagi/rusak, bisa bertahap). Void (batalkan transaksi sepenuhnya) khusus
  Admin — menggantikan tombol "Hapus Transaksi" lama yang menghapus permanen; sekarang transaksi
  tetap tersimpan (status VOIDED) untuk audit, hanya dikeluarkan dari perhitungan Laporan.
- **Role Manager**: peran baru di antara Kasir dan Admin (migrasi v11 -> v12, tanpa perubahan
  skema — cuma nilai enum baru). Manager bisa jual & retur seperti Kasir, plus akses Beban Usaha
  & Laba Bersih di Laporan — tapi tetap tidak bisa Void/koreksi transaksi maupun akses
  Pengaturan/Backup/Manajemen Pengguna (murni Admin-only).
- **Log Aktivitas**: tabel baru `audit_logs`, mencatat Void, Retur, Koreksi Transaksi, Tambah/
  Hapus Pengguna, dan Restore Backup (siapa, kapan, alasan). Layar baru di Pengaturan > Log
  Aktivitas (Admin-only). Jejak Restore Backup sengaja disimpan di file teks terpisah
  (`restore_audit_log.txt`), BUKAN di tabel `audit_logs` — karena restore menimpa seluruh file
  database termasuk tabel itu sendiri, jadi jejaknya justru akan ikut hilang kalau disimpan di
  sana (lihat komentar `RestoreAuditLog.kt`).

### Diperbaiki
- Bug lama di query Laporan Omzet (`getSalesSummary`): `SUM(t.total)` ikut ter-JOIN dengan baris
  item transaksi, sehingga omzet transaksi dengan banyak item terhitung berulang kali. Sekarang
  dihitung dari subquery terpisah yang tidak ikut kelipatan oleh JOIN.

### Diubah
- `exportSchema` Room diaktifkan (`true`) + `room.schemaLocation` dikonfigurasi ke `app/schemas/`.
  Mulai sekarang setiap kenaikan versi database menyimpan snapshot skema JSON asli, sehingga
  migrasi berikutnya (v10 -> v11, dst.) bisa diuji otomatis terhadap skema versi sebelumnya yang
  sungguhan (bukan cuma dugaan) memakai `MigrationTestHelper`. Folder `app/schemas/` WAJIB
  ikut di-commit ke Git — jangan masukkan ke `.gitignore`.

## [1.2.0] - 2026-08-26
### Ditambahkan
- **Manajemen Shift Kasir**: buka/tutup kasir dengan rekonsiliasi kas otomatis (kas seharusnya
  dihitung sistem dari data transaksi tunai asli, bukan input manual) — tabel `shifts` baru.
  Saat "Wajibkan Login PIN" aktif, transaksi digerbang: harus ada shift terbuka dulu.
- **Pelanggan & Piutang (Bon)**: metode pembayaran baru `BON` (bisa dicampur/split dengan
  Cash/QRIS/Debit), saldo piutang dihitung otomatis dari transaksi Bon dikurangi pelunasan
  (tabel `customers` + `debt_payments` baru), layar daftar pelanggan + detail piutang + catat
  pelunasan (boleh mencicil).
- **Sinkronisasi Cloud / Multi-Cabang (fondasi)**: kirim ringkasan omzet harian per cabang ke
  Firestore (opsional, nonaktif default, butuh setup Firebase sendiri — lihat `FIREBASE_SETUP.md`),
  layar Ringkasan Semua Cabang untuk pemilik. Sepenuhnya fail-soft: tanpa konfigurasi Firebase,
  app tetap 100% offline seperti biasa.
- Database Room naik ke v9 (v7 Beban Usaha → v8 Shift → v9 Customer/Piutang/kolom `customerId`).

## [1.1.0] - 2026-08-25
### Diperbaiki
- Build gagal: import `Modifier.pointerInput` yang salah paket di `MainActivity.kt`.
- Build gagal: file `domain/auth/Permission.kt` (dipakai untuk role-gating rute Expenses/Settings/Backup) hilang dari repo.
### Ditambahkan
- Unit test untuk logika inti: `Cart` (kalkulasi subtotal/diskon/pajak), `CheckoutValidator`
  (validasi checkout — cart kosong, pembayaran kurang, stok tidak cukup, split payment),
  `QrisUtil.injectAmount` (TLV & CRC16 QRIS dinamis), `BackupCrypto` (round-trip enkripsi backup).
- `applicationId` diganti dari `com.example.posapp` (ditolak Play Store) ke `id.gwg.posapp`.
### Diubah
- Logika validasi checkout dipisah ke `CheckoutValidator` (objek murni tanpa dependensi Room/Hilt)
  supaya bisa diuji unit test tanpa emulator/device.

## [1.0.0] - 2026-08-24
### Ditambahkan
- Rilis awal: Kasir (POS), Manajemen Produk + Varian, Scan Barcode, Cetak Struk Bluetooth ESC/POS,
  QRIS statis→dinamis on-device, Export PDF/Excel, Login PIN multi-user, Backup & Restore terenkripsi,
  Laporan Penjualan + Laba Bersih, Stok & Inventaris, Beban Usaha.
