package com.example.posapp.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.sync.OutletCatalogSyncRepository
import com.example.posapp.data.sync.OutletProductRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutletStockUiState(
    val isLoading: Boolean = true,
    val outlets: List<Pair<String, String>> = emptyList(), // (outletId, outletName)
    val selectedOutletId: String? = null,
    val rows: List<OutletProductRow> = emptyList(),
    val searchQuery: String = "",
)

@HiltViewModel
class OutletStockViewModel @Inject constructor(
    private val repository: OutletCatalogSyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutletStockUiState())
    val uiState: StateFlow<OutletStockUiState> = _uiState.asStateFlow()

    init {
        loadOutlets()
    }

    fun loadOutlets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val outlets = repository.listKnownOutlets()
            _uiState.value = _uiState.value.copy(isLoading = false, outlets = outlets)
        }
    }

    fun selectOutlet(outletId: String) {
        _uiState.value = _uiState.value.copy(selectedOutletId = outletId, rows = emptyList())
        viewModelScope.launch {
            repository.observeOutletCatalog(outletId).collect { rows ->
                _uiState.value = _uiState.value.copy(rows = rows)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    val filteredRows: List<OutletProductRow>
        get() {
            val q = _uiState.value.searchQuery.trim().lowercase()
            if (q.isBlank()) return _uiState.value.rows
            return _uiState.value.rows.filter { it.name.lowercase().contains(q) || it.sku.lowercase().contains(q) }
        }
}
