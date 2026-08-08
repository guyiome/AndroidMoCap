// Fichier racine : déclare les plugins utilisés par les sous-modules, sans les appliquer ici.
plugins {
    alias(libs.plugins.android.application) apply false
    // Depuis AGP 9.0, le support Kotlin est intégré : pas de plugin kotlin-android à déclarer ici.
    alias(libs.plugins.kotlin.compose) apply false
    // Requis pour les @Serializable de VTubeStudioProtocol.kt (point 39).
    alias(libs.plugins.kotlin.serialization) apply false
}
