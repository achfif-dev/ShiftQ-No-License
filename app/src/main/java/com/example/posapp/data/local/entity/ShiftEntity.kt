package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ShiftStatus { OPEN, CLOSED }

/**
 * Satu shift kasir: dari "Buka Kasir" (mencatat kas awal fisik di laci) sampai "Tutup Kasir"
 * (kas akhir dihitung ulang secara fisik oleh kasir, dibandingkan dengan kas yang seharusnya
 * ada menurut sistem = kas awal + total penjualan tunai selama shift).
 *
 * [expectedCash] & [difference] BARU diisi saat shift ditutup (dihitung oleh ShiftRepository
 * dari data transaksi real, bukan input manual) — supaya selisih kas benar-benar mencerminkan
 * kejujuran fisik uang di laci, bukan angka yang bisa diketik bebas oleh kasir.
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cashierId: Long?,
    val cashierName: String,       // snapshot nama kasir (tetap ada meski akun user dihapus nanti)
    val startCash: Double,         // kas awal fisik yang dihitung & diinput kasir saat buka shift
    val status: ShiftStatus = ShiftStatus.OPEN,
    val expectedCash: Double? = null, // startCash + total penjualan tunai selama shift (dihitung sistem)
    val actualCash: Double? = null,   // kas akhir fisik yang dihitung & diinput kasir saat tutup shift
    val difference: Double? = null,   // actualCash - expectedCash (negatif = kurang, positif = lebih)
    val note: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)
