import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aryanmaheshwari.taskmanager"
    compileSdk = 36

    // Read Gemini API Key: check local.properties first, then fallback to environment variable
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val geminiApiKey = localProperties.getProperty("gemini.api.key")
        ?: System.getenv("GEMINI_API_KEY")
        ?: ""

    // Secure Release Signing:
    // 1. Check keystore.properties (for local development)
    // 2. Fall back to environment variables (for GitHub Actions CI/CD)
    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    val keystoreFilePath = keystoreProperties.getProperty("storeFile")
        ?: System.getenv("KEYSTORE_FILE")
        ?: "keystore.jks"
    val releaseStoreFile = if (File(keystoreFilePath).isAbsolute) {
        File(keystoreFilePath)
    } else {
        rootProject.file(keystoreFilePath)
    }

    val releaseStorePassword = keystoreProperties.getProperty("storePassword")
        ?: System.getenv("KEYSTORE_PASSWORD")

    val releaseKeyAlias = keystoreProperties.getProperty("keyAlias")
        ?: System.getenv("KEY_ALIAS")

    val releaseKeyPassword = keystoreProperties.getProperty("keyPassword")
        ?: System.getenv("KEY_PASSWORD")

    val isSigningConfigured = releaseStoreFile.exists() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    if (isSigningConfigured) {
        println("[Signing] Release signing configuration loaded successfully.")
    } else {
        println("[Signing] Note: Release signing not configured (keystore.properties or KEYSTORE_* env vars missing).")
    }

    // Version management:
    // - Local builds use base versionCode (11) or -PversionCode override
    // - CI builds automatically increment versionCode using GITHUB_RUN_NUMBER (11 + GITHUB_RUN_NUMBER)
    val baseVersionCode = 11
    val computedVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull()
        ?: System.getenv("VERSION_CODE")?.toIntOrNull()
        ?: System.getenv("GITHUB_RUN_NUMBER")?.let { baseVersionCode + it.toInt() }
        ?: baseVersionCode

    val computedVersionName = project.findProperty("versionName")?.toString()
        ?: System.getenv("VERSION_NAME")
        ?: "1.0.10"

    defaultConfig {
        applicationId = "com.aryanmaheshwari.taskmanager"
        minSdk = 24
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    signingConfigs {
        create("release") {
            if (isSigningConfigured) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            resValue("string", "banner_ad_unit_id", "ca-app-pub-3940256099942544/6300978111")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            manifestPlaceholders["admobAppId"] = "ca-app-pub-7573894623963915~5258689081"
            resValue("string", "banner_ad_unit_id", "ca-app-pub-7573894623963915/8806277771")
        }
        create("releaseDebug") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".releasedebug"
            versionNameSuffix = "-rd"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            resValue("string", "banner_ad_unit_id", "ca-app-pub-3940256099942544/6300978111")
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.cardview:cardview:1.0.0")

    // Retrofit + OkHttp for API calls
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.lottie)
}