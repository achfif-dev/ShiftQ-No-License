package com.example.posapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.data.settings.StoreProfileRepository
import com.example.posapp.domain.auth.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class DashboardUiState(
    val cashierName: String? = null,
    val storeName: String = "Toko Saya",
    val storeLogoPath: String? = null,
    val todayRevenue: Double = 0.0,
    val todayTransactions: Int = 0,
    val todayGrossProfit: Double = 0.0,
    val topProducts: List<TopSellingItem> = emptyList(),
    val lowStockCount: Int = 0,
    val revenueTrend: List<DayRevenue> = emptyList(),
    /** Persentase perubahan total omzet 7 hari terakhir dibanding 7 hari SEBELUM itu (mis. 12.5
     * = naik 12.5%, -8.0 = turun 8%). Null kalau periode sebelumnya tidak ada omzet sama sekali
     * (pembagi nol) — badge tren disembunyikan pada kondisi ini. */
    val revenueTrendChangePercent: Double? = null,
    val isAdmin: Boolean = true,
    // Menu "Produk" (termasuk harga beli/margin) — ADMIN & MANAGER, KASIR ditolak. Sebelumnya
    // menu ini tampil untuk semua role tanpa syarat (audit 2026-09-06).
    val canManageProducts: Boolean = true,
    val isLoading: Boolean = true
)

/** Total omzet untuk satu hari, dipakai grafik tren 7 hari terakhir di Dashboard. */
data class DayRevenue(val label: String, val date: java.util.Date, val revenue: Double)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager,
    private val storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Muat ringkasan awal, lalu berlangganan perubahan transaksi/stok/sesi/profil toko
        // secara reaktif (Room Flow + DataStore Flow) agar Dashboard langsung update setelah
        // checkout atau ganti nama/logo toko tanpa perlu menutup-buka ulang aplikasi.
        viewModelScope.launch {
            combine(
                sessionManager.currentUser,
                productRepository.observeLowStock(),
                transactionRepository.observeAll(),
                storeProfileRepository.profile
            ) { user, lowStock, _, storeProfile -> Triple(user, lowStock, storeProfile) }
                .collect { (user, lowStock, storeProfile) ->
                    _uiState.value = _uiState.value.copy(
                        cashierName = user?.name,
                        isAdmin = user == null || user.role == UserRole.ADMIN,
                        canManageProducts = Permission.canManageProducts(user, storeProfile.pinLoginEnabled),
                        lowStockCount = lowStock.size,
                        storeName = storeProfile.name,
                        storeLogoPath = storeProfile.logoImagePath
                    )
                    loadSummary()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadSummary() }
    }

    private suspend fun loadSummary() {
        val (start, end) = todayRange()
        val summary = transactionRepository.getSalesSummary(start, end)
        val top = transactionRepository.getTopSellingItems(start, end, limit = 5)
        val trend = loadRevenueTrend()
        _uiState.value = _uiState.value.copy(
            todayRevenue = summary.totalRevenue,
            todayTransactions = summary.totalTransactions,
            todayGrossProfit = summary.totalGrossProfit,
            topProducts = top,
            revenueTrend = trend,
            revenueTrendChangePercent = trendChangePercent(trend),
            isLoading = false
        )
    }

    /** Bandingkan total omzet 7 hari (yang sudah dimuat di [trend]) terhadap total omzet 7 hari
     * SEBELUM periode itu, untuk badge naik/turun di atas grafik tren Dashboard. */
    private suspend fun trendChangePercent(trend: List<DayRevenue>): Double? {
        if (trend.isEmpty()) return null
        val currentTotal = trend.sumOf { it.revenue }
        val cal = Calendar.getInstance()
        cal.time = trend.first().date
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val currentPeriodStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, -trend.size)
        val previousPeriodStart = cal.timeInMillis
        val previousPeriodEnd = currentPeriodStart - 1
        val previousSummary = transactionRepository.getSalesSummary(previousPeriodStart, previousPeriodEnd)
        val previousTotal = previousSummary.totalRevenue
        if (previousTotal <= 0.0) return null
        return ((currentTotal - previousTotal) / previousTotal) * 100.0
    }

    /** Omzet 7 hari terakhir (termasuk hari ini), untuk grafik tren mini di Dashboard. */
    private suspend fun loadRevenueTrend(): List<DayRevenue> {
        val labelFormat = java.text.SimpleDateFormat("EEE", Locale("in", "ID"))
        val results = mutableListOf<DayRevenue>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -6)
        repeat(7) {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1
            val summary = transactionRepository.getSalesSummary(dayStart, dayEnd)
            results.add(DayRevenue(label = labelFormat.format(java.util.Date(dayStart)), date = java.util.Date(dayStart), revenue = summary.totalRevenue))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return results
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }
}
