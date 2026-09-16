package com.example.posapp.presentation.customer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.dao.CustomerWithDebt
import com.example.posapp.data.local.dao.OverdueDebtor
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.DebtPaymentEntity
import com.example.posapp.data.repository.CustomerRepository
import com.example.posapp.data.repository.RecordPaymentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CustomerEvent {
    data class ShowMessage(val message: String) : CustomerEvent()
}

@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    val customers: StateFlow<List<CustomerWithDebt>> = customerRepository.observeAllWithDebt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Piutang yang sudah lewat jatuh tempo (v15) — lihat StoreProfile.bonDueDays. */
    val overdueDebtors: StateFlow<List<OverdueDebtor>> = customerRepository.observeOverdueDebtors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<CustomerEvent>()
    val events: SharedFlow<CustomerEvent> = _events

    fun addCustomer(name: String, phone: String, address: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(CustomerEvent.ShowMessage("Nama pelanggan harus diisi")) }
            return
        }
        viewModelScope.launch {
            customerRepository.addCustomer(name, phone, address)
            _events.emit(CustomerEvent.ShowMessage("Pelanggan ditambahkan"))
        }
    }
}

/** Detail satu pelanggan: saldo piutang saat ini + riwayat pelunasan, dan aksi catat pelunasan. */
@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val customerId: Long = checkNotNull(savedStateHandle["customerId"])

    val detail: StateFlow<CustomerWithDebt?> = customerRepository.observeDebtDetail(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val payments: StateFlow<List<DebtPaymentEntity>> = customerRepository.observePayments(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<CustomerEvent>()
    val events: SharedFlow<CustomerEvent> = _events

    fun recordPayment(amount: Double, note: String) {
        viewModelScope.launch {
            when (val result = customerRepository.recordPayment(customerId, amount, note)) {
                is RecordPaymentResult.Success -> _events.emit(CustomerEvent.ShowMessage("Pelunasan dicatat"))
                is RecordPaymentResult.Error -> _events.emit(CustomerEvent.ShowMessage(result.message))
            }
        }
    }
}

/** Dipakai ringan di PosScreen untuk memilih pelanggan saat metode pembayaran Bon dipilih. */
@HiltViewModel
class CustomerPickerViewModel @Inject constructor(
    customerRepository: CustomerRepository
) : ViewModel() {
    val customers: StateFlow<List<CustomerEntity>> = customerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
