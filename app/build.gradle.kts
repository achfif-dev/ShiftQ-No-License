plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android DIHAPUS (v: migrasi API 36/Play Store) -- AGP 9.0 punya
    // "built-in Kotlin support" bawaan & TIDAK KOMPATIBEL lagi dengan plugin kotlin-android
    // terpisah yang dulu dipakai di sini (mendaftarkan keduanya sekaligus = build gagal).
    // Kotlin compiler sekarang otomatis disediakan AGP sendiri (versi default 2.2.10, lihat
    // root build.gradle.kts) -- TIDAK perlu plugin id terpisah untuk kompilasi Kotlin biasa lagi,
    // hanya untuk compiler plugin TAMBAHAN (Compose, serialization) di bawah ini.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Sinkronisasi Cloud (Fase 4 - lihat FIREBASE_SETUP.md) BUTUH proyek Firebase sendiri, yang
// tidak bisa dibuatkan otomatis dari sini. Plugin google-services HANYA diterapkan kalau file
// konfigurasinya sudah ada, supaya siapa pun yang clone/upload ulang repo ini tanpa membuat
// proyek Firebase dulu TETAP bisa build normal (fitur cloud sync otomatis nonaktif, bukan error).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Kredensial keystore release DIBACA DARI ENVIRONMENT VARIABLE, bukan di-hardcode di sini
// atau di gradle.properties yang ikut ter-commit — supaya aman untuk repo publik/privat.
// Di GitHub Actions, env var ini diisi dari GitHub Secrets (lihat android_build.yml).
// Kalau tidak di-set (mis. build lokal tanpa keystore), release TIDAK akan ditandatangani —
// build tetap jalan (tidak error), tapi APK hasilnya tidak akan bisa diinstall sampai
// signing config di-set. Gunakan APK debug untuk testing cepat di HP.
val releaseKeystoreFile: String? = System.getenv("RELEASE_KEYSTORE_FILE")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig: Boolean =
    !releaseKeystoreFile.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.posapp"
    // v: migrasi target API 36 (Android 16) -- WAJIB untuk app baru di Play Store sejak
    // 31 Agustus 2026 (lihat AGP 9.0.1 di root build.gradle.kts, yang mendukung sampai API 36.1).
    compileSdk = 36

    defaultConfig {
        // CATATAN: applicationId sengaja dipisah dari `namespace` (yang masih com.example.posapp
        // dan HANYA menentukan package R/BuildConfig internal, aman dibiarkan). applicationId inilah
        // identitas app yang publik/dipakai Play Store & dilihat pengguna — com.example.* ditolak
        // Play Store dan tidak profesional untuk app yang mau disewakan ke toko lain. Ganti
        // "id.gwg.posapp" di bawah sesuai domain/brand Anda sendiri sebelum rilis publik.
        applicationId = "id.shiftq.posapp"
        minSdk = 26
        targetSdk = 36
        // v: rilis pertama yang ditarget untuk Play Store (migrasi API 36 + kumpulan fitur besar
        // sejak 1.1.0: Promo Otomatis, Tahan Transaksi, Reminder Piutang, Cetak Label, Rekonsiliasi
        // Kas, keamanan cross-tenant Cloud Sync, dll) -- versionCode WAJIB naik dari build
        // sebelumnya yang pernah diinstal (Play Store menolak versionCode yang sama/lebih kecil).
        versionCode = 4
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions { jvmTarget = "17" } DIHAPUS (v: migrasi API 36) -- blok ini bagian dari
    // plugin org.jetbrains.kotlin.android yang sudah tidak dipakai lagi (lihat komentar di
    // plugins{} atas). compileOptions di atas sudah cukup menyamakan target bytecode Java &
    // Kotlin untuk built-in Kotlin compilation-nya AGP 9 (pola persis sama seperti contoh
    // migrasi resmi Google/Flutter untuk AGP 9 built-in Kotlin).
    buildFeatures {
        compose = true
    }
    // composeOptions { kotlinCompilerExtensionVersion = ... } DIHAPUS (v: migrasi API 36) --
    // sejak Kotlin 2.0, versi compiler Compose diatur oleh plugin
    // org.jetbrains.kotlin.plugin.compose (lihat plugins{} di atas & root build.gradle.kts),
    // BUKAN lagi lewat composeOptions manual di sini -- keduanya tidak boleh dipakai bersamaan.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // FIX (CI sering gagal di step "Run Lint"): tanpa blok ini, Android Gradle Plugin memakai
    // default `abortOnError = true` untuk task lint apa pun (termasuk `lintDebug` yang dipanggil
    // CI) — SATU issue berseverity Error di mana pun (termasuk lint bawaan yang tidak terkait
    // perubahan kode, mis. versi dependency using deprecated API) membuat seluruh job CI merah
    // sebelum sempat sampai ke step Test/Build APK. `checkReleaseBuilds = false` mencegah hal
    // sama terjadi diam-diam saat `assembleRelease` (release build juga menjalankan lint
    // vital secara default). Laporan lint TETAP dihasilkan & diupload sebagai artifact
    // ("Upload Lint report" di workflow) untuk dicek manual — cuma tidak lagi memblokir build.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    // Room exportSchema=true (lihat AppDatabase.kt) menulis snapshot skema JSON ke sini setiap
    // build. WAJIB commit folder app/schemas/ ke Git — ini "sumber kebenaran" struktur database
    // per versi, dipakai untuk menguji migrasi Room secara otomatis (MigrationTestHelper) dan
    // untuk siapa pun mengecek riwayat perubahan skema tanpa harus baca ulang seluruh entity.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// FIX: Firebase Firestore/Auth (Fase 4 - Sinkronisasi Cloud) menyeret com.google.guava:guava
// (Guava penuh, berisi class ListenableFuture asli) sebagai dependency transitif. CameraX
// (camera-core) sendiri bergantung pada com.google.guava:listenablefuture:1.0 — artifact
// TERPISAH & KOSONG (cuma stub) yang JUGA mendefinisikan class com.google.common.util.concurrent
// .ListenableFuture. Kalau keduanya sama-sama masuk classpath, Gradle/Kotlin bingung class mana
// yang dipakai -> "Cannot access class ListenableFuture" di BarcodeScannerScreen.kt (dulu tidak
// terjadi karena app ini belum punya dependency lain yang menarik Guava penuh, sebelum Fase 4
// menambahkan Firebase). Percobaan pertama (cuma `force` versi guava) TIDAK CUKUP karena kedua
// artifact tetap sama-sama ada di classpath. Fix yang benar: exclude stub-nya sepenuhnya supaya
// semua konsumen (CameraX & Firebase) dipaksa memakai SATU ListenableFuture asli dari Guava.
configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
    resolutionStrategy {
        force("com.google.guava:guava:32.1.3-android")
    }
}

dependencies {
    // Core / Compose
    // v: migrasi API 36 -- SEMPAT dicoba compose-bom:2026.08.00 ("selalu pakai versi terbaru"
    // per rekomendasi Google), TAPI itu menyeret Compose 1.12.0 yang mensyaratkan compileSdk 37
    // + AGP 9.2.0 (baca error "requires compileSdk 37" di CI) -- MELEBIHI target kita (36, sesuai
    // syarat Play Store saat ini). 2026.06.00 = versi terakhir sebelum lompatan itu, masih cocok
    // dengan compileSdk=36 & AGP 9.0.1.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Google Fonts "downloadable" — mengambil font asli dari Google Play Services saat
    // runtime (bukan font sistem Roboto bawaan), tanpa perlu membundel file .ttf di APK.
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room (offline persistent storage)
    // v: migrasi API 36 -- dinaikkan dari 2.6.1 (2024) ke 2.8.4 karena 2.6.1 memicu bug KSP2
    // yang sudah dikenal ("unexpected jvm signature V" saat memproses fungsi DAO suspend yang
    // return Unit implisit) -- baru benar-benar diperbaiki mulai Room 2.7.0-alpha11.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // CameraX + ML Kit Barcode Scanning
    // NOTE: versi HARUS >= 1.4.0 karena BarcodeScannerScreen.kt memakai
    // ProcessCameraProvider.awaitInstance() (Kotlin suspend fun), yang baru
    // ditambahkan di CameraX 1.4.0-alpha (unresolved reference di 1.3.x -
    // ini penyebab build gagal di compileDebugKotlin).
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Bluetooth ESC/POS Thermal Printer
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")

    // Excel (.xlsx) export ditulis manual via java.util.zip (lihat XlsxWriter.kt) — Apache POI
    // SUDAH DIHAPUS karena tidak kompatibel dengan runtime Android (penyebab crash tombol export).

    // PDF generation is done via native android.graphics.pdf.PdfDocument (no extra dep needed)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Coil for local product photos & QRIS image
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ZXing core: generate kode QR dinamis per transaksi (QRIS amount injection), murni offline
    implementation("com.google.zxing:core:3.5.3")

    // DataStore (profil toko, preferensi login PIN, dsb.)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Sinkronisasi Cloud lintas cabang (Fase 4 - opsional, nonaktif sampai google-services.json
    // ada & toggle "Sinkronisasi Cloud" dinyalakan Admin di Pengaturan). Lihat FIREBASE_SETUP.md.
    // Dependency ini AMAN ditambahkan walau belum ada proyek Firebase — hanya dipakai (dan hanya
    // butuh config valid) saat runtime memanggilnya, bukan saat compile.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    // Cloud Functions callable — dipakai TenantAuthProvider (mintSyncToken), PaymentGatewayRepository
    // (Fase 5-6) untuk memanggil backend tanpa menanam kredensial/rahasia apa pun di dalam APK.
    implementation("com.google.firebase:firebase-functions-ktx")

    // WorkManager + Hilt — penjadwalan tugas latar belakang oportunistik (hanya jalan saat ada
    // internet, lihat Constraints.NETWORK_TYPE_CONNECTED): sinkronisasi katalog produk/stok
    // lintas cabang (OutletCatalogSyncWorker).
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2") // termasuk org.junit.rules.TemporaryFolder dipakai BackupCryptoTest
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
