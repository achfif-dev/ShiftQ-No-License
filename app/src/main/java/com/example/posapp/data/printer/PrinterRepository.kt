package com.example.posapp.data.printer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.export.ReceiptStrings
import com.example.posapp.data.settings.StoreProfile
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class PrintResult {
    object Success : PrintResult()
    data class Error(val message: String) : PrintResult()
}

/** Jenis koneksi printer yang didukung. LAN/WiFi & USB ditambahkan supaya toko dengan printer
 * thermal non-Bluetooth (umum dipakai printer kasir 80mm kantor/resto) tidak perlu ganti alat. */
enum class PrinterConnectionType { BLUETOOTH, LAN, USB }

/** Konfigurasi printer tersimpan di Pengaturan > Profil Toko — lihat StoreProfileRepository. */
data class PrinterConfig(
    val type: PrinterConnectionType = PrinterConnectionType.BLUETOOTH,
    val bluetoothName: String? = null,
    val lanIpAddress: String = "",
    val lanPort: Int = 9100,
    val paperWidthMm: Float = 48f, // 48mm ~ printer 58mm umum; pakai 72f untuk printer 80mm
)

/** Konversi dari StoreProfile (DataStore) ke [PrinterConfig] yang dipakai [PrinterRepository]. */
fun StoreProfile.toPrinterConfig(): PrinterConfig = PrinterConfig(
    type = runCatching { PrinterConnectionType.valueOf(printerConnectionType) }.getOrDefault(PrinterConnectionType.BLUETOOTH),
    bluetoothName = selectedPrinterName,
    lanIpAddress = printerLanIp,
    lanPort = printerLanPort,
    paperWidthMm = printerPaperWidthMm,
)

/**
 * Menangani koneksi & pencetakan struk ke thermal printer ESC/POS lewat Bluetooth, LAN/WiFi
 * (TCP raw port 9100, standar hampir semua printer thermal jaringan/label), atau USB Host —
 * pakai library DantSu/ESCPOS-ThermalPrinter-Android yang sudah mendukung ketiganya.
 *
 * Alur:
 * 1. Bluetooth: pastikan printer sudah di-pair lewat Pengaturan Bluetooth Android dulu.
 * 2. LAN/WiFi: cukup tahu IP printer di jaringan yang sama (biasa tercetak di struk tes printer/
 *    menu printer itu sendiri) — tidak perlu pairing apa pun.
 * 3. USB: sambungkan lewat kabel OTG, Android akan memunculkan dialog izin USB otomatis.
 */
@Singleton
class PrinterRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /** Cegah markup injection pada struk/label ESC/POS.
     *
     * TEMUAN KEAMANAN (audit ulang): [sb] di [printReceipt]/[printLabel] diparse oleh DSL
     * formatting library DantSu ([C], [L], [R], <b>, <img>, dst.) SETELAH string dibangun. Kalau
     * teks dari pengguna (nama produk, catatan meja/pesanan, footer struk, dst.) kebetulan
     * mengandung karakter `[`, `]`, `<`, `>` yang cocok dengan salah satu tag itu, parser bisa
     * salah menafsirkan sisa struk sebagai perintah format baru — merusak layout cetakan, atau
     * untuk tag `<img>`, mencoba decode string sembarangan sebagai data gambar heksadesimal
     * (berpotensi macet/lambat saat mencetak). Semua teks yang berasal dari input pengguna
     * (bukan literal tetap yang kita tulis sendiri di kode) WAJIB lewat fungsi ini dulu sebelum
     * disisipkan ke [sb] — konsisten dengan pola sanitasi yang sudah dipakai di
     * XlsxWriter/ExcelExporter untuk masalah serupa (di sana untuk formula injection, di sini
     * untuk markup injection).
     */
    private fun sanitizeForReceipt(text: String): String = text.replace(MARKUP_TRIGGER_CHARS, "")

    private val MARKUP_TRIGGER_CHARS = Regex("[\\[\\]<>]")

    /** Nama printer Bluetooth yang sudah di-pair di sistem (untuk ditampilkan sebagai pilihan). */
    fun listPairedBluetoothPrinters(): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            BluetoothPrintersConnections().list?.map { it.device.name ?: "Unknown Printer" } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Daftar printer USB yang terdeteksi tersambung (belum tentu sudah diberi izin akses). */
    fun listUsbPrinters(): List<String> {
        return try {
            UsbPrintersConnections(context).list?.map { it.device.deviceName } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Sebelumnya SELALU memakai [BluetoothPrintersConnections.selectFirstPaired] walau daftar
     * printer bisa lebih dari satu — kalau toko punya >1 printer, pengguna tidak pernah bisa
     * benar-benar memilih yang mana dipakai. Sekarang cocokkan dulu dengan nama tersimpan;
     * kalau tidak diisi/tidak ketemu (mis. printer itu sudah di-unpair), fallback ke yang
     * ter-pairing pertama seperti biasa.
     */
    private fun selectBluetoothConnection(printerName: String?): BluetoothConnection? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val allPaired = try {
            BluetoothPrintersConnections().list
        } catch (e: Exception) {
            null
        }
        if (!printerName.isNullOrBlank()) {
            allPaired?.firstOrNull { it.device.name == printerName }?.let { return it }
        }
        return BluetoothPrintersConnections.selectFirstPaired()
    }

    private fun resolveConnection(config: PrinterConfig): DeviceConnection? = when (config.type) {
        PrinterConnectionType.BLUETOOTH -> selectBluetoothConnection(config.bluetoothName)
        PrinterConnectionType.LAN -> {
            if (config.lanIpAddress.isBlank()) null
            // BUG PENTING (ditemukan saat audit ulang): parameter timeout TcpConnection library
            // DantSu satuannya MILIDETIK (lihat contoh resmi & issue tracker library ini —
            // `TcpConnection(ip, port, 60000)` untuk timeout 60 detik), BUKAN detik seperti yang
            // saya kira sebelumnya. Nilai lama (15) berarti timeout 15 MILIDETIK — nyaris pasti
            // gagal connect ke printer LAN manapun karena jaringan butuh lebih dari itu untuk
            // handshake TCP. Diperbaiki jadi 15000 (15 detik, wajar untuk jaringan lokal/WiFi).
            else TcpConnection(config.lanIpAddress.trim(), config.lanPort, 15000)
        }
        PrinterConnectionType.USB -> try {
            UsbPrintersConnections(context).list?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun printReceipt(
        storeName: String,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        storeAddress: String = "",
        receiptFooter: String = "Terima kasih!",
        logoImagePath: String? = null,
        language: String = "id",
        printerConfig: PrinterConfig = PrinterConfig(),
        headerNote: String = "",
        showSku: Boolean = false,
        skuByProductId: Map<Long, String> = emptyMap()
    ): PrintResult {
        val strings = ReceiptStrings.forLanguage(language)
        if (printerConfig.type == PrinterConnectionType.BLUETOOTH &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return PrintResult.Error("Izin Bluetooth belum diberikan. Aktifkan izin Bluetooth di pengaturan aplikasi.")
        }
        var activeConnection: com.dantsu.escposprinter.connection.DeviceConnection? = null
        return try {
            val connection = resolveConnection(printerConfig) ?: return PrintResult.Error(
                when (printerConfig.type) {
                    PrinterConnectionType.BLUETOOTH -> "Tidak ada printer Bluetooth yang terpasang/di-pair"
                    PrinterConnectionType.LAN -> "Isi alamat IP printer LAN/WiFi dulu di Pengaturan > Profil Toko"
                    PrinterConnectionType.USB -> "Tidak ada printer USB yang terdeteksi. Cek kabel OTG & izin akses USB."
                }
            )
            activeConnection = connection

            // 384 dots (48mm) ~ printer thermal 58mm umum. paperWidthMm 72f -> printer 80mm.
            val charsPerLine = if (printerConfig.paperWidthMm >= 70f) 48 else 32
            val printer = EscPosPrinter(connection, 203, printerConfig.paperWidthMm, charsPerLine)

            val sb = StringBuilder()
            val logoBitmap = logoImagePath?.let { path ->
                try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
            }
            if (logoBitmap != null) {
                val maxWidth = 280
                val scaledLogo = if (logoBitmap.width > maxWidth) {
                    val scale = maxWidth.toFloat() / logoBitmap.width
                    Bitmap.createScaledBitmap(logoBitmap, maxWidth, (logoBitmap.height * scale).toInt(), true)
                } else {
                    logoBitmap
                }
                sb.append("[C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, scaledLogo)}</img>\n")
            }
            sb.append("[C]<b>${sanitizeForReceipt(storeName)}</b>\n")
            if (storeAddress.isNotBlank()) sb.append("[C]${sanitizeForReceipt(storeAddress)}\n")
            if (headerNote.isNotBlank()) sb.append("[C]${sanitizeForReceipt(headerNote)}\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[L]No: ${transaction.invoiceNumber}\n")
            transaction.note?.takeIf { it.isNotBlank() }?.let { sb.append("[L]Meja/Pesanan: ${sanitizeForReceipt(it)}\n") }
            sb.append("[L]${dateFormat.format(Date(transaction.createdAt))}\n")
            sb.append("[C]--------------------------------\n")

            items.forEach { item ->
                sb.append("[L]${sanitizeForReceipt(item.productNameSnapshot)}\n")
                if (showSku) {
                    skuByProductId[item.productId]?.takeIf { it.isNotBlank() }?.let { sku ->
                        sb.append("[L]SKU: ${sanitizeForReceipt(sku)}\n")
                    }
                }
                sb.append("[L]${item.quantity} ${sanitizeForReceipt(item.unitSnapshot)} x ${rupiah.format(item.priceSnapshot)}[R]${rupiah.format(item.lineTotal)}\n")
            }

            sb.append("[C]--------------------------------\n")
            sb.append("[L]${strings.subtotal}[R]${rupiah.format(transaction.subtotal)}\n")
            sb.append("[L]${strings.discount}[R]-${rupiah.format(transaction.discountAmount)}\n")
            if (transaction.taxPercent > 0.0) {
                sb.append("[L]${strings.tax} (${transaction.taxPercent}%)[R]${rupiah.format(transaction.taxAmount)}\n")
            }
            sb.append("[L]<b>${strings.total}</b>[R]<b>${rupiah.format(transaction.total)}</b>\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[L]${strings.paid} (${paymentLabel(transaction.paymentMethod, language)})[R]${rupiah.format(transaction.amountPaid)}\n")
            sb.append("[L]${strings.change}[R]${rupiah.format(transaction.changeAmount)}\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[C]${sanitizeForReceipt(receiptFooter)}\n")
            sb.append("[L]\n")

            printer.printFormattedTextAndCut(sb.toString())
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "Gagal mencetak struk. Pastikan printer menyala dan terhubung.")
        } finally {
            // Cegah kebocoran resource/socket (Bluetooth/TCP/USB) -- tanpa ini, koneksi tetap
            // terbuka setelah setiap cetak, dan mencetak berkali-kali dalam satu sesi app yang
            // sama (kasir mencetak banyak struk seharian tanpa restart app) bisa menghabiskan
            // file descriptor / gagal connect ulang ("socket already in use").
            runCatching { activeConnection?.disconnect() }
        }
    }

    /**
     * Cetak SATU label harga/barcode untuk [product] (v15) — dipanggil berulang oleh
     * LabelPrintViewModel kalau kasir minta beberapa lembar/produk sekaligus, karena library
     * ESC/POS ini mencetak per-perintah (tidak ada API "cetak N salinan" bawaan).
     *
     * Barcode digambar sendiri lewat ZXing (Code 128, dari [ProductEntity.sku]) lalu dicetak
     * sebagai gambar mentah — pola SAMA seperti logo toko di [printReceipt] — dan BUKAN lewat
     * tag `<barcode>` bawaan parser DantSu, supaya perilakunya konsisten & sudah terbukti bekerja
     * di codebase ini (lihat cara logo dicetak) daripada bergantung ke fitur library yang belum
     * pernah dipakai/diuji di sini.
     */
    fun printLabel(
        storeName: String,
        product: ProductEntity,
        printerConfig: PrinterConfig = PrinterConfig()
    ): PrintResult {
        if (printerConfig.type == PrinterConnectionType.BLUETOOTH &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return PrintResult.Error("Izin Bluetooth belum diberikan. Aktifkan izin Bluetooth di pengaturan aplikasi.")
        }
        var activeConnection: com.dantsu.escposprinter.connection.DeviceConnection? = null
        return try {
            val connection = resolveConnection(printerConfig) ?: return PrintResult.Error(
                when (printerConfig.type) {
                    PrinterConnectionType.BLUETOOTH -> "Tidak ada printer Bluetooth yang terpasang/di-pair"
                    PrinterConnectionType.LAN -> "Isi alamat IP printer LAN/WiFi dulu di Pengaturan > Profil Toko"
                    PrinterConnectionType.USB -> "Tidak ada printer USB yang terdeteksi. Cek kabel OTG & izin akses USB."
                }
            )
            activeConnection = connection
            val charsPerLine = if (printerConfig.paperWidthMm >= 70f) 48 else 32
            val printer = EscPosPrinter(connection, 203, printerConfig.paperWidthMm, charsPerLine)

            val barcodeWidth = if (printerConfig.paperWidthMm >= 70f) 380 else 260
            val barcodeBitmap = generateBarcodeBitmap(product.sku, barcodeWidth, 100)
                ?: return PrintResult.Error("SKU/barcode produk ini tidak valid untuk dicetak sebagai barcode")

            val sb = StringBuilder()
            sb.append("[C]${sanitizeForReceipt(storeName)}\n")
            sb.append("[C]<b>${sanitizeForReceipt(product.name)}</b>\n")
            sb.append("[C]${rupiah.format(product.sellPrice)}\n")
            sb.append("[C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, barcodeBitmap)}</img>\n")
            sb.append("[C]${sanitizeForReceipt(product.sku)}\n")
            sb.append("[L]\n")

            printer.printFormattedTextAndCut(sb.toString())
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "Gagal mencetak label. Pastikan printer menyala dan terhubung.")
        } finally {
            runCatching { activeConnection?.disconnect() }
        }
    }

    /** Barcode Code 128 hitam-putih murni — format ini menerima huruf+angka sehingga cocok untuk
     * SKU bebas (beda dari EAN-13 yang wajib 12-13 digit angka saja). */
    private fun generateBarcodeBitmap(data: String, widthPx: Int, heightPx: Int): Bitmap? {
        if (data.isBlank()) return null
        return try {
            val matrix = MultiFormatWriter().encode(data, BarcodeFormat.CODE_128, widthPx, heightPx)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            for (x in 0 until widthPx) {
                for (y in 0 until heightPx) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun paymentLabel(method: PaymentMethod, language: String = "id"): String = when (method) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.DEBIT_CREDIT -> "Debit/Kredit"
        PaymentMethod.QRIS -> "QRIS"
        PaymentMethod.BON -> if (language == "en") "Store Credit" else "Bon"
        PaymentMethod.MIXED -> if (language == "en") "Mixed" else "Campuran"
    }
}
