package com.example.posapp.presentation.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Layar scan barcode produk menggunakan CameraX untuk preview + ML Kit untuk decode.
 * Memanggil [onBarcodeDetected] sekali saat barcode pertama berhasil dibaca, lalu berhenti
 * memproses frame berikutnya untuk mencegah callback berulang pada barcode yang sama.
 *
 * Tampilan dibuat edge-to-edge (bukan Scaffold + TopAppBar tebal) supaya preview kamera
 * memenuhi seluruh layar seperti aplikasi scanner profesional — kontrol (tombol kembali,
 * bingkai target, teks bantuan) mengambang tipis di atasnya.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
fun BarcodeScannerScreen(
    onBarcodeDetected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasHandledResult by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // PERBAIKAN BUG: sama seperti di ProductScreen -- dialog izin runtime bisa memicu
    // onStop/onStart MainActivity, jadi WAJIB expectExternalActivityReturn() sebelum
    // memintanya, baik saat auto-request pertama kali layar ini dibuka maupun saat pengguna
    // menekan tombol "Berikan Izin Kamera" secara manual di bawah.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            autoLockManager.expectExternalActivityReturn()
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            val previewView = remember { PreviewView(context) }
            val executor = remember { Executors.newSingleThreadExecutor() }
            val scanner = remember { BarcodeScanning.getClient() }

            DisposableEffect(Unit) {
                onDispose {
                    executor.shutdown()
                    scanner.close()
                }
            }

            // Dulu pakai ProcessCameraProvider.getInstance(ctx).addListener(...) yang
            // mengembalikan com.google.common.util.concurrent.ListenableFuture (Guava) —
            // gampang bentrok begitu dependency lain (mis. Firebase) menarik versi Guava
            // berbeda ke classpath ("Cannot access class ListenableFuture"). awaitInstance()
            // adalah API resmi CameraX yang membungkus Future itu di baliknya, jadi kode kita
            // sendiri TIDAK PERNAH menyentuh tipe ListenableFuture sama sekali -- kebal dari
            // konflik Guava di atas, apa pun versi yang akhirnya dipilih Gradle.
            LaunchedEffect(hasCameraPermission) {
                try {
                    val cameraProvider = ProcessCameraProvider.awaitInstance(context)

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !hasHandledResult) {
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes: List<Barcode> ->
                                    val value = barcodes.firstOrNull()?.rawValue
                                    if (value != null && !hasHandledResult) {
                                        hasHandledResult = true
                                        onBarcodeDetected(value)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    // Kamera gagal di-bind (mis. device tanpa kamera belakang) — abaikan,
                    // pengguna tetap bisa kembali dan input SKU manual.
                }
            }

            AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

            // Scrim gradasi tipis di atas & bawah agar kontrol tetap terbaca tanpa
            // menutupi preview kamera dengan bar solid tebal.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )

            ScanFrame(modifier = Modifier.align(Alignment.Center))

            Text(
                "Arahkan kamera ke barcode produk",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )

            // Tombol kembali minimalis — lingkaran tipis mengambang, bukan TopAppBar penuh.
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(16.dp))
                Text("Izin kamera diperlukan untuk scan barcode", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    autoLockManager.expectExternalActivityReturn()
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("Berikan Izin Kamera")
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
        }
    }
}

/**
 * Bingkai target berupa sudut-sudut (bracket corners) minimalis — gaya scanner profesional
 * modern, menggantikan kotak solid penuh yang terasa berat/kaku.
 */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val frameSize = 240.dp
    val cornerLength = 28.dp
    val strokeWidth = 3.dp
    val cornerRadius = 16.dp

    Box(modifier = modifier.size(frameSize)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = with(density) { strokeWidth.toPx() }
            val corner = with(density) { cornerLength.toPx() }
            val radius = with(density) { cornerRadius.toPx() }
            val w = size.width
            val h = size.height
            val color = Color.White

            // Sedikit terangkan tepi bingkai supaya area target terlihat jelas tanpa
            // perlu overlay solid tebal menutupi seluruh layar.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.dp.toPx())
            )

            // Sudut kiri-atas
            drawLine(color, Offset(0f, radius), Offset(0f, corner), strokeWidth = stroke)
            drawLine(color, Offset(radius, 0f), Offset(corner, 0f), strokeWidth = stroke)
            // Sudut kanan-atas
            drawLine(color, Offset(w, radius), Offset(w, corner), strokeWidth = stroke)
            drawLine(color, Offset(w - radius, 0f), Offset(w - corner, 0f), strokeWidth = stroke)
            // Sudut kiri-bawah
            drawLine(color, Offset(0f, h - radius), Offset(0f, h - corner), strokeWidth = stroke)
            drawLine(color, Offset(radius, h), Offset(corner, h), strokeWidth = stroke)
            // Sudut kanan-bawah
            drawLine(color, Offset(w, h - radius), Offset(w, h - corner), strokeWidth = stroke)
            drawLine(color, Offset(w - radius, h), Offset(w - corner, h), strokeWidth = stroke)
        }
    }
}
