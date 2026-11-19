// Ráða ("read, interpret"): scans QR codes and shows where they really lead before anything opens.
// On the phone only: no internet permission; camera through CameraX, decoding through ZXing core.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.ozorhaar.rada"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ozorhaar.rada"
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
    implementation("androidx.activity:activity-ktx:1.9.3")
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
