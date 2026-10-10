plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vidsource.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vidsource.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // Signed with VIDSOURCE_KEYSTORE_* env vars when available (CI),
            // otherwise the release build falls back to debug signing so it
            // stays installable for sideloading.
            val ksPath = System.getenv("VIDSOURCE_KEYSTORE_FILE")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("VIDSOURCE_KEYSTORE_PASSWORD") ?: "vidsource"
                keyAlias = System.getenv("VIDSOURCE_KEY_ALIAS") ?: "vidsource"
                keyPassword = System.getenv("VIDSOURCE_KEY_PASSWORD") ?: "vidsource"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").let { cfg ->
                if (cfg.storeFile != null) cfg else signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Native playback (HLS / DASH / Progressive) with TV-friendly controls.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
