package com.guyiome.androidmocap.ui

/** Nombre de taps consécutifs requis pour révéler le panneau de mocks de debug (voir [AdvancedSettingsScreen]). */
internal const val DEBUG_PANEL_UNLOCK_TAP_COUNT = 7

/** Fenêtre de temps (ms) tolérée entre deux taps consécutifs -- au-delà, le compteur repart de 1 plutôt que de continuer. */
internal const val DEBUG_PANEL_UNLOCK_TAP_WINDOW_MS = 1000L

/**
 * État pur du geste de déverrouillage du panneau de mocks de debug (voir revue technique, point
 * 35) -- même principe que le menu développeur Android ("taper 7 fois sur le numéro de build"),
 * appliqué ici à la ligne "Version de l'app" de [AdvancedSettingsScreen]. Volontairement non persisté
 * (état Compose local, `remember`) : le geste ne coûte que quelques secondes à refaire, contrairement
 * aux mocks eux-mêmes (voir `AppSettingsStore.debugForceArCoreUnavailable`/`debugForceGpuUnavailable`,
 * qui doivent survivre au redémarrage nécessaire à leur propre test).
 */
internal data class DebugPanelUnlockState(
    val tapCount: Int = 0,
    val lastTapAtMs: Long = 0L,
) {
    val unlocked: Boolean get() = tapCount >= DEBUG_PANEL_UNLOCK_TAP_COUNT
    val remainingTaps: Int get() = (DEBUG_PANEL_UNLOCK_TAP_COUNT - tapCount).coerceAtLeast(0)
}

/**
 * Un tap : [nowMs] est l'horloge au moment de l'appui (`System.currentTimeMillis()` côté appelant).
 * No-op une fois déjà déverrouillé (pas de raison de continuer à compter). Un tap au-delà de
 * [DEBUG_PANEL_UNLOCK_TAP_WINDOW_MS] depuis le précédent repart de 1 plutôt que d'accumuler --
 * évite qu'une série de taps épars sur plusieurs minutes finisse par déverrouiller par accident.
 */
internal fun DebugPanelUnlockState.registerTap(nowMs: Long): DebugPanelUnlockState {
    if (unlocked) return this
    val withinWindow = tapCount > 0 && nowMs - lastTapAtMs <= DEBUG_PANEL_UNLOCK_TAP_WINDOW_MS
    val newCount = if (withinWindow) tapCount + 1 else 1
    return copy(tapCount = newCount, lastTapAtMs = nowMs)
}
