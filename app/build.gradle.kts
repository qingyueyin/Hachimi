import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

val appVersion = "1.0.0"
val gitHash = gitOutput("rev-parse", "--short", "HEAD", fallback = "unknown")
val gitCommitCount = gitOutput("rev-list", "--count", "HEAD", fallback = "1")
    .toIntOrNull()?.coerceAtLeast(1) ?: 1
val officialCertSha256 = providers.gradleProperty("HACHIMI_CERT_SHA256")
    .orElse(providers.environmentVariable("HACHIMI_CERT_SHA256"))
    .getOrElse("")
    .trim()
    .lowercase()

val releaseKeystore = file("release.keystore")
val releaseStorePassword = providers.gradleProperty("HACHIMI_STORE_PASSWORD")
    .orElse(providers.environmentVariable("HACHIMI_STORE_PASSWORD"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("HACHIMI_KEY_PASSWORD")
    .orElse(providers.environmentVariable("HACHIMI_KEY_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("HACHIMI_KEY_ALIAS")
    .orElse(providers.environmentVariable("HACHIMI_KEY_ALIAS"))
    .orElse("hachimi")
    .get()
val releaseSigningReady = releaseKeystore.exists() &&
    !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.qing.hachimi"
    compileSdk = 37

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.qingyueyin.hachimi"
        minSdk = 33
        targetSdk = 33
        versionCode = gitCommitCount
        versionName = "$appVersion-$gitHash"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("int", "GIT_COMMIT_COUNT", "$gitCommitCount")
        buildConfigField("String", "OFFICIAL_CERT_SHA256", "\"$officialCertSha256\"")
        buildConfigField("String", "OFFICIAL_REPO", "\"https://github.com/qingyueyin/Hachimi\"")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-debug.pro"
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
        // AGP 9.1's ExperimentalDetector crashes on Kotlin 2.3 FIR symbols.
        disable += setOf("UnsafeOptInUsageError", "UnsafeOptInUsageWarning")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.navigation3.ui)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigationevent.compose)

    // Material Icons Extended (for settings page icons)
    implementation(libs.androidx.compose.material.icons.extended)

    // Material3 (for ripple, theme foundation)
    implementation(libs.androidx.compose.material3)

    // MaterialKolor for dynamic MD3 color theming
    implementation(libs.material.kolor)

    // Koin DI (following Lyrico architecture)
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.androidx.security.crypto)

    // jaudiotagger: pure Java audio metadata read/write (MP3 ID3v2, FLAC Vorbis Comment + Picture, etc.)
    implementation("net.jthink:jaudiotagger:3.0.1")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20240303")
}

fun gitOutput(vararg args: String, fallback: String): String {
    return try {
        providers.exec {
            commandLine(listOf("git") + args)
            workingDir(rootProject.rootDir)
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().ifEmpty { fallback }
    } catch (_: Exception) {
        fallback
    }
}

val releaseKeystorePath = layout.projectDirectory.file("release.keystore")
val storePasswordProvider = providers.gradleProperty("HACHIMI_STORE_PASSWORD")
    .orElse(providers.environmentVariable("HACHIMI_STORE_PASSWORD"))
val keyAliasProvider = providers.gradleProperty("HACHIMI_KEY_ALIAS")
    .orElse(providers.environmentVariable("HACHIMI_KEY_ALIAS"))
    .orElse("hachimi")

tasks.register("printReleaseCertSha256") {
    group = "help"
    description = "Print SHA-256 of app/release.keystore for HACHIMI_CERT_SHA256"
    val ksPath = releaseKeystorePath
    val storePassword = storePasswordProvider
    val aliasProvider = keyAliasProvider
    doLast {
        val ksFile = ksPath.asFile
        check(ksFile.isFile) { "Missing ${ksFile.path}" }
        val password = storePassword.orNull
        check(!password.isNullOrBlank()) { "Set HACHIMI_STORE_PASSWORD first" }
        val alias = aliasProvider.get()
        val keystore = KeyStore.getInstance(ksFile, password.toCharArray())
        val cert = keystore.getCertificate(alias) ?: error("Alias '$alias' not in keystore")
        val hex = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
        println(hex)
        println("Add to ~/.gradle/gradle.properties:")
        println("HACHIMI_CERT_SHA256=$hex")
    }
}
