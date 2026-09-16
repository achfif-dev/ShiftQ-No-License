package com.example.posapp.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.repository.CategoryRepository
import com.example.posapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State form untuk tambah/edit produk. productId null = mode tambah baru. */
data class ProductFormState(
    val productId: Long? = null,
    val name: String = "",
    val sku: String = "",
    val categoryId: Long? = null,
    val purchasePrice: String = "",
    val sellPrice: String = "",
    val stock: String = "",
    val unit: String = com.example.posapp.data.local.entity.ProductUnits.DEFAULT,
    val lowStockThreshold: String = "5",
    val discountPercent: String = "0",
    val variantName: String = "",
    val hasVariants: Boolean = false,
    val photoPath: String? = null,
    val supplierId: Long? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() && sku.isNotBlank() &&
            purchasePrice.toDoubleOrNull() != null &&
            sellPrice.toDoubleOrNull() != null &&
            (hasVariants || stock.toIntOrNull() != null)
}

data class ProductUiState(
    val products: List<ProductEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val suppliers: List<com.example.posapp.data.local.entity.SupplierEntity> = emptyList(),
    val searchQuery: String = "",
    val isSaving: Boolean = false
)

sealed class ProductEvent {
    data class ShowMessage(val message: String) : ProductEvent()
    data class SavedSuccessfully(val productId: Long) : ProductEvent()
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: com.example.posapp.data.repository.SupplierRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSaving = MutableStateFlow(false)
    private val _events = MutableSharedFlow<ProductEvent>()
    val events: SharedFlow<ProductEvent> = _events

    private val productsFlow = productRepository.observeAll()
    private val categoriesFlow = categoryRepository.observeAll()
    private val suppliersFlow = supplierRepository.observeAll()

    val uiState: StateFlow<ProductUiState> = combine(
        productsFlow, categoriesFlow, suppliersFlow, _searchQuery, _isSaving
    ) { products, categories, suppliers, query, saving ->
        val filtered = if (query.isBlank()) products else products.filter {
            it.name.contains(query, ignoreCase = true) || it.sku.contains(query, ignoreCase = true)
        }
        ProductUiState(products = filtered, categories = categories, suppliers = suppliers, searchQuery = query, isSaving = saving)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun saveProduct(form: ProductFormState) {
        if (!form.isValid) {
            viewModelScope.launch { _events.emit(ProductEvent.ShowMessage("Lengkapi nama, SKU, harga, dan stok dengan benar")) }
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val existing = form.productId?.let { id -> uiState.value.products.find { it.id == id } }
                val entity = ProductEntity(
                    id = form.productId ?: 0L,
                    name = form.name.trim(),
                    sku = form.sku.trim(),
                    categoryId = form.categoryId,
                    supplierId = form.supplierId,
                    purchasePrice = form.purchasePrice.toDouble(),
                    sellPrice = form.sellPrice.toDouble(),
                    stock = if (form.hasVariants) 0 else (form.stock.toIntOrNull() ?: 0),
                    unit = form.unit.trim().ifBlank { com.example.posapp.data.local.entity.ProductUnits.DEFAULT },
                    lowStockThreshold = form.lowStockThreshold.toIntOrNull() ?: 5,
                    discountPercent = form.discountPercent.toDoubleOrNull() ?: 0.0,
                    variantName = form.variantName.ifBlank { null },
                    hasVariants = form.hasVariants,
                    photoPath = form.photoPath,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val savedId = productRepository.upsert(entity)
                _events.emit(ProductEvent.SavedSuccessfully(savedId))
            } catch (e: Exception) {
                _events.emit(ProductEvent.ShowMessage(e.message ?: "SKU/Barcode mungkin sudah dipakai produk lain"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            productRepository.delete(product.id)
            _events.emit(ProductEvent.ShowMessage("${product.name} dihapus"))
        }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.upsert(CategoryEntity(name = name.trim()))
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { categoryRepository.delete(category) }
    }

    // --- Varian produk (matrix Ukuran x Warna) ---

    fun observeVariants(productId: Long) = productRepository.observeVariants(productId)

    fun saveVariant(productId: Long, variant: ProductVariantEntity) {
        viewModelScope.launch {
            try {
                productRepository.upsertVariant(variant.copy(productId = productId))
                _events.emit(ProductEvent.ShowMessage("Varian tersimpan"))
            } catch (e: Exception) {
                _events.emit(ProductEvent.ShowMessage("Gagal menyimpan varian: SKU mungkin sudah dipakai"))
            }
        }
    }

    fun deleteVariant(variantId: Long) {
        viewModelScope.launch {
            productRepository.deleteVariant(variantId)
            _events.emit(ProductEvent.ShowMessage("Varian dihapus"))
        }
    }
}
