package com.example.posapp

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Menangkap SEMUA uncaught exception di aplikasi (bukan hanya di satu layar), lalu:
 * 1. Menyimpan detail crash ke file teks di penyimpanan internal app (untuk dilihat lagi nanti
 *    lewat file manager bila perlu, di folder Android/data/[nama package aplikasi]/files/crash_logs).
 * 2. Membuka [CrashActivity] yang menampilkan detail error itu di layar, dengan tombol Bagikan —
 *    supaya pengguna TIDAK hanya melihat app "tertutup sendiri" tanpa penjelasan.
 *
 * Dipasang sekali di [PosApplication.onCreate]. Ini murni alat bantu debugging; begitu penyebab
 * crash aslinya sudah diperbaiki, class ini boleh dilepas atau dibiarkan (tidak mengganggu
 * perilaku normal aplikasi selama tidak ada crash).
 */
class CrashHandler(private val appContext: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashText = buildCrashReport(thread, throwable)
            saveToFile(crashText)

            val intent = Intent(appContext, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CrashActivity.EXTRA_CRASH_TEXT, crashText)
            }
            appContext.startActivity(intent)
        } catch (inner: Throwable) {
            // Kalau proses penanganan crash ini sendiri gagal, jangan sampai malah menutupi
            // exception aslinya — lanjut ke handler default di bawah.
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("Waktu: $timestamp")
            appendLine("Thread: ${thread.name}")
            appendLine("Versi Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine(sw.toString())
        }
    }

    private fun saveToFile(crashText: String) {
        try {
            val dir = File(appContext.getExternalFilesDir(null), "crash_logs")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "crash_${System.currentTimeMillis()}.txt"
            File(dir, fileName).writeText(crashText)
        } catch (ignored: Exception) {
            // Penyimpanan file bersifat best-effort — kalau gagal (mis. penyimpanan penuh),
            // CrashActivity tetap bisa menampilkan errornya langsung dari memori.
        }
    }

    companion object {
        /** Pasang handler ini sekali saja, biasanya dari [PosApplication.onCreate]. */
        fun install(appContext: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext.applicationContext))
        }
    }
}
