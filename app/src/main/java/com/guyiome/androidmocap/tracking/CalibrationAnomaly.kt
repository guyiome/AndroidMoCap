package com.guyiome.androidmocap.tracking

/**
 * Seuil de variance de repos (delta absolu moyen frame-à-frame, voir [meanAbsoluteBlendshapeDelta])
 * en dessous duquel le visage est considéré immobile. Non mesuré sur device -- estimation
 * conservative, le bruit MediaPipe typique par blendshape isolée dépasse souvent ça d'un facteur
 * 2-3, mais la moyenne sur ~50 blendshapes appariées lisse largement ce bruit. À ajuster si le
 * signal se révèle trop permissif/trop strict en usage réel.
 */
internal const val REST_VARIANCE_THRESHOLD = 0.01f

/**
 * Amplitude, en degrés, au-delà de laquelle la pose calibrée (voir [maxAbsEulerDegrees]) n'est
 * plus considérée "proche de zéro" -- volontairement au-dessus du bruit résiduel habituel d'une
 * calibration (quelques degrés), pour ne réagir qu'à une dérive physique franche (téléphone
 * bougé/repositionné), pas à l'imprécision normale de calibrage.
 */
internal const val DRIFT_POSE_THRESHOLD_DEGREES = 8f

/**
 * Nombre de frames consécutives "au repos + pose décalée" avant de considérer la dérive comme
 * confirmée plutôt qu'un pic isolé -- estimation calée sur ~30 fps (palier STANDARD, le "moyen"
 * des trois paliers), soit ~3 secondes d'immobilité persistante avec une pose hors zéro. Se traduit
 * en ~1.5s sur OPTIMAL (60 fps) et ~4.5s sur COMPATIBLE (20 fps) -- écart assumé, jamais mesuré sur
 * device réel, à revisiter si le signal se révèle trop lent/rapide sur un palier donné.
 */
internal const val SUSTAINED_DRIFT_FRAMES = 90

/**
 * Nombre de frames consécutives sans visage détecté avant qu'une redétection compte comme une
 * vraie "perte puis récupération" (signal complémentaire, voir doc de [CalibrationAnomalyState])
 * plutôt qu'une frame MediaPipe isolée ratée (fréquent, sans rapport avec un déplacement physique).
 */
internal const val MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION = 3

/**
 * État de la détection d'anomalie de calibrage (voir revue technique, point 19) : purement
 * informatif, jamais d'action automatique -- pilote uniquement la teinte du bouton de calibrage
 * existant dans `MainHud`.
 *
 * Deux signaux indépendants peuvent déclencher [flagged], chacun documenté sur son champ de
 * streak. Comme `ThermalThrottleState.downgradeSuggested`, [flagged] est **sticky** : une fois
 * vrai il ne redevient jamais faux tout seul, seul un appel explicite à
 * `MainViewModel.performCalibration()` (bouton ou premier calibrage automatique) le remet à zéro
 * via [CalibrationAnomalyState.INITIAL] -- cohérent avec "se résout de lui-même dès que
 * l'utilisateur appuie pour recalibrer" (revue technique, point 19).
 */
data class CalibrationAnomalyState(
    val flagged: Boolean = false,
    val consecutiveRestDriftFrames: Int = 0,
    val consecutiveMissingFaceFrames: Int = 0,
) {
    companion object {
        val INITIAL = CalibrationAnomalyState()
    }
}

/**
 * Magnitude d'un triplet d'angles d'Euler calibrés [pitch, yaw, roll] (degrés) : le plus grand des
 * trois en valeur absolue, pas une norme euclidienne -- une dérive franche sur un seul axe (ex.
 * juste le roll si le téléphone a glissé latéralement) doit compter pleinement, pas être diluée par
 * les deux autres axes restés proches de zéro.
 */
internal fun maxAbsEulerDegrees(euler: FloatArray): Float = euler.maxOf { kotlin.math.abs(it) }

/**
 * Transition par frame. [isAtRest] et [poseMagnitudeDegrees] correspondent au critère principal du
 * point 19 ("visage au repos mais pose qui ne revient pas près de zéro") ; [faceDetected] au signal
 * complémentaire (perte puis redétection). Ne rien appeler tant que
 * `MainUiState.isCalibrated` est faux -- avant le tout premier calibrage, "proche de zéro" n'a pas
 * de sens et ferait remonter de faux positifs.
 */
internal fun CalibrationAnomalyState.next(
    isAtRest: Boolean,
    poseMagnitudeDegrees: Float,
    faceDetected: Boolean,
): CalibrationAnomalyState {
    val restDrift = isAtRest && poseMagnitudeDegrees >= DRIFT_POSE_THRESHOLD_DEGREES
    val restStreak = if (restDrift) consecutiveRestDriftFrames + 1 else 0
    val missingStreak = if (!faceDetected) consecutiveMissingFaceFrames + 1 else 0
    // Redétection après une perte suffisamment longue -- signal indépendant, ne dépend pas de
    // isAtRest/poseMagnitudeDegrees (voir doc de classe : "signal complémentaire, plus simple").
    val reacquired = faceDetected && consecutiveMissingFaceFrames >= MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION
    return copy(
        flagged = flagged || restStreak >= SUSTAINED_DRIFT_FRAMES || reacquired,
        consecutiveRestDriftFrames = restStreak,
        consecutiveMissingFaceFrames = missingStreak,
    )
}
