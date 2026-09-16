package com.example.posapp.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Mengirim snapshot katalog produk+stok cabang ini ke Firestore setiap beberapa jam, HANYA
 * kalau "Sinkronisasi Cloud" aktif di Pengaturan (dipakai ulang toggle yang sama dengan ringkasan
 * omzet cabang — lihat CloudSyncRepository) supaya tidak ada data terkirim tanpa persetujuan
 * eksplisit admin. Lihat batasan lingkup fitur ini di [OutletCatalogSyncRepository].
 */
@HiltWorker
class OutletCatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val storeProfileRepository: StoreProfileRepository,
    private val productDao: ProductDao,
    private val catalogSyncRepository: OutletCatalogSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profile = storeProfileRepository.profile.first()
        if (!profile.cloudSyncEnabled) return Result.success()
        if (!catalogSyncRepository.isConfigured()) return Result.success()

        val outletId = storeProfileRepository.ensureOutletId()
        val products = productDao.getAllForExport()
        val rows = products.map { p ->
            OutletProductRow(
                outletId = outletId,
                outletName = profile.outletName,
                sku = p.sku,
                name = p.name,
                stock = p.stock,
                sellPrice = p.sellPrice,
                updatedAt = System.currentTimeMillis(),
            )
        }
        val success = catalogSyncRepository.pushCatalog(outletId, profile.outletName, rows)
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "outlet_catalog_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<OutletCatalogSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
