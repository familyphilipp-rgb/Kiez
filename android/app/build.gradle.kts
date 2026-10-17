plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Adresse des Kiez-Servers; auf GitHub über die Variable KIEZ_SERVER_URL änderbar
val serverUrl = (System.getenv("KIEZ_SERVER_URL") ?: "").ifBlank { "https://kiez.ideenwerft-friedenau.de" }
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "de.ideenwerft.kiez"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.ideenwerft.kiez"
        minSdk = 28
        targetSdk = 35
        // Jede Build-Nummer zählt hoch, damit sich Updates über die alte Version installieren lassen.
        // +100, weil die frühere Web-Hülle schon Versionsnummern verbraucht hat.
        versionCode = 100 + buildNumber
        versionName = "2.0.$buildNumber"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    signingConfigs {
        // Fester Schlüssel im Repo, damit jede neue APK als Update installiert werden kann.
        // Für den Play Store einen eigenen, geheimen Schlüssel über KIEZ_KEYSTORE_* setzen.
        create("kiez") {
            storeFile = file(System.getenv("KIEZ_KEYSTORE_FILE") ?: "kiez-sideload.keystore")
            storePassword = System.getenv("KIEZ_KEYSTORE_PASSWORD") ?: "kiez-sideload"
            keyAlias = System.getenv("KIEZ_KEY_ALIAS") ?: "kiez"
            keyPassword = System.getenv("KIEZ_KEY_PASSWORD") ?: "kiez-sideload"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("kiez")
        }
        release {
            signingConfig = signingConfigs.getByName("kiez")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Bilder laden und zwischenspeichern
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    // Videos abspielen und zwischenspeichern
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
}
