plugins {
    alias(libs.plugins.android.application)
    // Depuis AGP 9.0, le support Kotlin est intégré : plus besoin (et plus compatible)
    // d'appliquer le plugin org.jetbrains.kotlin.android séparément.
    alias(libs.plugins.kotlin.compose)
    // Requis pour les @Serializable de VTubeStudioProtocol.kt (point 39, API Plugin VTube Studio).
    alias(libs.plugins.kotlin.serialization)
}

// Signature release : lue depuis des variables d'environnement, jamais depuis un fichier commité.
// Fonctionne aussi bien en local (export avant ./gradlew assembleRelease) qu'en CI (voir
// .github/workflows/release.yml, qui les fournit depuis les secrets du dépôt GitHub). Si ces
// variables sont absentes -- cas normal en dev quotidien (assembleDebug/installDebug) -- le bloc
// signingConfigs.release n'est simplement pas créé et release.signingConfig reste non défini :
// un assembleRelease sans les variables produit un APK non signé (comportement Gradle standard),
// pas un crash de configuration.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigningEnv = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

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
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningEnv) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Toujours désactivé pour l'instant (voir AndroidMoCap_revue_technique.md, point 8) :
            // activer R8 sans pouvoir vérifier sur device que MediaPipe/OSC (réflexion) survivent
            // à l'obfuscation serait risqué tant que le testeur habituel n'a pas d'appareil sous la
            // main -- à revisiter une fois les tests device possibles à nouveau.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningEnv) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // Requis pour BuildConfig.VERSION_NAME -- affiché dans DiagnosticsScreen (ligne "Version
        // de l'app", cible du déverrouillage du panneau de mocks de debug caché).
        buildConfig = true
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

    // Diffusion réseau : protocole VMC (OSC over UDP), destiné à Blender / Unity (voir point 39 --
    // VTube Studio ne le reçoit vraisemblablement pas nativement)
    implementation(libs.javaosc.core)

    // Diffusion réseau : intégration directe VTube Studio via son API Plugin (WebSocket JSON,
    // point 39) -- nv-websocket-client pour le client WebSocket (OkHttp essayé en premier, retiré :
    // propose systématiquement l'extension permessage-deflate sans réglage public pour la
    // désactiver, incompatible avec le serveur VTube Studio -- voir le commentaire dans
    // gradle/libs.versions.toml), kotlinx.serialization pour l'encodage JSON des requêtes/réponses
    // de l'API (préféré à org.json : reste testable en JVM pur, voir AndroidMoCap_tests_unitaires.md).
    implementation(libs.nv.websocket.client)
    implementation(libs.kotlinx.serialization.json)

    // Réglages persistés (AppSettingsStore, ConnectionSettingsStore) : mode éco, overlay du mesh,
    // seuil batterie, palier forcé, mocks de debug, sélection de blendshapes (optionnelle), type de
    // connexion et cible réseau
    implementation(libs.androidx.datastore.preferences)

    // Tests unitaires (JVM pur -- pas de dépendance Android, voir AndroidMoCap_tests_unitaires.md)
    testImplementation(libs.junit)
}
