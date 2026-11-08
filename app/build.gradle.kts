import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

val appVersion = "1.1.0"
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
    compileSdkMinor = 0

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
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "$appVersion-$gitHash"
        ndk {
            // arm64 手机 + x86_64（ChromeOS / 模拟器）
            abiFilters += listOf("arm64-v8a", "x86_64")
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
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-debug.pro"
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
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
    implementation(libs.miuix.nav.android)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigationevent.compose)

    // Material Icons Extended (for settings page icons)
    implementation(libs.androidx.compose.material.icons.extended)

    // Material3 (for ripple, theme foundation)
    implementation(libs.androidx.compose.material3)

    // MaterialKolor for dynamic MD3 color theming
    implementation(libs.material.kolor)

    // Koin DI (following Lyrico architecture)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.androidx.security.crypto)

    // jaudiotagger: pure Java audio metadata read/write (MP3 ID3v2, FLAC Vorbis Comment + Picture, etc.)
    implementation(libs.jaudiotagger)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
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
