package com.example.posapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * Menguji migrasi Room terhadap snapshot skema SUNGGUHAN di `app/schemas/` — bukan terhadap
 * dugaan. Folder skema sudah lama di-commit dan sourceSet androidTest sudah diarahkan ke sana
 * (lihat app/build.gradle.kts), tapi testnya sendiri tidak pernah ada, jadi separuh persiapannya
 * menganggur.
 *
 * Yang paling penting diuji di sini bukan "migrasi jalan tanpa error", melainkan bahwa data
 * lama SELAMAT dan kolom baru terisi masuk akal. Migrasi 15->16 menyentuh lapisan uang
 * (harga beli snapshot & potongan promo), jadi kesalahan di sini merusak laba seluruh riwayat
 * penjualan toko secara diam-diam.
 *
 * CATATAN CI: workflow saat ini hanya menjalankan `testDebugUnitTest` (JVM). Test ini butuh
 * emulator/perangkat (`connectedDebugAndroidTest`) — tambahkan step-nya di android_build.yml
 * kalau ingin ikut berjalan otomatis.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate15To16_menjaga_data_lama_dan_mengisi_snapshot_harga_beli() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                """
                INSERT INTO products (id, name, sku, purchasePrice, sellPrice, stock, unit,
                    lowStockThreshold, hasVariants, discountPercent, isActive, createdAt, updatedAt)
                VALUES (1, 'Kopi Sachet', 'SKU-1', 1500.0, 2500.0, 50, 'pcs', 5, 0, 0.0, 1, 0, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transactions (id, invoiceNumber, subtotal, taxPercent, taxAmount,
                    discountAmount, total, paymentMethod, amountPaid, changeAmount, createdAt,
                    status, returnedAmount)
                VALUES (1, 'INV-LAMA-001', 5000.0, 0.0, 0.0, 0.0, 5000.0, 'CASH', 5000.0, 0.0,
                    1700000000000, 'COMPLETED', 0.0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transaction_items (id, transactionId, productId, productNameSnapshot,
                    priceSnapshot, quantity, unitSnapshot, itemDiscount)
                VALUES (1, 1, 1, 'Kopi Sachet', 2500.0, 2, 'pcs', 0.0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        db.query("SELECT promoDiscount, purchasePriceSnapshot FROM transaction_items WHERE id = 1").use { c ->
            assertTrue("baris item lama harus selamat melewati migrasi", c.moveToFirst())
            assertEquals(0.0, c.getDouble(0), 0.001)
            // Backfill dari harga beli yang berlaku saat migrasi — perkiraan terbaik yang
            // tersedia, dan persis sama dengan perilaku lama (JOIN hidup ke products).
            assertEquals(1500.0, c.getDouble(1), 0.001)
        }

        db.query("SELECT shiftId FROM transactions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("transaksi lama tidak boleh diklaim shift mana pun", c.isNull(0))
        }

        // Kolom baru di tabel piutang: pelunasan lama dianggap tunai (perilaku paling wajar untuk
        // toko kecil) dan tidak dimiliki shift mana pun.
        db.query("PRAGMA table_info(debt_payments)").use { c ->
            val columns = mutableListOf<String>()
            while (c.moveToNext()) columns.add(c.getString(1))
            assertTrue(columns.contains("isCash"))
            assertTrue(columns.contains("shiftId"))
        }
        db.close()
    }
}
