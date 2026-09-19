package com.example.posapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.posapp.data.notification.LowStockNotificationWorker
import com.example.posapp.data.sync.OutletCatalogSyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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

        // v16 (audit): App Check dipasang SEBELUM komponen Firebase lain dipakai. Semua Cloud
        // Function callable sekarang dideploy dengan `enforceAppCheck: true` — tanpa inisialisasi
        // ini, seluruh fitur Cloud Sync & Payment Gateway akan ditolak server.
        //
        // PENTING SAAT DEPLOY: daftarkan aplikasi di Firebase Console > App Check (provider Play
        // Integrity) DULU, baru deploy functions. Urutan terbalik akan mematikan fitur cloud di
        // semua device sampai pendaftaran selesai. Build debug memakai provider debug —
        // tokennya muncul di Logcat dan harus didaftarkan manual di Console untuk uji coba.
        // Fail-soft: kegagalan di sini tidak boleh membuat app crash, karena semua fitur POS
        // inti (kasir, stok, laporan, cetak struk) berjalan sepenuhnya offline tanpa Firebase.
        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }

        // Idempotent (KEEP policy) — aman dipanggil setiap kali app start tanpa membuat job dobel.
        OutletCatalogSyncWorker.schedule(this)
        LowStockNotificationWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
