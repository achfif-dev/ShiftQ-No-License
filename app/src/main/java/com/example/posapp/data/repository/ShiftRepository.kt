package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.CashMovementDao
import com.example.posapp.data.local.dao.ShiftDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.entity.CashMovementEntity
import com.example.posapp.data.local.entity.CashMovementType
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ShiftEntity
import com.example.posapp.data.local.entity.ShiftStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class OpenShiftResult {
    data class Success(val shiftId: Long) : OpenShiftResult()
    data class Error(val message: String) : OpenShiftResult()
}

sealed class CloseShiftResult {
    data class Success(
        val expectedCash: Double,
        val difference: Double,
        val breakdown: ShiftPaymentBreakdown
    ) : CloseShiftResult()
    data class Error(val message: String) : CloseShiftResult()
}

sealed class CashMovementResult {
    object Success : CashMovementResult()
    data class Error(val message: String) : CashMovementResult()
}

/**
 * Ringkasan penjualan per metode pembayaran selama satu shift, dipakai di dialog tutup kasir
 * untuk rekonsiliasi menyeluruh — bukan cuma tunai. [cashNet] sudah bersih dari kembalian (lihat
 * [ShiftRepository.closeShift]); [qris]/[debitCredit]/[bon] adalah total tender apa adanya
 * karena metode itu praktiknya tidak menyisakan kembalian dari laci. [bon] ditampilkan terpisah
 * sebagai info piutang — TIDAK ikut menambah kas fisik yang diharapkan ada di laci.
 */
data class ShiftPaymentBreakdown(
    val cashNet: Double,
    val qris: Double,
    val debitCredit: Double,
    val bon: Double,
    val cashInFromMovements: Double,
    val cashOutFromMovements: Double
)

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val transactionDao: TransactionDao,
    private val cashMovementDao: CashMovementDao,
    private val auditLogRepository: AuditLogRepository
) {
    fun observeActiveShift(): Flow<ShiftEntity?> = shiftDao.observeActiveShift()

    fun observeHistory(): Flow<List<ShiftEntity>> = shiftDao.observeHistory()

    fun observeCashMovements(shiftId: Long): Flow<List<CashMovementEntity>> =
        cashMovementDao.observeForShift(shiftId)

    suspend fun openShift(cashierId: Long?, cashierName: String, startCash: Double): OpenShiftResult {
        if (startCash < 0) {
            return OpenShiftResult.Error("Kas awal tidak boleh negatif")
        }
        if (shiftDao.getActiveShift() != null) {
            return OpenShiftResult.Error("Masih ada shift yang belum ditutup")
        }
        val id = shiftDao.insert(
            ShiftEntity(cashierId = cashierId, cashierName = cashierName, startCash = startCash)
        )
        auditLogRepository.log(
            actorName = cashierName,
            actorRole = null,
            action = "OPEN_SHIFT",
            description = "Buka shift, kas awal Rp${startCash.toLong()}"
        )
        return OpenShiftResult.Success(id)
    }

    /**
     * Catat pergerakan kas fisik di laci yang terjadi DI LUAR transaksi penjualan (mis. ambil kas
     * untuk keperluan lain, belanja dadakan pakai uang laci, setoran tambahan modal). Hanya bisa
     * dicatat selagi ada shift aktif — nilainya dipakai saat tutup shift untuk mengoreksi "kas
     * seharusnya" di luar hasil penjualan tunai murni. [reason] wajib diisi untuk jejak audit.
     */
    suspend fun recordCashMovement(
        type: CashMovementType,
        amount: Double,
        reason: String,
        actorName: String
    ): CashMovementResult {
        if (amount <= 0) {
            return CashMovementResult.Error("Nominal harus lebih dari 0")
        }
        if (reason.isBlank()) {
            return CashMovementResult.Error("Alasan wajib diisi")
        }
        val active = shiftDao.getActiveShift()
            ?: return CashMovementResult.Error("Tidak ada shift yang sedang berjalan")

        cashMovementDao.insert(
            CashMovementEntity(
                shiftId = active.id,
                type = type,
                amount = amount,
                reason = reason,
                createdByName = actorName
            )
        )
        val label = if (type == CashMovementType.IN) "Kas masuk" else "Kas keluar"
        auditLogRepository.log(
            actorName = actorName,
            actorRole = null,
            action = "CASH_MOVEMENT",
            description = "$label Rp${amount.toLong()} — $reason"
        )
        return CashMovementResult.Success
    }

    /**
     * Tutup shift yang sedang aktif. [actualCash] adalah hasil hitung fisik kasir di laci —
     * [expectedCash] TIDAK diminta dari kasir, melainkan dihitung sistem dari tiga sumber:
     *
     * 1. Kas awal shift.
     * 2. Penjualan tunai BERSIH selama shift — dihitung PER TRANSAKSI dari data transaksi asli:
     *    (tender tunai transaksi itu - kembalian transaksi itu).coerceAtLeast(0.0), lalu
     *    dijumlah. Dihitung per transaksi (bukan SUM kotor lalu dikurangi SUM kembalian secara
     *    global) supaya kembalian satu transaksi tidak pernah "menutupi" kekurangan hitungan di
     *    transaksi lain — tiap transaksi mengoreksi dirinya sendiri.
     *    PERBAIKAN dari versi lama: sebelumnya kembalian yang sudah diberikan ke pembeli tetap
     *    dihitung seolah masih ada di laci, sehingga shift selalu tampak "kas kurang" walau kasir
     *    sudah bekerja benar.
     * 3. Pergerakan kas non-penjualan (kas masuk/keluar, lihat [recordCashMovement]) yang
     *    tercatat untuk shift ini — sebelumnya sama sekali tidak ada tempat mencatat ini, padahal
     *    laci bisa berubah tanpa transaksi penjualan sama sekali (mis. pemilik ambil kas).
     *
     * Turut dihitung breakdown per metode pembayaran ([ShiftPaymentBreakdown]) supaya kasir/
     * pemilik bisa merekonsiliasi QRIS & Debit/Kredit juga, bukan cuma tunai.
     */
    suspend fun closeShift(actualCash: Double, note: String? = null, actorName: String? = null): CloseShiftResult {
        if (actualCash < 0) {
            return CloseShiftResult.Error("Kas akhir tidak boleh negatif")
        }
        val active = shiftDao.getActiveShift()
            ?: return CloseShiftResult.Error("Tidak ada shift yang sedang berjalan")

        val endedAt = System.currentTimeMillis()

        val cashRows = transactionDao.getCashPaymentsWithChangeInRange(active.startedAt, endedAt)
        val netCashSales = cashRows.sumOf { (it.cashAmount - it.changeAmount).coerceAtLeast(0.0) }

        val methodTotals = transactionDao.getPaymentMethodTotalsInRange(active.startedAt, endedAt)
        val qris = methodTotals.firstOrNull { it.method == PaymentMethod.QRIS }?.total ?: 0.0
        val debitCredit = methodTotals.firstOrNull { it.method == PaymentMethod.DEBIT_CREDIT }?.total ?: 0.0
        val bon = methodTotals.firstOrNull { it.method == PaymentMethod.BON }?.total ?: 0.0

        val cashIn = cashMovementDao.getMovementTotal(active.id, CashMovementType.IN)
        val cashOut = cashMovementDao.getMovementTotal(active.id, CashMovementType.OUT)

        val expectedCash = active.startCash + netCashSales + cashIn - cashOut
        val difference = actualCash - expectedCash

        shiftDao.update(
            active.copy(
                status = ShiftStatus.CLOSED,
                expectedCash = expectedCash,
                actualCash = actualCash,
                difference = difference,
                note = note,
                endedAt = endedAt
            )
        )
        if (actorName != null) {
            auditLogRepository.log(
                actorName = actorName,
                actorRole = null,
                action = "CLOSE_SHIFT",
                description = "Tutup shift, selisih kas Rp${difference.toLong()}"
            )
        }
        return CloseShiftResult.Success(
            expectedCash = expectedCash,
            difference = difference,
            breakdown = ShiftPaymentBreakdown(
                cashNet = netCashSales,
                qris = qris,
                debitCredit = debitCredit,
                bon = bon,
                cashInFromMovements = cashIn,
                cashOutFromMovements = cashOut
            )
        )
    }
}
