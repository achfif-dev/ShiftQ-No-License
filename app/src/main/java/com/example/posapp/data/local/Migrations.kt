package com.example.posapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrasi resmi Room, menggantikan `.fallbackToDestructiveMigration()` yang sebelumnya dipakai
 * (lihat DatabaseModule) — database sudah dipakai di instalasi nyata (versi 9), jadi migrasi
 * destruktif akan MENGHAPUS seluruh data toko (produk, transaksi, stok, piutang) begitu skema
 * berubah lagi. Mulai dari sini, setiap kenaikan versi WAJIB punya Migration eksplisit di sini.
 *
 * v9 -> v10:
 * 1. Tambah kolom `users.pinSalt` (nullable) untuk migrasi hash PIN dari SHA-256 polos ke
 *    PBKDF2WithHmacSHA256 bergaram (lihat UserRepository). Baris lama (pinSalt masih NULL)
 *    tetap bisa login seperti biasa lalu otomatis di-upgrade begitu login berikutnya berhasil.
 * 2. Tambah unique index pada `transactions.invoiceNumber`. Sebelum index dibuat, bereskan dulu
 *    kemungkinan duplikat lama (instalasi yang sudah lama berjalan dengan generator invoice
 *    lama yang cuma presisi detik) dengan memberi sufiks pada baris duplikat ke-2 dst., supaya
 *    migrasi tidak gagal/crash di tengah data produksi yang sudah ada.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN pinSalt TEXT DEFAULT NULL")

        val dupeInvoices = mutableListOf<String>()
        db.query("SELECT invoiceNumber FROM transactions GROUP BY invoiceNumber HAVING COUNT(*) > 1").use { cursor ->
            while (cursor.moveToNext()) {
                dupeInvoices.add(cursor.getString(0))
            }
        }
        dupeInvoices.forEach { invoiceNumber ->
            db.query(
                SimpleSQLiteQuery(
                    "SELECT id FROM transactions WHERE invoiceNumber = ? ORDER BY id ASC",
                    arrayOf(invoiceNumber)
                )
            ).use { idCursor ->
                var n = 0
                while (idCursor.moveToNext()) {
                    n++
                    if (n == 1) continue // baris pertama (paling lama) tetap pakai invoice number asli
                    val id = idCursor.getLong(0)
                    db.execSQL(
                        "UPDATE transactions SET invoiceNumber = invoiceNumber || '-DUP$n' WHERE id = $id"
                    )
                }
            }
        }

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_invoiceNumber ON transactions(invoiceNumber)"
        )
    }
}

/**
 * v10 -> v11: Fitur Retur/Refund & Void Transaksi.
 * 1. Kolom baru di `transactions`: `status` (COMPLETED/VOIDED), `returnedAmount` (akumulasi
 *    nominal yang sudah diretur), `voidedByName` & `voidedAt` (jejak audit void).
 * 2. Tabel baru `transaction_returns` (header retur/void) & `transaction_return_items`
 *    (rincian item yang diretur per baris retur) — lihat ReturnEntity.kt untuk penjelasan model.
 * Baris transaksi lama otomatis dianggap status='COMPLETED' & returnedAmount=0 (DEFAULT), jadi
 * tidak ada data yang berubah maknanya untuk transaksi yang sudah ada.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN returnedAmount REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN voidedByName TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN voidedAt INTEGER DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transactionId INTEGER NOT NULL,
                isVoid INTEGER NOT NULL,
                reason TEXT NOT NULL,
                refundAmount REAL NOT NULL,
                refundMethod TEXT DEFAULT NULL,
                processedByName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transaction_returns_transactionId ON transaction_returns(transactionId)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_return_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnId INTEGER NOT NULL,
                transactionItemId INTEGER NOT NULL,
                productId INTEGER NOT NULL,
                variantId INTEGER DEFAULT NULL,
                quantityReturned INTEGER NOT NULL,
                restocked INTEGER NOT NULL,
                FOREIGN KEY(returnId) REFERENCES transaction_returns(id) ON DELETE CASCADE,
                FOREIGN KEY(transactionItemId) REFERENCES transaction_items(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transaction_return_items_returnId ON transaction_return_items(returnId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transaction_return_items_transactionItemId ON transaction_return_items(transactionItemId)"
        )
    }
}

/**
 * v11 -> v12: Fitur Role Manager & Audit Log.
 * - Role MANAGER tidak butuh migrasi (kolom `role` di `users` sudah TEXT sejak awal, MANAGER
 *   cuma nilai string baru yang valid — lihat Converters.fromUserRole/toUserRole).
 * - Tabel baru `audit_logs`: append-only, sengaja TANPA foreign key ke tabel mana pun (lihat
 *   AuditLogEntity.kt) supaya jejak aktivitas tidak pernah ikut terhapus oleh CASCADE apa pun.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                actorName TEXT NOT NULL,
                actorRole TEXT NOT NULL,
                action TEXT NOT NULL,
                description TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v12 -> v13: Program Poin Loyalitas & modul Pemasok/Pesanan Pembelian.
 * 1. Kolom baru `customers.loyaltyPoints` (default 0) — saldo poin, lihat CustomerEntity.
 * 2. Tabel baru `suppliers` (nama, telepon, alamat) — dipakai draf PO stok tipis.
 * 3. Kolom baru `products.supplierId` (nullable, TANPA FK di level DB — lihat catatan di
 *    ProductEntity.kt) + index biasa untuk query "produk stok tipis per pemasok" tetap cepat.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN loyaltyPoints INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT,
                address TEXT,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("ALTER TABLE products ADD COLUMN supplierId INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_supplierId ON products(supplierId)")
    }
}

/**
 * v13 -> v14: Tabel `cash_movements` — kas masuk/keluar non-penjualan per shift (mis. ambil kas
 * untuk keperluan lain, belanja dadakan pakai uang laci, setoran tambahan modal di tengah shift).
 * Dipakai oleh ShiftRepository.closeShift untuk mengoreksi "kas seharusnya" di luar hasil
 * penjualan tunai murni — sebelum ini, pergerakan semacam itu sama sekali tidak tercatat sehingga
 * selisih kas saat tutup shift bisa salah tanpa sebab yang jelas bagi kasir/pemilik.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                shiftId INTEGER NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                reason TEXT NOT NULL,
                createdByName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(shiftId) REFERENCES shifts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_cash_movements_shiftId ON cash_movements(shiftId)"
        )
    }
}

/**
 * v14 -> v15: Promo/Diskon otomatis, Tahan Transaksi (Park Sale), & due date BON/Piutang.
 * 1. Tabel baru `promos` — aturan diskon otomatis (minimal belanja, per kategori, atau beli-X-
 *    gratis-Y), diterapkan otomatis di kasir lewat PromoEngine, lihat PromoEntity.kt.
 * 2. Tabel baru `parked_sales` — transaksi yang "ditahan" kasir (pelanggan belum selesai
 *    memilih/bayar) supaya kasir bisa melayani pelanggan lain dulu, lihat ParkedSaleEntity.kt.
 * 3. Kolom baru `transaction_payments.dueDate` (nullable) — hanya diisi untuk baris metode BON,
 *    dipakai fitur Reminder Piutang Jatuh Tempo. Baris lama (sebelum fitur ini) tetap NULL dan
 *    tidak pernah dianggap jatuh tempo — konsisten dengan cara migrasi lain di file ini yang
 *    tidak mengubah makna data yang sudah ada.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS promos (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                percent REAL NOT NULL DEFAULT 0.0,
                minPurchase REAL NOT NULL DEFAULT 0.0,
                categoryId INTEGER DEFAULT NULL,
                productId INTEGER DEFAULT NULL,
                buyQty INTEGER NOT NULL DEFAULT 1,
                getFreeQty INTEGER NOT NULL DEFAULT 1,
                startAt INTEGER DEFAULT NULL,
                endAt INTEGER DEFAULT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS parked_sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemsData TEXT NOT NULL,
                note TEXT DEFAULT NULL,
                customerId INTEGER DEFAULT NULL,
                customerName TEXT DEFAULT NULL,
                tableTag TEXT DEFAULT NULL,
                transactionDiscount REAL NOT NULL DEFAULT 0.0,
                itemCount INTEGER NOT NULL DEFAULT 0,
                estimatedTotal REAL NOT NULL DEFAULT 0.0,
                createdByName TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("ALTER TABLE transaction_payments ADD COLUMN dueDate INTEGER DEFAULT NULL")
    }
}
