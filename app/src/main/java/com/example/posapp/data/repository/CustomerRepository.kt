package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.CustomerDao
import com.example.posapp.data.local.dao.CustomerWithDebt
import com.example.posapp.data.local.dao.OverdueDebtor
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.DebtPaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class RecordPaymentResult {
    object Success : RecordPaymentResult()
    data class Error(val message: String) : RecordPaymentResult()
}

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao
) {
    fun observeAll(): Flow<List<CustomerEntity>> = customerDao.observeAll()

    fun observeAllWithDebt(): Flow<List<CustomerWithDebt>> = customerDao.observeAllWithDebt()

    fun observeDebtDetail(customerId: Long): Flow<CustomerWithDebt?> = customerDao.observeDebtDetail(customerId)

    /** Pelanggan dengan piutang BON yang sudah lewat jatuh tempo (v15) — lihat CustomerDao.observeOverdueDebtors. */
    fun observeOverdueDebtors(): Flow<List<OverdueDebtor>> =
        customerDao.observeOverdueDebtors(System.currentTimeMillis())

    fun observePayments(customerId: Long): Flow<List<DebtPaymentEntity>> = customerDao.observePayments(customerId)

    suspend fun addCustomer(name: String, phone: String?, address: String?): Long =
        customerDao.insert(
            CustomerEntity(
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotBlank() },
                address = address?.trim()?.takeIf { it.isNotBlank() }
            )
        )

    suspend fun updateCustomer(customer: CustomerEntity, name: String, phone: String?, address: String?) {
        customerDao.update(
            customer.copy(
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotBlank() },
                address = address?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    /** Nonaktifkan pelanggan tanpa menghapus riwayat transaksi/piutangnya. */
    suspend fun setActive(customer: CustomerEntity, active: Boolean) =
        customerDao.update(customer.copy(isActive = active))

    /**
     * Catat pelunasan (sebagian atau penuh) piutang seorang pelanggan. [amount] tidak wajib
     * sama dengan sisa piutang — pelanggan boleh mencicil; saldo piutang otomatis berkurang
     * karena selalu dihitung ulang dari total pelunasan yang tercatat.
     */
    /** [delta] boleh negatif (penukaran poin) atau positif (poin didapat dari belanja). Dipanggil
     * oleh CheckoutUseCase setelah transaksi berhasil disimpan — lihat StoreProfile.loyaltyEnabled. */
    suspend fun adjustLoyaltyPoints(customerId: Long, delta: Long) {
        if (delta == 0L) return
        customerDao.adjustLoyaltyPoints(customerId, delta)
    }

    suspend fun recordPayment(customerId: Long, amount: Double, note: String? = null): RecordPaymentResult {
        if (amount <= 0) {
            return RecordPaymentResult.Error("Nominal pelunasan harus lebih dari 0")
        }
        customerDao.insertPayment(
            DebtPaymentEntity(customerId = customerId, amount = amount, note = note?.takeIf { it.isNotBlank() })
        )
        return RecordPaymentResult.Success
    }
}
