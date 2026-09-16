package com.example.posapp.presentation.label

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.printer.PrintResult
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.printer.toPrinterConfig
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Satu antrean cetak: produk + berapa lembar label yang mau dicetak untuknya. */
data class LabelQueueItem(val product: ProductEntity, val copies: Int)

sealed class LabelPrintEvent {
    data class ShowMessage(val message: String) : LabelPrintEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LabelPrintViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val printerRepository: PrinterRepository,
    private val storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<ProductEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else productRepository.search(query, null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _queue = MutableStateFlow<List<LabelQueueItem>>(emptyList())
    val queue: StateFlow<List<LabelQueueItem>> = _queue.asStateFlow()

    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private val _events = MutableSharedFlow<LabelPrintEvent>()
    val events: SharedFlow<LabelPrintEvent> = _events

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun addToQueue(product: ProductEntity) {
        val current = _queue.value
        val existingIndex = current.indexOfFirst { it.product.id == product.id }
        _queue.value = if (existingIndex >= 0) {
            current.toMutableList().apply { this[existingIndex] = this[existingIndex].copy(copies = this[existingIndex].copies + 1) }
        } else {
            current + LabelQueueItem(product, 1)
        }
        _searchQuery.value = ""
    }

    fun updateCopies(productId: Long, copies: Int) {
        if (copies <= 0) {
            _queue.value = _queue.value.filterNot { it.product.id == productId }
        } else {
            _queue.value = _queue.value.map { if (it.product.id == productId) it.copy(copies = copies) else it }
        }
    }

    fun removeFromQueue(productId: Long) {
        _queue.value = _queue.value.filterNot { it.product.id == productId }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    /** Cetak SEMUA item di antrean berurutan. Berhenti & laporkan di item pertama yang gagal
     * (mis. printer terputus di tengah) daripada diam-diam melewati sisanya. */
    fun printQueue() {
        val items = _queue.value
        if (items.isEmpty()) {
            viewModelScope.launch { _events.emit(LabelPrintEvent.ShowMessage("Antrean label masih kosong")) }
            return
        }
        viewModelScope.launch {
            _isPrinting.value = true
            val profile = storeProfileRepository.profile.first()
            val printerConfig = profile.toPrinterConfig()
            var printedLabels = 0
            var failed = false
            outer@ for (item in items) {
                for (copyIndex in 0 until item.copies) {
                    val result = withContext(Dispatchers.IO) {
                        printerRepository.printLabel(profile.name, item.product, printerConfig)
                    }
                    when (result) {
                        is PrintResult.Success -> printedLabels++
                        is PrintResult.Error -> {
                            _events.emit(LabelPrintEvent.ShowMessage("Gagal di \"${item.product.name}\": ${result.message} ($printedLabels label berhasil dicetak sebelumnya)"))
                            failed = true
                        }
                    }
                    if (failed) break@outer
                }
            }
            if (!failed) {
                _events.emit(LabelPrintEvent.ShowMessage("$printedLabels label berhasil dicetak"))
                clearQueue()
            }
            _isPrinting.value = false
        }
    }
}
