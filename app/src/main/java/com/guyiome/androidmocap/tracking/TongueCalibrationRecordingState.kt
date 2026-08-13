package com.guyiome.androidmocap.tracking

/** Publique (pas `internal`) : exposée dans `MainUiState`, elle-même publique -- même précédent que
 *  `TrackingTier`. */
enum class TongueCalibrationPhase { IDLE, RECORDING_TONGUE_OUT, PREPARE_TONGUE_IN, RECORDING_TONGUE_IN, DONE }

/**
 * Machine à état pure pilotant l'enregistrement d'une calibration de l'étage 3 (revue technique,
 * point 15) : "langue dehors" puis "langue rentrée", quelques secondes chacune -- même famille que
 * `CalibrationAnomalyState`/`ThermalThrottleState`, aucune dépendance Android, pilotée tick par tick
 * (temps écoulé + embedding optionnel de la frame) depuis `MainViewModel` plutôt que d'implémenter
 * cette logique de comptage directement dans le ViewModel.
 *
 * Une pause [TongueCalibrationPhase.PREPARE_TONGUE_IN] sépare les deux enregistrements (ajoutée le
 * 11 août 2026, retour utilisateur après un premier essai de calibration faible) : sans elle, la
 * transition entre les deux phases est instantanée dès que la durée de la première est atteinte,
 * risquant de capturer des frames de transition (langue en train de rentrer, pas encore stabilisée)
 * dans l'accumulateur "langue rentrée" -- et inversement, une préparation insuffisante avant "langue
 * dehors" pouvait déjà expliquer une référence peu discriminante. Aucune accumulation pendant cette
 * pause.
 */
internal data class TongueCalibrationRecordingState(
    val phase: TongueCalibrationPhase = TongueCalibrationPhase.IDLE,
    val tongueOutAccumulator: EmbeddingAccumulator = EmbeddingAccumulator(),
    val tongueInAccumulator: EmbeddingAccumulator = EmbeddingAccumulator(),
    val elapsedMsInPhase: Long = 0L,
)

/** Unité de base pour les durées d'enregistrement de calibration -- provisoire, réglable en
 *  pratique via `AppSettingsStore.tongueCalibrationRecordingDurationMs` (panneau debug) plutôt que
 *  codée en dur, vu le nombre de sessions de réglage qu'ont demandé les étages 1/2. Ne pilote plus
 *  directement la durée d'une phase depuis le 13 août 2026 (point 56) : les deux phases en dérivent
 *  désormais par multiplicateur, voir [DEFAULT_TONGUE_OUT_RECORDING_DURATION_MS] et
 *  [DEFAULT_TONGUE_IN_RECORDING_DURATION_MS]. */
internal const val DEFAULT_CALIBRATION_RECORDING_DURATION_MS = 3000L

/** Durée par défaut de la pause entre les deux phases d'enregistrement -- pas encore réglable
 *  depuis le panneau debug (contrairement à la durée d'enregistrement), jugé moins critique. */
internal const val DEFAULT_CALIBRATION_PREPARE_DURATION_MS = 2000L

/**
 * Multiplicateur appliqué à l'unité de base pour obtenir la durée de la phase "langue dehors" --
 * source unique de vérité, utilisée à la fois pour [DEFAULT_TONGUE_OUT_RECORDING_DURATION_MS] et
 * par `MainViewModel` pour dériver la durée réelle (réglable) de cette phase.
 *
 * Doublé le 13 août 2026 (revue technique, point 56), en symétrie avec
 * [TONGUE_IN_RECORDING_MULTIPLIER] : jusque-là seule la phase "langue rentrée" avait été allongée
 * (voir son kdoc) pour couvrir une variation pendant l'enregistrement plutôt qu'une pose statique.
 * Une fois les étages 1/2 retirés du filtre dur (voir kdoc de `mouthGeometricGateOpen`,
 * `TongueOutGate.kt`), toute la discrimination repose sur l'étage 3 seul -- une référence "langue
 * dehors" plus riche (couvrant une légère variation de position/profondeur de la langue pendant la
 * tenue, pas seulement une pose figée) est le seul levier de fiabilité qui reste vraiment
 * pertinent, la marge de classification elle-même ne pouvant pas séparer un chevauchement réel
 * entre les deux classes (confirmé sur les données du 13 août 2026, voir kdoc de
 * `TongueEmbeddingClassifier.DEFAULT_CLASSIFICATION_MARGIN`).
 */
internal const val TONGUE_OUT_RECORDING_MULTIPLIER = 2L

/** Durée par défaut de la phase "langue dehors" -- voir kdoc de [TONGUE_OUT_RECORDING_MULTIPLIER]. */
internal const val DEFAULT_TONGUE_OUT_RECORDING_DURATION_MS =
    DEFAULT_CALIBRATION_RECORDING_DURATION_MS * TONGUE_OUT_RECORDING_MULTIPLIER

/**
 * Multiplicateur appliqué à l'unité de base pour obtenir la durée de la phase "langue rentrée" --
 * source unique de vérité, utilisée à la fois pour [DEFAULT_TONGUE_IN_RECORDING_DURATION_MS] et
 * par `MainViewModel` pour dériver la durée réelle (réglable) de cette phase, plutôt que deux
 * constantes indépendantes qui pourraient diverger.
 */
internal const val TONGUE_IN_RECORDING_MULTIPLIER = 2L

/**
 * Durée par défaut de la phase "langue rentrée" -- volontairement le double de l'unité de base
 * (revue technique, point 15 -- session de diagnostic du 13 août 2026, confirmée sur device) : la
 * référence "langue rentrée" enregistrée jusqu'ici était une pose statique, bouche fermée -- jamais
 * avec mouvement de mâchoire. Or une bouche ouverte par un mouvement de mâchoire (langue toujours
 * rentrée) s'est révélée visuellement confondue avec "langue dehors" par le classifieur, avec un
 * chevauchement réel des deux classes en similarité cosinus (pas un simple problème de seuil de
 * marge, voir `TongueEmbeddingClassifier.DEFAULT_CLASSIFICATION_MARGIN`). Élargir la référence pour
 * qu'elle couvre aussi cette variation demande plusieurs cycles d'ouverture/fermeture de mâchoire
 * pendant l'enregistrement, d'où une durée doublée. La phase "langue dehors" reçoit désormais le
 * même traitement (même multiplicateur sur la même unité de base) -- voir
 * [TONGUE_OUT_RECORDING_MULTIPLIER].
 */
internal const val DEFAULT_TONGUE_IN_RECORDING_DURATION_MS =
    DEFAULT_CALIBRATION_RECORDING_DURATION_MS * TONGUE_IN_RECORDING_MULTIPLIER

/** Démarre l'enregistrement -- toujours par la phase "langue dehors" en premier. Nommée
 *  différemment de `MainViewModel.startTongueCalibration()` (qui l'appelle) pour éviter une
 *  collision de noms qui bouclerait sur la méthode membre plutôt que cette fonction top-level. */
internal fun newTongueCalibrationRecording(): TongueCalibrationRecordingState =
    TongueCalibrationRecordingState(phase = TongueCalibrationPhase.RECORDING_TONGUE_OUT)

/**
 * Avance la machine d'un pas de temps [elapsedMs], en intégrant [embedding] si non-null (ignoré
 * pendant [TongueCalibrationPhase.PREPARE_TONGUE_IN], qui n'accumule jamais) -- peut être `null` si
 * aucun embedding n'a pu être calculé pour ce tick (visage non détecté, étages 1/2 fermés le temps
 * d'une frame -- rare mais possible même en calibration guidée) : le sample est alors simplement
 * sauté, la durée continue de s'écouler sans lui. Transition automatique
 * `RECORDING_TONGUE_OUT` -> `PREPARE_TONGUE_IN` -> `RECORDING_TONGUE_IN` -> `DONE` une fois
 * [durationMs]/[prepareDurationMs]/[tongueInDurationMs] atteint ou dépassé pour la phase courante
 * (`>=`, pas `==` : robuste à un pas de temps qui dépasse la frontière sans tomber pile dessus).
 * No-op si déjà `IDLE` ou `DONE`.
 *
 * [tongueInDurationMs] distinct de [durationMs] depuis le 13 août 2026 -- voir le kdoc de
 * [DEFAULT_TONGUE_IN_RECORDING_DURATION_MS] pour le raisonnement (la phase "langue rentrée" a
 * besoin de plus de temps pour couvrir plusieurs cycles de mouvement de mâchoire). [durationMs]
 * (phase "langue dehors") reçoit le même traitement depuis la même date -- voir
 * [DEFAULT_TONGUE_OUT_RECORDING_DURATION_MS].
 */
internal fun TongueCalibrationRecordingState.tick(
    elapsedMs: Long,
    embedding: FloatArray?,
    durationMs: Long = DEFAULT_TONGUE_OUT_RECORDING_DURATION_MS,
    prepareDurationMs: Long = DEFAULT_CALIBRATION_PREPARE_DURATION_MS,
    tongueInDurationMs: Long = DEFAULT_TONGUE_IN_RECORDING_DURATION_MS,
): TongueCalibrationRecordingState = when (phase) {
    TongueCalibrationPhase.IDLE, TongueCalibrationPhase.DONE -> this
    TongueCalibrationPhase.RECORDING_TONGUE_OUT -> {
        val nextAccumulator = if (embedding != null) tongueOutAccumulator.accumulate(embedding) else tongueOutAccumulator
        val nextElapsed = elapsedMsInPhase + elapsedMs
        if (nextElapsed >= durationMs) {
            copy(phase = TongueCalibrationPhase.PREPARE_TONGUE_IN, tongueOutAccumulator = nextAccumulator, elapsedMsInPhase = 0L)
        } else {
            copy(tongueOutAccumulator = nextAccumulator, elapsedMsInPhase = nextElapsed)
        }
    }
    TongueCalibrationPhase.PREPARE_TONGUE_IN -> {
        val nextElapsed = elapsedMsInPhase + elapsedMs
        if (nextElapsed >= prepareDurationMs) {
            copy(phase = TongueCalibrationPhase.RECORDING_TONGUE_IN, elapsedMsInPhase = 0L)
        } else {
            copy(elapsedMsInPhase = nextElapsed)
        }
    }
    TongueCalibrationPhase.RECORDING_TONGUE_IN -> {
        val nextAccumulator = if (embedding != null) tongueInAccumulator.accumulate(embedding) else tongueInAccumulator
        val nextElapsed = elapsedMsInPhase + elapsedMs
        if (nextElapsed >= tongueInDurationMs) {
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
