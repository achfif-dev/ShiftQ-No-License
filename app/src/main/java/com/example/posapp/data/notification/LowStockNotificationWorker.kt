package com.example.posapp.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.posapp.R
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Cek berkala produk stok tipis (v15) & tampilkan SATU notifikasi sistem Android kalau ada —
 * pelengkap kartu "Stok Tipis" di Dashboard yang sifatnya pasif (baru kelihatan kalau pemilik
 * membuka app). Dijadwalkan setiap 6 jam (cukup sering untuk toko harian, tidak mengganggu
 * baterai) — lihat [schedule]. Tidak melakukan apa pun kalau StoreProfile.lowStockNotificationsEnabled
 * nonaktif, atau izin notifikasi (Android 13+) belum diberikan — fail-soft, tidak pernah crash
 * background job hanya karena izin belum ada.
 */
@HiltWorker
class LowStockNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val productRepository: ProductRepository,
    private val storeProfileRepository: StoreProfileRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profile = storeProfileRepository.profile.first()
        if (!profile.lowStockNotificationsEnabled) return Result.success()

        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val lowStockProducts = productRepository.observeLowStock().first()
        if (lowStockProducts.isEmpty()) return Result.success()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stok Menipis",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Pemberitahuan saat ada produk yang stoknya sudah menipis" }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = openAppIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val names = lowStockProducts.take(3).joinToString(", ") { it.name }
        val moreCount = lowStockProducts.size - 3
        val body = if (moreCount > 0) "$names, dan $moreCount produk lainnya" else names

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${lowStockProducts.size} produk stoknya menipis")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "low_stock_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UNIQUE_WORK_NAME = "low_stock_notification_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LowStockNotificationWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
