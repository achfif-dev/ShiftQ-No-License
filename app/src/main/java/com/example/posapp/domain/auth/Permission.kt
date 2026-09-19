package com.example.posapp.domain.auth

import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole

/**
 * Guard peran terpusat (fail-closed) untuk rute-rute sensitif (Expenses, Settings, Backup/Store
 * Profile). Dipakai independen di MainActivity untuk setiap rute, bukan cuma mengandalkan menu
 * yang disembunyikan di UI — supaya deep link atau navigasi langsung tidak bisa menembus guard.
 *
 * Aturan: kalau fitur "Wajibkan Login PIN" nonaktif, app dianggap mode single-user dan semua
 * diizinkan. Kalau aktif, hanya user dengan role ADMIN yang boleh mengakses rute-rute ini.
 */
object Permission {
    // Beban Usaha (Expenses) boleh diakses ADMIN & MANAGER — mencatat pengeluaran operasional
    // toko adalah tugas level pengawasan, bukan cuma pemilik, dan tidak mengubah harga/data
    // transaksi. Tetap tertutup untuk KASIR biasa.
    fun canAccessExpenses(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN || user?.role == UserRole.MANAGER
    }

    // Pengaturan (Profil Toko/pajak, Manajemen Pengguna & PIN, Backup, Cloud Sync, Multi-Outlet)
    // TETAP murni ADMIN-only — MANAGER sengaja TIDAK diberi akses ke sini, sesuai batasan
    // perannya: "bisa lihat laporan & catat pengeluaran, tapi tidak bisa ubah data sensitif".
    fun canAccessSettings(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    fun canManageBackup(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    // Retur barang boleh diproses Kasir maupun Admin — supaya pelanggan tidak perlu menunggu
    // Admin datang cuma untuk retur barang. Alasan retur tetap WAJIB diisi di layer UI/ViewModel
    // untuk akuntabilitas, dan setiap retur mencatat processedByName (lihat TransactionRepository).
    fun canProcessReturn(user: UserEntity?, pinLoginEnabled: Boolean): Boolean = true

    // Void (batalkan transaksi sepenuhnya) HANYA Admin — beda dari retur, void meniadakan
    // transaksi seolah tidak pernah terjadi sama sekali, jadi risikonya lebih besar kalau
    // disalahgunakan (mis. kasir menutupi transaksi yang uangnya sudah diambil).
    fun canVoidTransaction(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    // Koreksi harga/qty/item transaksi yang SUDAH tersimpan (lewat Riwayat Penjualan) — HANYA
    // Admin, risiko yang sama seperti Void: bisa dipakai menutupi kecurangan pada transaksi yang
    // sudah selesai (audit menyeluruh menemukan ReportViewModel.saveTransactionCorrection() tidak
    // pernah memanggil fungsi ini sama sekali — cuma tombol yang disembunyikan di UI untuk
    // non-Admin, celah yang sama seperti void sebelum diperbaiki).
    fun canCorrectTransaction(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    // Manajemen Produk (tambah/ubah/hapus produk & kategori, termasuk harga beli/margin) —
    // level sama seperti Expenses: ADMIN & MANAGER boleh, KASIR TIDAK. Sebelumnya rute
    // "products" tidak digerbang sama sekali (audit 2026-09-06) sehingga kasir bisa melihat
    // & mengubah harga beli/margin toko lewat layar ini. Sekarang digerbang independen di
    // MainActivity, sama seperti Expenses.
    fun canManageProducts(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN || user?.role == UserRole.MANAGER
    }

    // Stok Opname (set stok ke hasil hitung fisik) HANYA Admin — beda dari stok masuk/keluar
    // biasa yang boleh dicatat kasir/manager saat menerima barang. Opname bisa menutupi selisih
    // stok akibat kecurangan/kesalahan, jadi risikonya sama seperti Void Transaksi. Sebelumnya
    // StockViewModel.adjustStock() menerima tipe "OPNAME" dari siapa pun tanpa cek role (audit
    // 2026-09-06).
    fun canPerformStockOpname(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    // Kelola Promo/Diskon otomatis (v15) — level sama seperti Manajemen Produk: mengubah aturan
    // ini memengaruhi margin toko secara luas (bukan cuma satu produk), jadi ADMIN & MANAGER
    // boleh, KASIR tidak. PromoEngine yang MENERAPKAN promo ke keranjang jalan untuk semua kasir
    // (itu bagian normal alur jual-beli, bukan pengaturan) — ini murni guard layar Kelola Promo.
    fun canManagePromos(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN || user?.role == UserRole.MANAGER
    }

    /**
     * Melihat ANGKA BISNIS di layar Laporan: total omzet, laba, tren harian, produk terlaris,
     * dan rekap metode pembayaran. ADMIN & MANAGER saja.
     *
     * CATATAN PENTING — ini SENGAJA bukan gerbang rute. Layar Laporan juga memuat Riwayat
     * Penjualan, dan dari situlah Kasir memproses RETUR (lihat [canProcessReturn] yang memang
     * mengizinkan semua peran). Menggerbangi seluruh rute "reports" — seperti yang biasa
     * disarankan saat audit — akan membuat kasir tidak bisa melayani retur sama sekali tanpa
     * memanggil Admin, regresi operasional yang baru ketahuan setelah dipakai toko. Jadi yang
     * ditutup hanya blok analitiknya, di ViewModel (bukan cuma disembunyikan di UI).
     */
    fun canViewSalesAnalytics(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN || user?.role == UserRole.MANAGER
    }

    /**
     * Layar Stok (stok masuk/keluar/opname + riwayat penyesuaian, termasuk nilai persediaan).
     * ADMIN & MANAGER — setara Manajemen Produk, karena layar ini bisa mengubah jumlah
     * persediaan tanpa melewati transaksi penjualan. Opname sendiri tetap ADMIN-only lewat
     * [canPerformStockOpname]. Sebelumnya rute "stock" tidak digerbang sama sekali.
     */
    fun canAccessStock(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN || user?.role == UserRole.MANAGER
    }

    private fun isAdminOrPinDisabled(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN
    }
}
