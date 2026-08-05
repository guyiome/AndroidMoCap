// Fichier racine : déclare les plugins utilisés par les sous-modules, sans les appliquer ici.
plugins {
    alias(libs.plugins.android.application) apply false
    // Depuis AGP 9.0, le support Kotlin est intégré : pas de plugin kotlin-android à déclarer ici.
    alias(libs.plugins.kotlin.compose) apply false
}
