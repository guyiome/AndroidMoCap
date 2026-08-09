package com.guyiome.androidmocap.settings

/**
 * Langue forcée de l'app, indépendante de la langue système -- sélecteur en-app (point 30),
 * fonctionne sur toutes les versions d'Android (contrairement au sélecteur système natif, réglages
 * système > langues de l'app, qui n'existe que depuis Android 13, voir res/xml/locales_config.xml)
 * via `AppCompatDelegate.setApplicationLocales()`. La persistance de la langue choisie est gérée
 * automatiquement par AppCompat lui-même (voir `AppLocalesMetadataHolderService` dans
 * AndroidManifest.xml), pas par ce projet -- ce fichier reste donc volontairement limité au mapping
 * pur nom/tag, testable en JVM ; la construction du `LocaleListCompat` réel et l'appel à
 * `AppCompatDelegate` vivent dans `MainViewModel`/`MainActivity`, seules parties non testables ici
 * (dépendent du framework Android, même contrainte que les autres senders réseau de ce projet).
 */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en"),
}

/**
 * Retrouve l'[AppLanguage] correspondant à un tag de langue (ex. celui que renvoie
 * `AppCompatDelegate.getApplicationLocales().toLanguageTags()`, qui peut être vide plutôt que
 * `null` quand aucune langue n'est forcée) -- [AppLanguage.SYSTEM] si vide/inconnu.
 */
fun appLanguageFromTag(tag: String?): AppLanguage =
    AppLanguage.entries.firstOrNull { it.languageTag != null && it.languageTag == tag } ?: AppLanguage.SYSTEM
