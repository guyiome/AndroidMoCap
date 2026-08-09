package com.guyiome.androidmocap.logging

/**
 * Hiérarchie de sévérité, alignée sur celle documentée par `android.util.Log` lui-même (voir
 * discussion, revue technique point 50) : VERBOSE < DEBUG < INFO < WARN < ERROR. VERBOSE/DEBUG ne
 * sont volontairement pas proposés dans le réglage utilisateur ([AppSettingsStore] -- convention
 * Android officielle, jamais censés survivre dans un build release) ; seuls INFO/WARN/ERROR sont
 * sélectionnables, ERROR par défaut. Public (pas `internal`) : traverse volontairement des API
 * publiques (`MainUiState`, `LoggingSettingsScreen`, `AppSettingsStore`), contrairement au reste du
 * package `logging` qui reste `internal`.
 */
enum class LogLevel(val shortCode: Char) {
    VERBOSE('V'),
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
}
