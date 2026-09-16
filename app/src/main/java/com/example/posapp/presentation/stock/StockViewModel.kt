package com.example.posapp.presentation.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import com.example.posapp.data.repository.CategoryRepository
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.domain.auth.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StockEvent {
    data class ShowMessage(val message: String) : StockEvent()
}

@HiltViewModel
class StockViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: com.example.posapp.data.repository.SupplierRepository,
    private val sessionManager: SessionManager,
    storeProfileRepository: com.example.posapp.data.settings.StoreProfileRepository
) : ViewModel() {

    /** Stok Opname HANYA Admin (lihat Permission.canPerformStockOpname) — dipakai StockScreen
     * untuk menyembunyikan opsi "Set Opname" bagi non-admin. Pertahanan lapis kedua ada di
     * [adjustStock]/[adjustVariantStock] yang menolak tipe OPNAME kalau dipanggil tanpa izin,
     * jadi UI yang disembunyikan bukan satu-satunya penghalang (audit 2026-09-06). */
    val canPerformOpname: StateFlow<Boolean> = combine(
        sessionManager.currentUser,
        storeProfileRepository.profile
    ) { user, profile -> Permission.canPerformStockOpname(user, profile.pinLoginEnabled) }
        // Nilai awal fail-closed (false) selagi menunggu currentUser/profile pertama kali
        // ter-emit — konsisten dengan Permission.kt (fail-closed), bukan cuma mengandalkan
        // pertahanan lapis kedua di adjustStock/adjustVariantStock (audit 2026-09-09).
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Peta categoryId -> nama kategori, dipakai untuk memilih ikon avatar produk yang
    // sesuai kategori (senada dengan avatar produk di layar Kasir & Produk).
    val categoryNamesById: StateFlow<Map<Long, String>> = categoryRepository.observeAll()
        .map { categories: List<CategoryEntity> -> categories.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val lowStockProducts: StateFlow<List<ProductEntity>> = productRepository.observeLowStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pemasok aktif (v13), dipakai mengelompokkan draf Pesanan Pembelian dari stok tipis. */
    val suppliers: StateFlow<List<com.example.posapp.data.local.entity.SupplierEntity>> = supplierRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierPoEnabled: StateFlow<Boolean> = storeProfileRepository.profile
        .map { it.supplierPoEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Riwayat penyesuaian stok gabungan (produk biasa & tiap kombinasi varian), terbaru dulu. */
    val adjustmentHistory: StateFlow<List<StockAdjustmentEntity>> = productRepository.observeAdjustmentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<StockEvent>()
    val events: SharedFlow<StockEvent> = _events

    /**
     * @param type "IN" untuk stok masuk, "OUT" untuk stok keluar/rusak, "OPNAME" untuk set
     * stok ke jumlah hasil hitung fisik langsung (bukan penambahan/pengurangan).
     */
    fun adjustStock(productId: Long, type: String, quantity: Int, reason: String?) {
        if (quantity < 0) {
            viewModelScope.launch { _events.emit(StockEvent.ShowMessage("Jumlah tidak boleh negatif")) }
            return
        }
        if (type == "OPNAME" && !canPerformOpname.value) {
            viewModelScope.launch { _events.emit(StockEvent.ShowMessage("Hanya Admin yang boleh melakukan stok opname")) }
            return
        }
        viewModelScope.launch {
            productRepository.adjustStock(productId, type, quantity, reason)
            _events.emit(StockEvent.ShowMessage("Stok berhasil diperbarui"))
        }
    }

    /** Penyesuaian stok untuk satu kombinasi varian tertentu (mis. "Merah / L"). */
    fun adjustVariantStock(productId: Long, variant: ProductVariantEntity, type: String, quantity: Int, reason: String?) {
        if (quantity < 0) {
            viewModelScope.launch { _events.emit(StockEvent.ShowMessage("Jumlah tidak boleh negatif")) }
            return
        }
        if (type == "OPNAME" && !canPerformOpname.value) {
            viewModelScope.launch { _events.emit(StockEvent.ShowMessage("Hanya Admin yang boleh melakukan stok opname")) }
            return
        }
        viewModelScope.launch {
            productRepository.adjustVariantStock(productId, variant.id, variant.variantLabel, type, quantity, reason)
            _events.emit(StockEvent.ShowMessage("Stok varian \"${variant.variantLabel}\" berhasil diperbarui"))
        }
    }

    suspend fun getVariantsFor(productId: Long): List<ProductVariantEntity> =
        productRepository.getVariants(productId)
}
