# QRIS Otomatis (Payment Gateway Midtrans) — Panduan untuk Pemilik Toko

Fitur ini membuat pembayaran QRIS terkonfirmasi **LUNAS otomatis** di layar Kasir, tanpa kasir
perlu cek manual ke HP/rekening. QRIS statis biasa (Pengaturan > Profil Toko) tetap ada sebagai
cadangan kalau internet mati — fitur ini murni tambahan opsional.

**Uang QRIS masuk LANGSUNG ke rekening/akun Midtrans milik toko sendiri** — developer aplikasi
tidak pernah memegang atau melihat uang maupun kredensial toko.

## Langkah setup (dilakukan sendiri oleh pemilik toko, ± 10 menit)

### 1. Daftar akun Midtrans

1. Buka [dashboard.midtrans.com](https://dashboard.midtrans.com/register), daftar akun bisnis
   (gratis, tidak ada biaya bulanan — Midtrans hanya potong persentase kecil per transaksi
   sukses, cek tarif terbaru di midtrans.com).
2. Lengkapi data bisnis sesuai diminta (bisa mulai dari mode **Sandbox** dulu untuk uji coba
   tanpa perlu verifikasi bisnis lengkap).

### 2. Ambil kredensial API

Di dashboard Midtrans: **Settings > Access Keys**. Catat 3 hal:
- **Merchant ID**
- **Server Key** (mode Sandbox untuk uji coba, atau Production setelah akun diverifikasi)
- **Client Key**

### 3. Masukkan ke aplikasi

Di aplikasi kasir: **Pengaturan > Payment Gateway (QRIS Otomatis)** → isi ketiga kredensial di
atas → pilih mode Sandbox/Produksi sesuai key yang dipakai → **Simpan & Uji Koneksi**.

Kredensial ini dikirim sekali lewat koneksi terenkripsi ke server, TIDAK disimpan di HP.

### 4. Pasang webhook notifikasi (supaya status "Lunas" otomatis realtime)

Di dashboard Midtrans: **Settings > Configuration > Payment Notification URL**, isi dengan:

```
https://asia-southeast2-<PROJECT_ID_FIREBASE>.cloudfunctions.net/midtransNotification
```

Ganti `<PROJECT_ID_FIREBASE>` dengan Project ID Firebase yang dipakai aplikasi ini (lihat
Firebase Console, atau tanya penjual/developer aplikasi kalau tidak tahu — nilai ini SAMA
untuk semua toko yang pakai aplikasi ini, bukan per toko).

### 5. Coba transaksi QRIS Otomatis

Buka Kasir > checkout dengan metode QRIS > tekan **"Buat QRIS Otomatis"**. Scan dengan aplikasi
e-wallet apa pun yang mendukung QRIS. Selesai bayar, status di layar kasir akan berubah jadi
"✓ Pembayaran terkonfirmasi otomatis" dalam beberapa detik — tidak perlu refresh manual.

## Mode Sandbox vs Produksi

- **Sandbox**: transaksi tidak nyata (tidak charge uang asli), untuk latihan/uji coba dulu.
- **Produksi**: transaksi asli, uang beneran masuk ke rekening yang didaftarkan di akun Midtrans.

Ganti dari Sandbox ke Produksi kapan saja lewat layar Pengaturan yang sama, setelah akun
Midtrans selesai diverifikasi Midtrans (biasanya perlu upload dokumen usaha).

## Kalau tidak ingin pakai QRIS Otomatis

Tidak masalah — cukup jangan isi Pengaturan > Payment Gateway. Aplikasi tetap berjalan normal
dengan QRIS statis manual seperti biasa.


## Tambahan v16 (hasil audit)

### Aktifkan TTL Firestore untuk `payment_status`
Dokumen status pembayaran sebelumnya tidak pernah dihapus, jadi biayanya naik terus untuk data
yang tidak berguna lagi setelah beberapa menit. Sejak v16 setiap dokumen membawa field
`expireAt` (7 hari, cukup untuk penelusuran sengketa pembayaran).

Firebase Console > **Firestore Database** > tab **TTL** > *Create policy*:
koleksi `payment_status`, field timestamp `expireAt`.

### Pindah device tanpa menghubungi developer
Kredensial Midtrans terikat ke `ownerUid` device yang pertama kali menyimpannya. Kalau data
aplikasi dihapus / HP diganti, uid anonim lama hilang dan ikatan itu tidak akan cocok lagi.

Sejak v16 tidak perlu lagi menghubungi developer: cukup buka Pengaturan > Payment Gateway di
device baru dan masukkan ulang **Server Key Midtrans yang sama persis**. Server memakai Server
Key itu sendiri sebagai bukti kepemilikan (hanya ada di dashboard Midtrans milik toko tersebut)
lalu memindahkan ikatannya ke device baru.

### App Check
`createQrisCharge` dan `saveGatewayCredentials` sekarang mewajibkan App Check. Ikuti langkah di
FIREBASE_SETUP.md bagian **4b** SEBELUM men-deploy functions.
