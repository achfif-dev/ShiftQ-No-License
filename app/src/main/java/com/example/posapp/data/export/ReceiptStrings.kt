package com.example.posapp.data.export

/**
 * Label struk cetak (PrinterRepository) & PDF invoice (PdfInvoiceGenerator), tersedia dalam
 * Bahasa Indonesia & English — dipilih lewat Pengaturan > Profil Toko > Bahasa Struk & Invoice.
 * Ini HANYA menerjemahkan struk/invoice, bukan seluruh tampilan aplikasi.
 */
data class ReceiptStrings(
    val subtotal: String,
    val discount: String,
    val tax: String,
    val total: String,
    val paid: String,
    val change: String,
    val defaultFooter: String
) {
    companion object {
        val INDONESIAN = ReceiptStrings(
            subtotal = "Subtotal",
            discount = "Diskon",
            tax = "Pajak",
            total = "Total",
            paid = "Bayar",
            change = "Kembali",
            defaultFooter = "Terima kasih!"
        )
        val ENGLISH = ReceiptStrings(
            subtotal = "Subtotal",
            discount = "Discount",
            tax = "Tax",
            total = "Total",
            paid = "Paid",
            change = "Change",
            defaultFooter = "Thank you!"
        )

        /** @param languageCode "id" atau "en" (StoreProfile.receiptLanguage); default Indonesia bila tidak dikenal. */
        fun forLanguage(languageCode: String?): ReceiptStrings = if (languageCode == "en") ENGLISH else INDONESIAN
    }
}
