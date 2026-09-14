plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yann.sportscomplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yann.sportscomplication"
        minSdk = 30 // Wear OS 4+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        // Keystore fixe committé dans le repo (wear/debug.keystore) pour que
        // chaque build GitHub Actions signe avec la MÊME clé. Sans ça, AGP
        // génère un keystore de debug aléatoire à chaque run CI, ce qui
        // provoque une erreur INSTALL_FAILED_UPDATE_INCOMPATIBLE dès qu'on
        // essaie d'installer une mise à jour par-dessus une version
        // précédente signée différemment.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
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
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.3.0")
}
