package com.example.posapp.presentation.promo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.PromoEntity
import com.example.posapp.data.local.entity.PromoType
import com.example.posapp.data.repository.CategoryRepository
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.PromoRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PromoEvent {
    data class ShowMessage(val message: String) : PromoEvent()
}

data class PromoUiState(
    val promos: List<PromoEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val promoFeatureEnabled: Boolean = false
)

/** Kelola aturan Promo/Diskon otomatis (v15) — ADMIN & MANAGER, lihat Permission.canManagePromos. */
@HiltViewModel
class PromoViewModel @Inject constructor(
    private val promoRepository: PromoRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<PromoEvent>()
    val events: SharedFlow<PromoEvent> = _events

    val uiState: StateFlow<PromoUiState> = combine(
        promoRepository.observeAll(),
        categoryRepository.observeAll(),
        productRepository.observeAll(),
        storeProfileRepository.profile
    ) { promos, categories, products, profile ->
        PromoUiState(
            promos = promos,
            categories = categories,
            products = products,
            promoFeatureEnabled = profile.promoEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PromoUiState())

    fun setPromoFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setPromoEnabled(enabled) }
    }

    fun savePromo(
        existing: PromoEntity?,
        name: String,
        type: PromoType,
        percent: Double,
        minPurchase: Double,
        categoryId: Long?,
        productId: Long?,
        buyQty: Int,
        getFreeQty: Int
    ) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(PromoEvent.ShowMessage("Nama promo wajib diisi")) }
            return
        }
        val invalid = when (type) {
            PromoType.PERCENT_MIN_PURCHASE -> percent <= 0 || percent > 100 || minPurchase < 0
            PromoType.PERCENT_CATEGORY -> percent <= 0 || percent > 100 || categoryId == null
            PromoType.BUY_X_GET_Y_FREE -> productId == null || buyQty <= 0 || getFreeQty <= 0
        }
        if (invalid) {
            viewModelScope.launch { _events.emit(PromoEvent.ShowMessage("Isian promo belum lengkap/valid untuk jenis ini")) }
            return
        }
        viewModelScope.launch {
            val promo = (existing ?: PromoEntity(name = name, type = type)).copy(
                name = name.trim(),
                type = type,
                percent = percent,
                minPurchase = minPurchase,
                categoryId = categoryId,
                productId = productId,
                buyQty = buyQty,
                getFreeQty = getFreeQty
            )
            promoRepository.save(promo)
            _events.emit(PromoEvent.ShowMessage(if (existing == null) "Promo ditambahkan" else "Promo diperbarui"))
        }
    }

    fun setActive(promo: PromoEntity, active: Boolean) {
        viewModelScope.launch { promoRepository.setActive(promo, active) }
    }

    fun deletePromo(id: Long) {
        viewModelScope.launch {
            promoRepository.delete(id)
            _events.emit(PromoEvent.ShowMessage("Promo dihapus"))
        }
    }
}
