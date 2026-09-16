package com.example.posapp.data.local.entity

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
    indices = [Index("customerId")]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Double,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
