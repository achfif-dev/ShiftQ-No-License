package com.example.posapp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.export.FileShareHelper
import com.example.posapp.data.export.PdfInvoiceGenerator
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.ParkedSaleEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.PromoEntity
import com.example.posapp.data.printer.PrintResult
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.printer.toPrinterConfig
import com.example.posapp.data.repository.CategoryRepository
import com.example.posapp.data.repository.CustomerRepository
import com.example.posapp.data.repository.ParkedSaleRepository
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.PromoRepository
import com.example.posapp.data.repository.RestoreParkedSaleResult
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.data.settings.StoreProfile
import com.example.posapp.data.settings.StoreProfileRepository
import com.example.posapp.data.sync.CloudSyncRepository
import com.example.posapp.data.sync.OutletSalesSummary
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import com.example.posapp.domain.usecase.CheckoutResult
import com.example.posapp.domain.usecase.CheckoutUseCase
import com.example.posapp.domain.usecase.PaymentSplit
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
import java.io.File
import javax.inject.Inject

data class PosUiState(
    val products: List<ProductEntity> = emptyList(),
    // Peta categoryId -> nama kategori, dipakai untuk memilih ikon produk yang sesuai
    // kategori saat produk belum punya foto (lihat iconForCategory di PosScreen).
    val categoryNamesById: Map<Long, String> = emptyMap(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val cart: Cart = Cart(),
    val isProcessing: Boolean = false,
    val storeProfile: StoreProfile = StoreProfile(),
    val cashierName: String? = null,
    val isAdmin: Boolean = true,
    // Tombol "Produk" di top bar Kasir — ADMIN & MANAGER, KASIR ditolak (lihat
    // Permission.canManageProducts). Rute "products" tetap digerbang independen di
    // MainActivity sebagai lapis kedua (audit 2026-09-06).
    val canManageProducts: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PosViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val checkoutUseCase: CheckoutUseCase,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val printerRepository: PrinterRepository,
    private val pdfInvoiceGenerator: PdfInvoiceGenerator,
    private val fileShareHelper: FileShareHelper,
    private val storeProfileRepository: StoreProfileRepository,
    private val sessionManager: SessionManager,
    private val customerRepository: CustomerRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val promoRepository: PromoRepository,
    private val parkedSaleRepository: ParkedSaleRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _cart = MutableStateFlow(Cart())
    private val _isProcessing = MutableStateFlow(false)

    private val _events = MutableSharedFlow<PosEvent>()
    val events: SharedFlow<PosEvent> = _events

    // Flow produk mengikuti perubahan query pencarian & kategori terpilih secara reaktif
    private val productsFlow = combine(_searchQuery, _selectedCategoryId) { q, c -> q to c }
        .flatMapLatest { (query, categoryId) -> productRepository.search(query, categoryId) }

    val uiState: StateFlow<PosUiState> = combine(
        productsFlow,
        categoryRepository.observeAll(),
        _searchQuery,
        _selectedCategoryId,
        _cart,
        _isProcessing,
        storeProfileRepository.profile,
        sessionManager.currentUser,
        promoRepository.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val products = values[0] as List<ProductEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = values[1] as List<CategoryEntity>
        val query = values[2] as String
        val categoryId = values[3] as Long?
        val rawCart = values[4] as Cart
        val processing = values[5] as Boolean
        val profile = values[6] as StoreProfile
        val user = values[7] as com.example.posapp.data.local.entity.UserEntity?
        @Suppress("UNCHECKED_CAST")
        val promos = values[8] as List<PromoEntity>
        // Promo diterapkan di sini (bukan di dalam mutator _cart) supaya keranjang "mentah" yang
        // diedit kasir (addToCart, updateQuantity, dst.) tetap sumber kebenaran tunggal — promo
        // selalu DIHITUNG ULANG dari nol setiap kali baris/promo berubah, tidak pernah "menempel"
        // secara stateful. Kalau fitur promo nonaktif, kirim daftar kosong -> PromoEngine otomatis
        // membersihkan sisa potongan promo lama (mis. baru saja dimatikan admin di tengah transaksi).
        val cart = com.example.posapp.domain.usecase.PromoEngine.apply(
            rawCart,
            if (profile.promoEnabled) promos else emptyList()
        )
        PosUiState(
            products = products,
            categoryNamesById = categories.associate { it.id to it.name },
            searchQuery = query,
            selectedCategoryId = categoryId,
            cart = cart,
            isProcessing = processing,
            storeProfile = profile,
            cashierName = user?.name,
            isAdmin = user == null || user.role == UserRole.ADMIN,
            canManageProducts = com.example.posapp.domain.auth.Permission.canManageProducts(user, profile.pinLoginEnabled)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosUiState())

    /** Daftar transaksi yang sedang ditahan (v15) — lihat ParkedSaleRepository. */
    val parkedSales: StateFlow<List<ParkedSaleEntity>> = parkedSaleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Daftar pelanggan aktif, dipakai untuk memilih pelanggan saat metode Bon/Piutang dipakai. */
    val customers: StateFlow<List<CustomerEntity>> = customerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Selaraskan pajak keranjang dengan pengaturan toko (Pengaturan > Profil Toko > Pajak)
        // secara reaktif, termasuk saat pajak dinonaktifkan (taxPercent otomatis jadi 0).
        viewModelScope.launch {
            storeProfileRepository.profile.collect { profile ->
                val effectiveTaxPercent = if (profile.taxEnabled) profile.taxPercent else 0.0
                if (_cart.value.taxPercent != effectiveTaxPercent) {
                    _cart.value = _cart.value.copy(taxPercent = effectiveTaxPercent)
                }
            }
        }
    }

    private val _lastReceipt = MutableStateFlow<Pair<TransactionEntity, List<TransactionItemEntity>>?>(null)
    val lastReceipt: StateFlow<Pair<TransactionEntity, List<TransactionItemEntity>>?> = _lastReceipt.asStateFlow()

    fun dismissReceipt() {
        _lastReceipt.value = null
    }

    fun printReceipt() {
        val receipt = _lastReceipt.value ?: return
        val profile = uiState.value.storeProfile
        viewModelScope.launch {
            val skuMap = if (profile.receiptShowSku) buildSkuMap(receipt.second) else emptyMap()
            val result = withContext(Dispatchers.IO) {
                printerRepository.printReceipt(
                    storeName = profile.name,
                    transaction = receipt.first,
                    items = receipt.second,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter,
                    logoImagePath = profile.logoImagePath,
                    language = profile.receiptLanguage,
                    printerConfig = profile.toPrinterConfig(),
                    headerNote = profile.receiptHeaderNote,
                    showSku = profile.receiptShowSku,
                    skuByProductId = skuMap
                )
            }
            when (result) {
                is PrintResult.Success -> _events.emit(PosEvent.ShowMessage("Struk berhasil dicetak"))
                is PrintResult.Error -> _events.emit(PosEvent.ShowMessage(result.message))
            }
        }
    }

    fun exportReceiptPdf() {
        val receipt = _lastReceipt.value ?: return
        val profile = uiState.value.storeProfile
        viewModelScope.launch {
            val skuMap = if (profile.receiptShowSku) buildSkuMap(receipt.second) else emptyMap()
            val file = withContext(Dispatchers.IO) {
                pdfInvoiceGenerator.generate(
                    storeName = profile.name,
                    transaction = receipt.first,
                    items = receipt.second,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter,
                    logoImagePath = profile.logoImagePath,
                    language = profile.receiptLanguage,
                    headerNote = profile.receiptHeaderNote,
                    showSku = profile.receiptShowSku,
                    skuByProductId = skuMap
                )
            }
            _events.emit(PosEvent.PdfReady(file))
        }
    }

    /** SKU per productId, hanya di-lookup kalau StoreProfile.receiptShowSku aktif (v15) — SKU
     * TIDAK disimpan sebagai snapshot di TransactionItemEntity (produk bisa berganti SKU setelah
     * transaksi lama), jadi ini SELALU SKU produk yang berlaku SEKARANG, bukan snapshot historis. */
    private suspend fun buildSkuMap(items: List<TransactionItemEntity>): Map<Long, String> =
        items.map { it.productId }.distinct().mapNotNull { productId ->
            productRepository.findById(productId)?.let { productId to it.sku }
        }.toMap()

    fun createShareIntent(file: File) = fileShareHelper.createShareIntent(file, "application/pdf")

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun addToCartBySku(sku: String) {
        viewModelScope.launch {
            val product = productRepository.findBySku(sku)
            if (product != null) {
                addToCart(product)
                return@launch
            }
            val variant = productRepository.findVariantBySku(sku)
            if (variant != null) {
                val allProducts = uiState.value.products
                val parent = allProducts.find { it.id == variant.productId }
                if (parent != null) {
                    addToCart(parent, variant)
                    return@launch
                }
            }
            _events.emit(PosEvent.ShowMessage("Produk dengan barcode \"$sku\" tidak ditemukan"))
        }
    }

    /** Ambil daftar varian aktif suatu produk (dipakai bottom sheet pemilih varian di POS). */
    suspend fun getVariantsFor(productId: Long): List<ProductVariantEntity> =
        productRepository.getVariants(productId)

    fun addToCart(product: ProductEntity, variant: ProductVariantEntity? = null) {
        val availableStock = variant?.stock ?: product.stock
        if (availableStock <= 0) {
            viewModelScope.launch { _events.emit(PosEvent.ShowMessage("Stok ${product.name} habis")) }
            return
        }
        val current = _cart.value
        val key = "${product.id}:${variant?.id ?: 0}"
        val existingIndex = current.lines.indexOfFirst { it.lineKey == key }
        val newLines = if (existingIndex >= 0) {
            current.lines.toMutableList().apply {
                val line = this[existingIndex]
                if (line.quantity + 1 > availableStock) {
                    return@apply
                }
                this[existingIndex] = line.copy(quantity = line.quantity + 1)
            }
        } else {
            current.lines + CartLine(product = product, variant = variant, quantity = 1)
        }
        _cart.value = current.copy(lines = newLines)
    }

    fun updateQuantity(lineKey: String, quantity: Int) {
        val current = _cart.value
        val newLines = if (quantity <= 0) {
            current.lines.filterNot { it.lineKey == lineKey }
        } else {
            current.lines.map { if (it.lineKey == lineKey) it.copy(quantity = quantity) else it }
        }
        _cart.value = current.copy(lines = newLines)
    }

    /**
     * TEMUAN KEAMANAN (audit ulang): sebelumnya menerima [discount] APA PUN tanpa batas atau
     * cek role sama sekali — Kasir biasa bisa memberi diskon hingga 100% pada satu baris,
     * membuat barang itu "gratis" secara sah di sistem. Sekarang nilai yang diminta dipangkas
     * (clamp) lewat [DiscountPolicy] berdasarkan role kasir yang sedang login & batas
     * [StoreProfile.maxKasirDiscountPercent] — ADMIN/MANAGER tidak dibatasi. Diskon yang
     * BENAR-BENAR diterapkan (setelah clamp) baru dicatat ke audit log saat checkout berhasil,
     * lihat CheckoutUseCase — bukan di sini, supaya tidak membanjiri log tiap kali kasir
     * menggeser/mengetik ulang nilai sebelum yakin.
     */
    fun updateLineDiscount(lineKey: String, discount: Double) {
        val current = _cart.value
        val line = current.lines.firstOrNull { it.lineKey == lineKey } ?: return
        val profile = uiState.value.storeProfile
        val user = sessionManager.currentUser.value
        val baseAmount = line.unitPrice * line.quantity
        val result = com.example.posapp.domain.usecase.DiscountPolicy.clamp(
            requestedDiscount = discount,
            baseAmount = baseAmount,
            user = user,
            pinLoginEnabled = profile.pinLoginEnabled,
            maxKasirDiscountPercent = profile.maxKasirDiscountPercent
        )
        _cart.value = current.copy(
            lines = current.lines.map {
                if (it.lineKey == lineKey) it.copy(discount = result.amount) else it
            }
        )
        if (result.wasClamped) {
            viewModelScope.launch {
                _events.emit(
                    PosEvent.ShowMessage(
                        "Diskon dipangkas ke maksimal ${profile.maxKasirDiscountPercent}% (kebijakan Kasir). " +
                            "Admin/Manager bisa login di device ini untuk memberi diskon lebih besar."
                    )
                )
            }
        }
    }

    /** Lihat catatan lengkap di [updateLineDiscount] — kebijakan & alasan yang sama berlaku di
     * sini, hanya basisnya subtotal keranjang (sebelum diskon), bukan satu baris. */
    fun updateTransactionDiscount(discount: Double) {
        val current = _cart.value
        val profile = uiState.value.storeProfile
        val user = sessionManager.currentUser.value
        val result = com.example.posapp.domain.usecase.DiscountPolicy.clamp(
            requestedDiscount = discount,
            baseAmount = current.subtotal,
            user = user,
            pinLoginEnabled = profile.pinLoginEnabled,
            maxKasirDiscountPercent = profile.maxKasirDiscountPercent
        )
        _cart.value = current.copy(transactionDiscount = result.amount)
        if (result.wasClamped) {
            viewModelScope.launch {
                _events.emit(
                    PosEvent.ShowMessage(
                        "Diskon dipangkas ke maksimal ${profile.maxKasirDiscountPercent}% dari subtotal (kebijakan Kasir). " +
                            "Admin/Manager bisa login di device ini untuk memberi diskon lebih besar."
                    )
                )
            }
        }
    }

    /**
     * Terapkan penukaran poin loyalitas (v13) sebagai potongan tambahan di kasir. [points]
     * otomatis dibatasi supaya tidak melebihi saldo poin pelanggan maupun subtotal belanja
     * (setelah dikurangi diskon manual) — tidak mungkin menghasilkan potongan negatif atau
     * "hutang poin". Kirim points = 0 untuk membatalkan penukaran.
     */
    fun applyLoyaltyRedemption(customer: CustomerEntity, points: Long) {
        val profile = uiState.value.storeProfile
        val current = _cart.value
        val maxDiscountable = (current.subtotal - current.transactionDiscount).coerceAtLeast(0.0)
        val maxPointsBySpend = if (profile.loyaltyPointValueRupiah > 0) {
            (maxDiscountable / profile.loyaltyPointValueRupiah).toLong()
        } else 0L
        val clampedPoints = points.coerceIn(0L, minOf(customer.loyaltyPoints, maxPointsBySpend))
        _cart.value = current.copy(
            loyaltyPointsRedeemed = clampedPoints,
            loyaltyDiscount = (clampedPoints * profile.loyaltyPointValueRupiah).toDouble()
        )
    }

    /** Batalkan penukaran poin yang sedang diterapkan (mis. pelanggan diganti/dibatalkan). */
    fun clearLoyaltyRedemption() {
        _cart.value = _cart.value.copy(loyaltyPointsRedeemed = 0, loyaltyDiscount = 0.0)
    }

    /** Set nomor meja/nama pemesan (v13, mode Resto/Kafe) — disimpan ke TransactionEntity.note
     * saat checkout. Kirim null/kosong untuk mengosongkan. */
    fun updateTableTag(tag: String?) {
        _cart.value = _cart.value.copy(tableTag = tag?.trim()?.takeIf { it.isNotBlank() })
    }

    fun updateTaxPercent(percent: Double) {
        _cart.value = _cart.value.copy(taxPercent = percent)
    }

    fun clearCart() {
        _cart.value = Cart()
    }

    fun checkout(payments: List<PaymentSplit>, customerId: Long? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            val cashierName = sessionManager.currentUser.value?.name
            val actorRole = sessionManager.currentUser.value?.role
            // Pakai cart dari uiState (SUDAH termasuk potongan promo otomatis, lihat combine di
            // atas), bukan _cart.value mentah -- supaya nominal yang benar-benar ditagih SAMA
            // PERSIS dengan yang terakhir dilihat kasir di layar, termasuk promonya.
            val effectiveCart = uiState.value.cart
            when (val result = checkoutUseCase(
                effectiveCart, payments, note = effectiveCart.tableTag, cashierName = cashierName,
                customerId = customerId, actorRole = actorRole
            )) {
                is CheckoutResult.Success -> {
                    _lastReceipt.value = transactionRepository.getTransactionWithItems(result.transactionId)
                    _events.emit(PosEvent.CheckoutSuccess(result.transactionId, result.invoiceNumber, result.change))
                    clearCart()
                    syncTodaySummaryIfEnabled()
                }
                is CheckoutResult.Error -> {
                    _events.emit(PosEvent.ShowMessage(result.message))
                }
            }
            _isProcessing.value = false
        }
    }

    /** Tahan keranjang saat ini (v15) — pelanggan belum selesai memilih/bayar, kasir bisa
     * langsung melayani orang lain. Keranjang dikosongkan setelah berhasil ditahan. */
    fun parkCurrentSale(note: String?, customerId: Long? = null, customerName: String? = null) {
        viewModelScope.launch {
            val cashierName = sessionManager.currentUser.value?.name ?: "Kasir"
            val parked = parkedSaleRepository.park(uiState.value.cart, note, customerId, customerName, cashierName)
            if (parked) {
                clearCart()
                _events.emit(PosEvent.ShowMessage("Transaksi ditahan"))
            } else {
                _events.emit(PosEvent.ShowMessage("Keranjang masih kosong, tidak ada yang ditahan"))
            }
        }
    }

    /** Lanjutkan transaksi yang ditahan. Hanya bisa kalau keranjang SEKARANG kosong -- supaya
     * keranjang yang sedang disusun kasir tidak tertimpa diam-diam. */
    fun restoreParkedSale(id: Long) {
        if (!_cart.value.isEmpty) {
            viewModelScope.launch { _events.emit(PosEvent.ShowMessage("Selesaikan/tahan dulu keranjang yang sedang berjalan sebelum melanjutkan transaksi lain")) }
            return
        }
        viewModelScope.launch {
            when (val result = parkedSaleRepository.restore(id)) {
                is RestoreParkedSaleResult.Success -> {
                    val profile = uiState.value.storeProfile
                    val effectiveTax = if (profile.taxEnabled) profile.taxPercent else 0.0
                    _cart.value = result.cart.copy(taxPercent = effectiveTax)
                    if (result.skippedItemNames.isNotEmpty()) {
                        _events.emit(PosEvent.ShowMessage("Transaksi dilanjutkan. Produk berikut dilewati karena sudah tidak tersedia: ${result.skippedItemNames.joinToString(", ")}"))
                    } else {
                        _events.emit(PosEvent.ShowMessage("Transaksi dilanjutkan"))
                    }
                }
                is RestoreParkedSaleResult.Error -> _events.emit(PosEvent.ShowMessage(result.message))
            }
        }
    }

    fun discardParkedSale(id: Long) {
        viewModelScope.launch {
            parkedSaleRepository.discard(id)
            _events.emit(PosEvent.ShowMessage("Transaksi tertahan dihapus"))
        }
    }

    fun logout() = sessionManager.logout()

    /**
     * Kirim ulang ringkasan omzet HARI INI ke Firestore setelah checkout — best-effort, tidak
     * pernah memblokir/menggagalkan alur kasir kalau cloud sync nonaktif, belum dikonfigurasi,
     * atau device sedang offline (lihat CloudSyncRepository, semua fail-soft).
     */
    private fun syncTodaySummaryIfEnabled() {
        viewModelScope.launch {
            val profile = storeProfileRepository.profile.first()
            if (!profile.cloudSyncEnabled) return@launch
            val outletId = storeProfileRepository.ensureOutletId()

            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1
            val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(dayStart))

            val summary = transactionRepository.getSalesSummary(dayStart, dayEnd)
            cloudSyncRepository.pushDailySummary(
                OutletSalesSummary(
                    outletId = outletId,
                    outletName = profile.outletName,
                    dateKey = dateKey,
                    totalRevenue = summary.totalRevenue,
                    totalTransactions = summary.totalTransactions,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

sealed class PosEvent {
    data class ShowMessage(val message: String) : PosEvent()
    data class CheckoutSuccess(val transactionId: Long, val invoiceNumber: String, val change: Double) : PosEvent()
    data class PdfReady(val file: File) : PosEvent()
}
