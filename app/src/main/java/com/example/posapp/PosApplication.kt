package com.example.posapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.posapp.data.notification.LowStockNotificationWorker
import com.example.posapp.data.sync.OutletCatalogSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PosApplication : Application(), Configuration.Provider {

    // Dipakai WorkManager (lihat androidx.startup di AndroidManifest.xml yang dimatikan untuk
    // initializer bawaan) supaya Worker ber-anotasi @HiltWorker (OutletCatalogSyncWorker) bisa
    // menerima dependency lewat constructor injection biasa.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Dipasang PALING AWAL (sebelum apapun lain di onCreate) supaya menangkap crash yang
        // terjadi sedini mungkin, termasuk saat inisialisasi dependency Hilt/Room/DataStore.
        CrashHandler.install(this)

        // Idempotent (KEEP policy) — aman dipanggil setiap kali app start tanpa membuat job dobel.
        OutletCatalogSyncWorker.schedule(this)
        LowStockNotificationWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
