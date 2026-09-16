package com.example.posapp.presentation.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.ExpenseEntity
import com.example.posapp.data.local.entity.ExpensePeriod
import com.example.posapp.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ExpenseEvent {
    data class ShowMessage(val message: String) : ExpenseEvent()
}

/** Kelola daftar Beban Usaha (Sewa Toko, Gaji Karyawan, Listrik, dll) — halaman khusus Admin. */
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    val expenses: StateFlow<List<ExpenseEntity>> = expenseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<ExpenseEvent>()
    val events: SharedFlow<ExpenseEvent> = _events

    fun addExpense(name: String, amount: Double, period: ExpensePeriod) {
        if (name.isBlank() || amount <= 0) {
            viewModelScope.launch { _events.emit(ExpenseEvent.ShowMessage("Nama & nominal beban harus diisi dengan benar")) }
            return
        }
        viewModelScope.launch {
            expenseRepository.add(name, amount, period)
            _events.emit(ExpenseEvent.ShowMessage("Beban usaha ditambahkan"))
        }
    }

    fun updateExpense(expense: ExpenseEntity, name: String, amount: Double, period: ExpensePeriod) {
        if (name.isBlank() || amount <= 0) {
            viewModelScope.launch { _events.emit(ExpenseEvent.ShowMessage("Nama & nominal beban harus diisi dengan benar")) }
            return
        }
        viewModelScope.launch {
            expenseRepository.update(expense.copy(name = name.trim(), amount = amount, period = period))
            _events.emit(ExpenseEvent.ShowMessage("Beban usaha diperbarui"))
        }
    }

    /** Nonaktifkan sementara tanpa hapus data (mis. beban musiman yang sedang tidak berlaku). */
    fun setActive(expense: ExpenseEntity, active: Boolean) {
        viewModelScope.launch { expenseRepository.setActive(expense, active) }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            expenseRepository.delete(id)
            _events.emit(ExpenseEvent.ShowMessage("Beban usaha dihapus"))
        }
    }
}
