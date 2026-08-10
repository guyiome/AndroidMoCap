package com.guyiome.androidmocap.tracking

/**
 * Moyenne mobile exponentielle (EMA) de `FaceTrackingResult.inferenceTimeMs` -- alimente
 * l'avertissement "l'appareil peine" combiné au throttling thermique pour la détection de la
 * langue tirée (revue technique, point 15). Volontairement pas basé sur [OneEuroFilter] : ce
 * dernier est calé pour des signaux angle/ouverture avec des paramètres `minCutoff`/`beta` qui ne
 * correspondent à rien pour lisser une simple durée en millisecondes -- une EMA classique reste
 * plus simple et tout aussi adéquate ici, et garde ce module trivial à tester à la main.
 */
internal data class InferenceLoadState(val averageMs: Float = 0f, val sampleCount: Int = 0)

/**
 * Intègre un nouvel échantillon. La toute première frame prend la valeur brute sans lissage (même
 * convention que [OneEuroFilter] : pas de raison de retarder la toute première lecture avec une
 * moyenne à zéro).
 */
internal fun InferenceLoadState.next(sampleMs: Long, alpha: Float = 0.2f): InferenceLoadState {
    val avg = if (sampleCount == 0) sampleMs.toFloat() else alpha * sampleMs + (1f - alpha) * averageMs
    return copy(averageMs = avg, sampleCount = sampleCount + 1)
}

/**
 * `true` si la moyenne lissée dépasse le budget d'une frame au débit courant d'un facteur
 * [marginRatio] -- `false` tant qu'aucun échantillon n'a encore été intégré (pas de faux positif
 * avant la première frame).
 */
internal fun InferenceLoadState.isRunningHigh(frameBudgetMs: Long, marginRatio: Float = 1.2f): Boolean =
    sampleCount > 0 && averageMs > frameBudgetMs * marginRatio
