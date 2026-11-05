plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.envio.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.envio.app"
        minSdk = 26
        targetSdk = 34
        // En GitHub el numero de compilacion sube solo, asi cada APK nuevo se instala encima del anterior.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.$run"
    }

    // Firma fija: todos los APK salen firmados con la misma llave y se pueden instalar como
    // actualizacion sin desinstalar (desinstalar borra tu identidad y tus contactos).
    signingConfigs {
        getByName("debug") {
            storeFile = file("envio-debug.keystore")
            storePassword = "envio123"
            keyAlias = "envio"
            keyPassword = "envio123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    }
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Modo directo (sin internet): Bluetooth / BLE / Wi-Fi Direct automatico
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Modo internet: relevo MQTT, sin cuentas ni configuracion
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
