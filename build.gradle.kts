plugins {
    // AGP 9.0 punya "built-in Kotlin support" -- plugin org.jetbrains.kotlin.android TERPISAH
    // yang dulu dipakai project ini SUDAH TIDAK KOMPATIBEL dengan AGP 9 (lihat komentar di
    // app/build.gradle.kts), jadi SENGAJA tidak didaftarkan lagi di sini. AGP 9.0 secara default
    // otomatis memakai Kotlin Gradle Plugin 2.2.10 secara internal (tidak perlu dideklarasikan
    // manual) -- kita ikut versi default ini (bukan versi Kotlin yang lebih baru) supaya
    // migrasi tetap di jalur yang paling banyak diuji & didokumentasikan resmi oleh Google/JetBrains.
    id("com.android.application") version "9.0.1" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    // v: migrasi API 36 (lanjutan) -- KSP 2.2.10-2.0.2 (percobaan pertama) ternyata masih pakai
    // cara lama (`kotlin.sourceSets`) untuk mendaftarkan source yang di-generate, yang sudah
    // TIDAK DIIZINKAN AGP 9 built-in Kotlin ("Using kotlin.sourceSets DSL ... not allowed").
    // Dukungan resmi utk built-in Kotlin baru ditambahkan KSP mulai versi 2.3.1 -- naik ke 2.3.9
    // (versi standalone terbaru, KSP sudah tidak lagi terikat ke nomor versi Kotlin sejak 2.3.0).
    id("com.google.devtools.ksp") version "2.3.9" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    // Plugin compiler Compose (v2.x+) & kotlinx-serialization -- versinya HARUS PERSIS SAMA
    // dengan versi Kotlin Gradle Plugin yang dipakai (2.2.10, default built-in AGP 9.0 di atas).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
