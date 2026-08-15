package com.guyiome.androidmocap.tracking

/**
 * Plancher de débit sous lequel on ne descend jamais en throttling, même si la moitié du débit
 * nominal serait plus basse -- un débit trop faible rendrait le retour visuel (mesh overlay) et le
 * calibrage inutilisables, alors que l'objectif est de soulager la chauffe, pas de geler l'app.
 */
internal const val MIN_THROTTLED_FPS = 10

/**
 * Nombre de sondages *consécutifs* en throttling avant de considérer la chauffe comme persistante
 * (voir [ThermalThrottleState.downgradeSuggested]) -- calé sur un sondage toutes les 5 secondes côté
 * [com.guyiome.androidmocap.ui.MainViewModel] (voir sa constante `THERMAL_POLL_INTERVAL_MS`), donc
 * environ une minute de chauffe continue. Les deux valeurs doivent rester cohérentes entre elles ;
 * documentées séparément plutôt que couplées techniquement pour garder cette logique pure sans
 * dépendance de timing réel, testable en JVM sans délai d'exécution.
 */
internal const val SUSTAINED_THROTTLE_TICKS = 12

/**
 * État de la réponse au throttling thermique (voir
 * [com.guyiome.androidmocap.capabilities.DeviceCapabilityDetector.isThermalThrottling]), piloté par
 * [MainViewModel][com.guyiome.androidmocap.ui.MainViewModel] via un sondage périodique pendant la
 * capture. Ne touche ni au délégué GPU/CPU ni à la source caméra (ARCore/CameraX) -- décision
 * explicite de ne pas reconstruire le pipeline caméra/MediaPipe à chaud (même principe que le
 * sélecteur de palier manuel, voir [TrackingTierSelector]) : seul le débit cible est réduit, débit
 * déjà ajustable à chaud via `CameraController`/`ArCoreHeadPoseTracker.setTargetFps`.
 *
 * Remonte automatiquement au débit nominal dès que la chauffe retombe (voir [next]) -- pas
 * d'hystérésis : un sondage toutes les quelques secondes est déjà assez espacé pour ne pas osciller
 * visiblement.
 *
 * [downgradeSuggested] devient vrai (et le reste pour le reste de la session, jamais remis à faux)
 * après [SUSTAINED_THROTTLE_TICKS] sondages consécutifs en throttling -- signal purement informatif
 * affiché en diagnostic (voir `AdvancedSettingsScreen`), à côté du sélecteur de palier manuel déjà
 * existant -- jamais appliqué automatiquement : rétrograder le palier reste toujours un choix
 * explicite de l'utilisateur, pas une action prise pour lui à sa place.
 */
data class ThermalThrottleState(
    val currentFps: Int,
    val isThrottling: Boolean = false,
    val consecutiveThrottledTicks: Int = 0,
    val downgradeSuggested: Boolean = false,
) {
    companion object {
        fun initial(nominalFps: Int) = ThermalThrottleState(currentFps = nominalFps)
    }
}

/**
 * Un sondage : [isThermalThrottling] est le résultat du dernier appel à
 * [com.guyiome.androidmocap.capabilities.DeviceCapabilityDetector.isThermalThrottling]. [nominalFps]
 * est le débit du palier choisi ([com.guyiome.androidmocap.tracking.TierConfig.targetFps]), constant
 * pour toute la session -- passé à chaque appel plutôt que mémorisé dans l'état pour garder celui-ci
 * simple et sans hypothèse sur la durée de vie de l'appelant.
 */
internal fun ThermalThrottleState.next(nominalFps: Int, isThermalThrottling: Boolean): ThermalThrottleState {
    val consecutive = if (isThermalThrottling) consecutiveThrottledTicks + 1 else 0
    val fps = if (isThermalThrottling) (nominalFps / 2).coerceAtLeast(MIN_THROTTLED_FPS) else nominalFps
    return copy(
        currentFps = fps,
        isThrottling = isThermalThrottling,
        consecutiveThrottledTicks = consecutive,
        downgradeSuggested = downgradeSuggested || consecutive >= SUSTAINED_THROTTLE_TICKS,
    )
}
