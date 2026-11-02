plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.marc.albumartlock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marc.albumartlock"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // A fixed key kept in the repo so every build can be installed over the previous one.
    // It only identifies this sideloaded personal app; it is not a Play Store key.
    signingConfigs {
        create("sideload") {
            storeFile = file("sideload.keystore")
            storePassword = "albumartlock"
            keyAlias = "albumartlock"
            keyPassword = "albumartlock"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
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
