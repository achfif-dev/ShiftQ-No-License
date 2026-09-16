package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.SupplierEntity
import com.example.posapp.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SupplierEvent {
    data class ShowMessage(val message: String) : SupplierEvent()
}

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    val suppliers: StateFlow<List<SupplierEntity>> = supplierRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<SupplierEvent>()
    val events: SharedFlow<SupplierEvent> = _events

    fun addSupplier(name: String, phone: String, address: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(SupplierEvent.ShowMessage("Nama pemasok harus diisi")) }
            return
        }
        viewModelScope.launch {
            supplierRepository.addSupplier(name, phone, address)
            _events.emit(SupplierEvent.ShowMessage("Pemasok ditambahkan"))
        }
    }

    fun updateSupplier(supplier: SupplierEntity, name: String, phone: String, address: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(SupplierEvent.ShowMessage("Nama pemasok harus diisi")) }
            return
        }
        viewModelScope.launch {
            supplierRepository.updateSupplier(supplier, name, phone, address)
            _events.emit(SupplierEvent.ShowMessage("Pemasok diperbarui"))
        }
    }

    fun setActive(supplier: SupplierEntity, active: Boolean) {
        viewModelScope.launch { supplierRepository.setActive(supplier, active) }
    }
}
