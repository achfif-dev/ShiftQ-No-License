# Tambahkan aturan ProGuard khusus di sini.
-keep class com.example.posapp.data.local.entity.** { *; }
-keep class com.dantsu.escposprinter.** { *; }

# WAJIB: AppDatabase.Converters pakai PaymentMethod.valueOf()/UserRole.valueOf()/
# ExpensePeriod.valueOf()/ShiftStatus.valueOf() sebagai Room TypeConverter. Tanpa aturan ini,
# R8 (isMinifyEnabled=true di release) men-strip values()/valueOf() milik enum sehingga APK
# Release force-close langsung saat dibuka (root cause bug 24 Aug, sempat hilang lagi dari
# proguard-rules.pro di build berikutnya — jangan dihapus lagi).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
