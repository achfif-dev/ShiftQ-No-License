package com.example.posapp.data.qris

import android.graphics.BitmapFactory
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Membaca ulang gambar QRIS statis yang diunggah pemilik toko dan mengambil payload EMVCo
 * mentahnya (string di dalam kode QR), supaya nominal transaksi bisa disisipkan secara dinamis
 * lewat [QrisUtil]. Sepenuhnya on-device (ML Kit bundled), tidak butuh koneksi internet.
 */
@Singleton
class QrisImageDecoder @Inject constructor() {

    suspend fun decode(imagePath: String): String? = suspendCancellableCoroutine { cont ->
        val bitmap = try {
            BitmapFactory.decodeFile(imagePath)
        } catch (e: Exception) {
            null
        }
        if (bitmap == null) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val scanner = BarcodeScanning.getClient()
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                if (cont.isActive) cont.resume(barcodes.firstOrNull()?.rawValue)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }
}
