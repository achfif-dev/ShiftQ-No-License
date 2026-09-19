package com.example.posapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Data pelanggan, dipakai terutama untuk pelacakan piutang (transaksi "Bon"). Saldo piutang
 * TIDAK disimpan sebagai kolom di sini (rawan drift kalau lupa disinkronkan) — selalu dihitung
 * langsung dari data transaksi asli oleh [com.example.posapp.data.repository.CustomerRepository]:
 * total pembayaran ber-metode BON pada transaksi milik pelanggan ini, dikurangi total pelunasan
 * yang sudah dicatat di [DebtPaymentEntity].
 */
@Entity(tableName = "customers", indices = [Index("phone")])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Saldo poin loyalitas saat ini (v13). Ditambah otomatis saat checkout (lihat
     * CheckoutUseCase) sebesar total/StoreProfile.loyaltyRupiahPerPoint, dan dikurangi saat
     * pelanggan menukar poin sebagai potongan pembayaran. Disimpan sebagai kolom langsung
     * (bukan dihitung ulang dari histori) karena penukaran poin adalah aksi tersendiri yang
     * tidak punya jejak "transaksi asli" seperti piutang. */
    val loyaltyPoints: Long = 0
)

/** Satu pelunasan (cicilan/lunas) piutang seorang pelanggan. */
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    // v16: cocok dengan CREATE INDEX index_debt_payments_shiftId di MIGRATION_15_16.
    indices = [Index("customerId"), Index("shiftId")]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Double,
    val note: String? = null,
    /** v16: true = uang pelunasan diterima TUNAI dan masuk ke laci kasir, jadi harus menambah
     * "kas seharusnya" saat tutup shift. false = transfer/QRIS, tidak menyentuh laci.
     * Sebelumnya pelunasan piutang tidak pernah ikut dihitung di rekonsiliasi shift sama sekali,
     * sehingga kas fisik selalu tampak BERLEBIH sebesar pelunasan tunai yang diterima hari itu. */
    // defaultValue WAJIB sama persis dengan literal "DEFAULT 1" di ALTER TABLE (MIGRATION_15_16).
    @ColumnInfo(defaultValue = "1")
    val isCash: Boolean = true,
    /** v16: shift yang aktif saat pelunasan dicatat (null = tidak ada shift terbuka / baris lama). */
    val shiftId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
