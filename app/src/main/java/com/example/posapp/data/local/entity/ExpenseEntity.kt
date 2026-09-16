package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Periode pemakaian satu Beban Usaha. [daysInPeriod] dipakai untuk mem-prorata nominal beban
 * ke rentang tanggal laporan mana pun (mis. Sewa Toko Rp3.000.000/BULANAN -> ~Rp100.000/hari,
 * lalu dikalikan jumlah hari pada periode laporan yang dipilih Admin).
 */
enum class ExpensePeriod(val label: String, val daysInPeriod: Double) {
    HARIAN("Harian", 1.0),
    MINGGUAN("Mingguan", 7.0),
    BULANAN("Bulanan", 30.0),
    TAHUNAN("Tahunan", 365.0)
}

/**
 * Satu pos Beban Usaha (mis. Sewa Toko, Gaji Karyawan, Listrik & Air) yang dikonfigurasi bebas
 * oleh Admin sesuai kondisi tokonya masing-masing, dipakai untuk menghitung Laba Bersih
 * (Laba Kotor - Total Beban Usaha) di Laporan. Nonaktifkan ([isActive] = false) untuk berhenti
 * memperhitungkan beban tanpa kehilangan riwayat/nominalnya.
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val period: ExpensePeriod = ExpensePeriod.BULANAN,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
