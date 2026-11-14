// Hreinsa ("cleanse"): takes tracking out of shared links and location and camera details out of shared photos.
// On the phone only: no internet permission, no libraries beyond the Kotlin standard library.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.ozorhaar.hreinsa"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ozorhaar.hreinsa"
        minSdk = 31
        targetSdk = 36
        versionCode = ciRun
        versionName = "1.0.$ciRun"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("YGG_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("YGG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YGG_KEY_ALIAS") ?: "yggdrasil"
                keyPassword = System.getenv("YGG_KEYSTORE_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
