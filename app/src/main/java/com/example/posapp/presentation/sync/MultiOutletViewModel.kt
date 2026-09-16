package com.example.posapp.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.sync.CloudSyncRepository
import com.example.posapp.data.sync.OutletSalesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MultiOutletUiState(
    val dateKey: String = "",
    val summaries: List<OutletSalesSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isConfigured: Boolean = true
)

private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

@HiltViewModel
class MultiOutletViewModel @Inject constructor(
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiOutletUiState(dateKey = apiDateFormat.format(Date())))
    val uiState: StateFlow<MultiOutletUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val isConfigured = cloudSyncRepository.isConfigured()
        if (!isConfigured) {
            _uiState.value = _uiState.value.copy(isLoading = false, isConfigured = false, summaries = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isConfigured = true)
            val results = cloudSyncRepository.fetchSummariesForDate(_uiState.value.dateKey)
            _uiState.value = _uiState.value.copy(summaries = results.sortedByDescending { it.totalRevenue }, isLoading = false)
        }
    }
}
