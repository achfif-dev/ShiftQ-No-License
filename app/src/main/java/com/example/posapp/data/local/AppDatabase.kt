package com.example.posapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
import com.example.posapp.data.local.dao.SupplierDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.dao.UserDao
import com.example.posapp.data.local.entity.AuditLogEntity
import com.example.posapp.data.local.entity.CashMovementEntity
import com.example.posapp.data.local.entity.CashMovementType
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.DebtPaymentEntity
import com.example.posapp.data.local.entity.ExpenseEntity
import com.example.posapp.data.local.entity.ExpensePeriod
import com.example.posapp.data.local.entity.ParkedSaleEntity
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.PromoEntity
import com.example.posapp.data.local.entity.PromoType
import com.example.posapp.data.local.entity.ShiftEntity
import com.example.posapp.data.local.entity.ShiftStatus
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import com.example.posapp.data.local.entity.SupplierEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
import com.example.posapp.data.local.entity.TransactionReturnEntity
import com.example.posapp.data.local.entity.TransactionReturnItemEntity
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole

class Converters {
    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromExpensePeriod(value: ExpensePeriod): String = value.name

    @TypeConverter
    fun toExpensePeriod(value: String): ExpensePeriod = ExpensePeriod.valueOf(value)

    @TypeConverter
    fun fromShiftStatus(value: ShiftStatus): String = value.name

    @TypeConverter
    fun toShiftStatus(value: String): ShiftStatus = ShiftStatus.valueOf(value)

    @TypeConverter
    fun fromCashMovementType(value: CashMovementType): String = value.name

    @TypeConverter
    fun toCashMovementType(value: String): CashMovementType = CashMovementType.valueOf(value)

    @TypeConverter
    fun fromPromoType(value: PromoType): String = value.name

    @TypeConverter
    fun toPromoType(value: String): PromoType = PromoType.valueOf(value)
}

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        StockAdjustmentEntity::class,
        ProductVariantEntity::class,
        UserEntity::class,
        TransactionPaymentEntity::class,
        ExpenseEntity::class,
        ShiftEntity::class,
        CustomerEntity::class,
        DebtPaymentEntity::class,
        TransactionReturnEntity::class,
        TransactionReturnItemEntity::class,
        AuditLogEntity::class,
        SupplierEntity::class,
        CashMovementEntity::class,
        PromoEntity::class,
        ParkedSaleEntity::class
    ],
    version = 15, // v15: promos, parked_sales, transaction_payments.dueDate (lihat MIGRATION_14_15)
    // exportSchema = true: mulai v10, setiap build menyimpan snapshot skema JSON ke app/schemas/
    // (lihat room.schemaLocation di app/build.gradle.kts). WAJIB commit folder schemas/ ke Git.
    // Ini yang memungkinkan migrasi berikutnya (v10 -> v11, dst.) diuji otomatis dengan
    // MigrationTestHelper terhadap skema versi sebelumnya yang SUNGGUHAN, bukan cuma dugaan.
    // Riwayat sebelum v10 tidak tersedia (dulu exportSchema=false) — MIGRATION_9_10 sudah
    // divalidasi manual terhadap struktur entity v9 yang tercatat di komentar Migrations.kt.
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun productVariantDao(): ProductVariantDao
    abstract fun userDao(): UserDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    abstract fun customerDao(): CustomerDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun supplierDao(): SupplierDao
    abstract fun cashMovementDao(): CashMovementDao
    abstract fun promoDao(): PromoDao
    abstract fun parkedSaleDao(): ParkedSaleDao

    companion object {
        const val DATABASE_NAME = "pos_database"
    }
}
