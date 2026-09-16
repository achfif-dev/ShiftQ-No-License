package com.example.posapp.di

import android.content.Context
import androidx.room.Room
import com.example.posapp.data.local.AppDatabase
import com.example.posapp.data.local.dao.AuditLogDao
import com.example.posapp.data.local.dao.CashMovementDao
import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.dao.CustomerDao
import com.example.posapp.data.local.dao.ExpenseDao
import com.example.posapp.data.local.dao.ParkedSaleDao
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.PromoDao
import com.example.posapp.data.local.dao.ShiftDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // Migrasi resmi wajib untuk setiap kenaikan versi (lihat Migrations.kt) — database
            // ini sudah dipakai di instalasi nyata, migrasi destruktif akan menghapus seluruh
            // data toko (produk, transaksi, stok, piutang) begitu skema berubah.
            .addMigrations(
                com.example.posapp.data.local.MIGRATION_9_10,
                com.example.posapp.data.local.MIGRATION_10_11,
                com.example.posapp.data.local.MIGRATION_11_12,
                com.example.posapp.data.local.MIGRATION_12_13,
                com.example.posapp.data.local.MIGRATION_13_14,
                com.example.posapp.data.local.MIGRATION_14_15
            )
            // Hanya untuk skenario downgrade (mis. pasang ulang APK versi lama secara tidak
            // sengaja) — kasus langka yang aman diberi fallback destruktif karena versi
            // skema yang lebih baru tidak mungkin dibaca oleh kode yang lebih lama.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideStockAdjustmentDao(db: AppDatabase): StockAdjustmentDao = db.stockAdjustmentDao()

    @Provides
    fun provideProductVariantDao(db: AppDatabase): ProductVariantDao = db.productVariantDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    fun provideSupplierDao(db: AppDatabase): com.example.posapp.data.local.dao.SupplierDao = db.supplierDao()

    @Provides
    fun provideCashMovementDao(db: AppDatabase): CashMovementDao = db.cashMovementDao()

    @Provides
    fun providePromoDao(db: AppDatabase): PromoDao = db.promoDao()

    @Provides
    fun provideParkedSaleDao(db: AppDatabase): ParkedSaleDao = db.parkedSaleDao()
}
