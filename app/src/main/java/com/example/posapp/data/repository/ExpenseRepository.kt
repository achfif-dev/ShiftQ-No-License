package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.ExpenseDao
import com.example.posapp.data.local.entity.ExpenseEntity
import com.example.posapp.data.local.entity.ExpensePeriod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    fun observeAll(): Flow<List<ExpenseEntity>> = expenseDao.observeAll()

    suspend fun add(name: String, amount: Double, period: ExpensePeriod): Long =
        expenseDao.insert(ExpenseEntity(name = name.trim(), amount = amount, period = period))

    suspend fun update(expense: ExpenseEntity) = expenseDao.update(expense)

    suspend fun setActive(expense: ExpenseEntity, active: Boolean) =
        expenseDao.update(expense.copy(isActive = active))

    suspend fun delete(id: Long) = expenseDao.delete(id)

    /**
     * Total Beban Usaha yang berlaku untuk rentang [start]..[end] (epoch ms), diprorata dari
     * nominal per-periode tiap beban aktif — mis. Sewa Toko Rp3.000.000/BULANAN diperlakukan
     * sebagai ~Rp100.000/hari (3.000.000 / 30), lalu dikalikan jumlah hari pada rentang laporan
     * yang dipilih Admin (mis. "Hari Ini" bisa berupa sebagian hari, dihitung proporsional).
     */
    suspend fun getTotalForRange(start: Long, end: Long): Double {
        val days = ((end - start).toDouble() / MILLIS_PER_DAY).coerceAtLeast(0.0)
        return expenseDao.getActive().sumOf { expense -> (expense.amount / expense.period.daysInPeriod) * days }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
