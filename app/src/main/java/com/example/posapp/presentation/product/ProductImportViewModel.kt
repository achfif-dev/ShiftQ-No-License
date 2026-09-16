package com.example.posapp.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.export.ProductCsvParser
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.domain.usecase.ProductImportSummary
import com.example.posapp.domain.usecase.ProductImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProductImportEvent {
    data class ShowMessage(val message: String) : ProductImportEvent()
}

/** Tahapan alur Import Produk Massal — SELALU lewat [Preview] dulu (pratinjau hasil parsing +
 * daftar error per-baris), pengguna harus menekan konfirmasi eksplisit sebelum apa pun ditulis
 * ke database, lihat [ProductImportUseCase]. */
sealed class ProductImportStep {
    object PickFile : ProductImportStep()
    object Parsing : ProductImportStep()
    data class Preview(val parseResult: ProductCsvParser.ParseResult) : ProductImportStep()
    object Importing : ProductImportStep()
    data class Done(val summary: ProductImportSummary) : ProductImportStep()
}

@HiltViewModel
class ProductImportViewModel @Inject constructor(
    private val productImportUseCase: ProductImportUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _step = MutableStateFlow<ProductImportStep>(ProductImportStep.PickFile)
    val step: StateFlow<ProductImportStep> = _step.asStateFlow()

    private val _events = MutableSharedFlow<ProductImportEvent>()
    val events: SharedFlow<ProductImportEvent> = _events

    fun onFileContentRead(text: String?) {
        if (text == null) {
            viewModelScope.launch { _events.emit(ProductImportEvent.ShowMessage("Gagal membaca file yang dipilih")) }
            return
        }
        _step.value = ProductImportStep.Parsing
        val result = ProductCsvParser.parse(text)
        if (result.headerError != null) {
            viewModelScope.launch { _events.emit(ProductImportEvent.ShowMessage(result.headerError)) }
            _step.value = ProductImportStep.PickFile
            return
        }
        _step.value = ProductImportStep.Preview(result)
    }

    fun confirmImport() {
        val current = _step.value
        if (current !is ProductImportStep.Preview) return
        if (current.parseResult.validRows.isEmpty()) {
            viewModelScope.launch { _events.emit(ProductImportEvent.ShowMessage("Tidak ada baris valid untuk diimpor")) }
            return
        }
        _step.value = ProductImportStep.Importing
        viewModelScope.launch {
            val user = sessionManager.currentUser.value
            val summary = productImportUseCase.import(
                rows = current.parseResult.validRows,
                parseErrors = current.parseResult.errors,
                actorName = user?.name,
                actorRole = user?.role
            )
            _step.value = ProductImportStep.Done(summary)
        }
    }

    fun cancelPreview() {
        _step.value = ProductImportStep.PickFile
    }

    fun reset() {
        _step.value = ProductImportStep.PickFile
    }
}
