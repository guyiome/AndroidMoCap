package com.guyiome.androidmocap.tracking

/**
 * Machine à état pure pilotant l'enregistrement d'une calibration de l'étage 3 (revue technique,
 * point 15) : "langue dehors" puis "langue rentrée", quelques secondes chacune -- même famille que
 * `CalibrationAnomalyState`/`ThermalThrottleState`, aucune dépendance Android, pilotée tick par tick
 * (temps écoulé + embedding optionnel de la frame) depuis `MainViewModel` plutôt que d'implémenter
 * cette logique de comptage directement dans le ViewModel.
 */
/** Publique (pas `internal`) : exposée dans `MainUiState`, elle-même publique -- même précédent que
 *  `TrackingTier`. */
enum class TongueCalibrationPhase { IDLE, RECORDING_TONGUE_OUT, RECORDING_TONGUE_IN, DONE }

internal data class TongueCalibrationRecordingState(
    val phase: TongueCalibrationPhase = TongueCalibrationPhase.IDLE,
    val tongueOutAccumulator: EmbeddingAccumulator = EmbeddingAccumulator(),
    val tongueInAccumulator: EmbeddingAccumulator = EmbeddingAccumulator(),
    val elapsedMsInPhase: Long = 0L,
)

/** Durée par défaut de chaque phase d'enregistrement -- provisoire, réglable en pratique via
 *  `AppSettingsStore.tongueCalibrationRecordingDurationMs` (panneau debug) plutôt que codée en dur,
 *  vu le nombre de sessions de réglage qu'ont demandé les étages 1/2. */
internal const val DEFAULT_CALIBRATION_RECORDING_DURATION_MS = 3000L

/** Démarre l'enregistrement -- toujours par la phase "langue dehors" en premier. Nommée
 *  différemment de `MainViewModel.startTongueCalibration()` (qui l'appelle) pour éviter une
 *  collision de noms qui bouclerait sur la méthode membre plutôt que cette fonction top-level. */
internal fun newTongueCalibrationRecording(): TongueCalibrationRecordingState =
    TongueCalibrationRecordingState(phase = TongueCalibrationPhase.RECORDING_TONGUE_OUT)

/**
 * Avance la machine d'un pas de temps [elapsedMs], en intégrant [embedding] si non-null --
 * peut être `null` si aucun embedding n'a pu être calculé pour ce tick (visage non détecté, étages
 * 1/2 fermés le temps d'une frame -- rare mais possible même en calibration guidée) : le sample est
 * alors simplement sauté, la durée continue de s'écouler sans lui. Transition automatique
 * `RECORDING_TONGUE_OUT` -> `RECORDING_TONGUE_IN` -> `DONE` une fois [durationMs] atteint ou dépassé
 * pour la phase courante (`>=`, pas `==` : robuste à un pas de temps qui dépasse la frontière sans
 * tomber pile dessus). No-op si déjà `IDLE` ou `DONE`.
 */
internal fun TongueCalibrationRecordingState.tick(
    elapsedMs: Long,
    embedding: FloatArray?,
    durationMs: Long = DEFAULT_CALIBRATION_RECORDING_DURATION_MS,
): TongueCalibrationRecordingState = when (phase) {
    TongueCalibrationPhase.IDLE, TongueCalibrationPhase.DONE -> this
    TongueCalibrationPhase.RECORDING_TONGUE_OUT -> {
        val nextAccumulator = if (embedding != null) tongueOutAccumulator.accumulate(embedding) else tongueOutAccumulator
        val nextElapsed = elapsedMsInPhase + elapsedMs
        if (nextElapsed >= durationMs) {
            copy(phase = TongueCalibrationPhase.RECORDING_TONGUE_IN, tongueOutAccumulator = nextAccumulator, elapsedMsInPhase = 0L)
        } else {
            copy(tongueOutAccumulator = nextAccumulator, elapsedMsInPhase = nextElapsed)
        }
    }
    TongueCalibrationPhase.RECORDING_TONGUE_IN -> {
        val nextAccumulator = if (embedding != null) tongueInAccumulator.accumulate(embedding) else tongueInAccumulator
        val nextElapsed = elapsedMsInPhase + elapsedMs
        if (nextElapsed >= durationMs) {
            copy(phase = TongueCalibrationPhase.DONE, tongueInAccumulator = nextAccumulator, elapsedMsInPhase = 0L)
        } else {
            copy(tongueInAccumulator = nextAccumulator, elapsedMsInPhase = nextElapsed)
        }
    }
}

internal data class TongueCalibrationResult(val tongueOutReference: FloatArray, val tongueInReference: FloatArray)

/** Résultat final -- non-null seulement une fois `DONE` et les deux moyennes disponibles. */
internal fun TongueCalibrationRecordingState.result(): TongueCalibrationResult? {
    if (phase != TongueCalibrationPhase.DONE) return null
    val tongueOut = tongueOutAccumulator.average() ?: return null
    val tongueIn = tongueInAccumulator.average() ?: return null
    return TongueCalibrationResult(tongueOut, tongueIn)
}
