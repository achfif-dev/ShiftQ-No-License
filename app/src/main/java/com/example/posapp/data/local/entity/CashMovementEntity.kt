package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CashMovementType { IN, OUT }

/**
 * Pergerakan kas fisik di laci yang terjadi DI LUAR transaksi penjualan/pembayaran — misalnya
 * pemilik mengambil kas untuk keperluan pribadi, kasir mengeluarkan tunai untuk belanja
 * kebutuhan toko dadakan (galon, parkir, dll.), atau setoran tambahan modal ke laci di tengah
 * shift. Tanpa mencatat ini, [com.example.posapp.data.repository.ShiftRepository.closeShift]
 * tidak bisa menghitung "kas seharusnya" dengan akurat karena laci bisa berkurang/bertambah
 * tanpa jejak apa pun di data transaksi.
 *
 * Selalu terikat ke satu [ShiftEntity] ([shiftId]) — bukan ke rentang waktu — supaya tidak
 * ambigu kalau suatu hari ada lebih dari satu shift yang dibuka di tanggal yang sama, dan
 * supaya pergerakan yang tercatat hanya dihitung untuk shift yang benar saat tutup kasir.
 * [reason] wajib diisi (divalidasi di [com.example.posapp.data.repository.ShiftRepository])
 * karena ini uang yang keluar/masuk laci tanpa transaksi penjualan — jejaknya harus jelas untuk
 * audit, sama seperti tindakan lain yang tercatat di [AuditLogEntity].
 */
@Entity(
    tableName = "cash_movements",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("shiftId")]
)
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val type: CashMovementType,
    val amount: Double,
    val reason: String,
    val createdByName: String,
    val createdAt: Long = System.currentTimeMillis()
)
