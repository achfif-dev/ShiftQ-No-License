package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.DebtPaymentEntity
import kotlinx.coroutines.flow.Flow

/** Ringkasan piutang per pelanggan, dihitung langsung dari data transaksi & pelunasan asli. */
data class CustomerWithDebt(
    val id: Long,
    val name: String,
    val phone: String?,
    val address: String?,
    val debtBalance: Double,
    val loyaltyPoints: Long
)

/** Satu pelanggan dengan piutang yang sudah melewati tanggal jatuh tempo (v15) — lihat
 * [CustomerDao.observeOverdueDebtors]. */
data class OverdueDebtor(
    val customerId: Long,
    val customerName: String,
    val customerPhone: String?,
    val earliestDueDate: Long,
    val debtBalance: Double
)

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT c.id as id, c.name as name, c.phone as phone, c.address as address, c.loyaltyPoints as loyaltyPoints,
            COALESCE((
                SELECT SUM(tp.amount) FROM transaction_payments tp
                JOIN transactions t ON t.id = tp.transactionId
                WHERE tp.method = 'BON' AND t.customerId = c.id
            ), 0) - COALESCE((
                SELECT SUM(dp.amount) FROM debt_payments dp WHERE dp.customerId = c.id
            ), 0) as debtBalance
        FROM customers c
        WHERE c.isActive = 1
        ORDER BY c.name ASC
        """
    )
    fun observeAllWithDebt(): Flow<List<CustomerWithDebt>>

    @Query(
        """
        SELECT c.id as id, c.name as name, c.phone as phone, c.address as address, c.loyaltyPoints as loyaltyPoints,
            COALESCE((
                SELECT SUM(tp.amount) FROM transaction_payments tp
                JOIN transactions t ON t.id = tp.transactionId
                WHERE tp.method = 'BON' AND t.customerId = c.id
            ), 0) - COALESCE((
                SELECT SUM(dp.amount) FROM debt_payments dp WHERE dp.customerId = c.id
            ), 0) as debtBalance
        FROM customers c
        WHERE c.id = :customerId
        """
    )
    fun observeDebtDetail(customerId: Long): Flow<CustomerWithDebt?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): CustomerEntity?

    /**
     * Pelanggan dengan piutang BON yang sudah lewat tanggal jatuh tempo (v15, lihat
     * TransactionPaymentEntity.dueDate & StoreProfile.bonDueDays) DAN saldo piutangnya masih
     * > 0 (belum lunas — HAVING debtBalance > 0 menyaring pelanggan yang debtnya sudah dibayar
     * lunas walau salah satu transaksi lamanya pernah telat). Transaksi VOIDED dikeluarkan dari
     * hitungan (uangnya dianggap tidak pernah terjadi, sama seperti kalkulasi debt lain di app
     * ini). Diurutkan dari yang paling lama menunggak.
     */
    @Query(
        """
        SELECT c.id as customerId, c.name as customerName, c.phone as customerPhone,
            MIN(tp.dueDate) as earliestDueDate,
            COALESCE((
                SELECT SUM(tp2.amount) FROM transaction_payments tp2
                JOIN transactions t2 ON t2.id = tp2.transactionId
                WHERE tp2.method = 'BON' AND t2.customerId = c.id AND t2.status != 'VOIDED'
            ), 0) - COALESCE((
                SELECT SUM(dp.amount) FROM debt_payments dp WHERE dp.customerId = c.id
            ), 0) as debtBalance
        FROM customers c
        JOIN transactions t ON t.customerId = c.id
        JOIN transaction_payments tp ON tp.transactionId = t.id AND tp.method = 'BON'
        WHERE c.isActive = 1 AND t.status != 'VOIDED' AND tp.dueDate IS NOT NULL AND tp.dueDate < :now
        GROUP BY c.id
        HAVING debtBalance > 0
        ORDER BY earliestDueDate ASC
        """
    )
    fun observeOverdueDebtors(now: Long): Flow<List<OverdueDebtor>>

    @Insert
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("SELECT * FROM debt_payments WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observePayments(customerId: Long): Flow<List<DebtPaymentEntity>>

    @Insert
    suspend fun insertPayment(payment: DebtPaymentEntity): Long

    /** [delta] boleh negatif (penukaran poin) atau positif (poin didapat dari belanja). Dipanggil
     * oleh CheckoutUseCase setelah transaksi berhasil disimpan — lihat StoreProfile.loyaltyEnabled.
     * Dibatasi MAX(0, ...) sebagai jaring pengaman terakhir supaya saldo tidak pernah negatif
     * walau ada bug di lapisan atas yang mengirim delta pengurangan lebih besar dari saldo. */
    @Query("UPDATE customers SET loyaltyPoints = MAX(0, loyaltyPoints + :delta) WHERE id = :customerId")
    suspend fun adjustLoyaltyPoints(customerId: Long, delta: Long)
}
