package com.guyiome.androidmocap.tracking

/**
 * Anti-rebond avant d'injecter réellement `tongueOut` dans les données envoyées au réseau (revue
 * technique, point 15 -- activé le 13 août 2026 après deux sessions de test device avec la
 * calibration élargie au mouvement de mâchoire, voir `TongueCalibrationRecordingState.kt`).
 *
 * Ces deux sessions ont confirmé une nette amélioration (5/8 faux `TONGUE_OUT` sur l'ancienne
 * calibration statique, contre 0/98 puis 3/103 sur la nouvelle) mais **pas un zéro absolu** -- le
 * résidu observé est fait d'erreurs isolées (une frame seule, ou deux consécutives au maximum),
 * jamais une tenue soutenue. Exiger [DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES] classements
 * `TONGUE_OUT` d'affilée avant de considérer l'état confirmé filtre ce résidu (constaté : 1 frame
 * isolée + 1 paire consécutive, aucune tenue de 3 frames ou plus dans le mauvais sens sur les deux
 * sessions) sans retarder significativement une vraie détection (une vraie tenue "langue dehors"
 * classait en continu sur les deux sessions, jamais de coupure de 3 frames ou plus une fois lancée).
 * Accepté comme risque résiduel raisonnable par l'utilisateur plutôt que de viser un impossible zéro
 * absolu -- voir la revue technique pour le détail des deux sessions.
 */
internal const val DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES = 3

internal data class TongueOutInjectionState(val consecutiveOutFrames: Int = 0)

/** Incrémente sur `TONGUE_OUT`, remet à zéro sur tout le reste (`TONGUE_IN`, `UNDECIDED`, `null`
 *  -- pas de distinction entre eux, aucun n'est un indice positif). */
internal fun TongueOutInjectionState.next(classification: TongueEmbeddingClassification?): TongueOutInjectionState =
    TongueOutInjectionState(
        consecutiveOutFrames = if (classification == TongueEmbeddingClassification.TONGUE_OUT) consecutiveOutFrames + 1 else 0,
    )

internal fun TongueOutInjectionState.confirmed(
    requiredConsecutiveFrames: Int = DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES,
): Boolean = consecutiveOutFrames >= requiredConsecutiveFrames
