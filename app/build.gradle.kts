plugins {
    alias(libs.plugins.android.application)
    // Depuis AGP 9.0, le support Kotlin est intégré : plus besoin (et plus compatible)
    // d'appliquer le plugin org.jetbrains.kotlin.android séparément.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.guyiome.androidmocap"
    // 37 requis par les dernières versions d'androidx.core / lifecycle (AAR metadata check).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.guyiome.androidmocap"
        // minSdk 30 : couvre l'essentiel du parc actif (~87%, cf. étude de faisabilité),
        // et ARCore/CameraX/MediaPipe filtrent de toute façon les appareils trop anciens.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-poc"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Pas de bloc kotlinOptions/kotlin.compilerOptions : avec le Kotlin intégré à AGP 9,
    // jvmTarget suit automatiquement compileOptions.targetCompatibility ci-dessus.

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Évite les conflits de fichiers META-INF dupliqués entre MediaPipe (protobuf) et d'autres dépendances.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Caméra
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Tracking facial : ARCore (pose de tête) + MediaPipe Face Landmarker (52 blendshapes ARKit)
    implementation(libs.google.ar.core)
    implementation(libs.mediapipe.tasks.vision)

    // Diffusion réseau : protocole VMC (OSC over UDP) vers VTube Studio / Blender / Unity
    implementation(libs.javaosc.core)

    // Réglages de calibrage persistés (offset caméra, IP/port cible, sensibilité)
    implementation(libs.androidx.datastore.preferences)
}
