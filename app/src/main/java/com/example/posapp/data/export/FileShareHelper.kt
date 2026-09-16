package com.example.posapp.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileShareHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Membuat Intent share (mis. via WhatsApp/Email/dll) untuk file hasil export. */
    fun createShareIntent(file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Menyalin isi [sourceFile] ke [destinationUri] yang dipilih pengguna lewat Storage Access
     * Framework (mis. dari ActivityResultContracts.CreateDocument), misalnya ke folder Download.
     * Ini yang menyediakan opsi "simpan langsung ke perangkat" — sebelumnya app cuma bisa
     * "bagikan" lewat aplikasi lain, tidak ada cara simpan file ke penyimpanan secara langsung.
     */
    fun saveToUri(sourceFile: File, destinationUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
    }
}
