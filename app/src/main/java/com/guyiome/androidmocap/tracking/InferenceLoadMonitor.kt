package com.guyiome.androidmocap.tracking

/**
 * Moyenne mobile exponentielle (EMA) de `FaceTrackingResult.inferenceTimeMs` -- alimente
 * l'avertissement "l'appareil peine" combiné au throttling thermique pour la détection de la
 * langue tirée. Volontairement pas basé sur [OneEuroFilter] : ce
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
 *
 * `marginRatio` par défaut relevé à `4f` (au lieu de `1.2f` initialement) suite à un faux positif
 * confirmé sur device le 11 août 2026 : `inferenceTimeMs` mesure la latence bout-en-bout
 * capture->résultat (voir `FaceLandmarkerHelper.onLiveStreamResult`), pas une durée de blocage
 * synchrone par frame -- elle dépasse couramment `1000/targetFps` dans un pipeline qui pipeline
 * déjà plusieurs frames en vol, sans que ce soit un signe de réelle surcharge. Avec une marge de
 * 1.2, l'avertissement se déclenchait quasiment tout de suite, sans lien avec la détection de
 * langue tirée elle-même -- symptôme signalé par l'utilisateur ("énorme warning" dès l'activation
 * du toggle). `4f` est une valeur de repli délibérément conservatrice, pas calée sur de vraies
 * mesures device -- à affiner une fois de vraies données de charge collectées (même statut que les
 * seuils provisoires de `TongueOutGate`/`MouthColorAnalysis`).
 */
internal fun InferenceLoadState.isRunningHigh(frameBudgetMs: Long, marginRatio: Float = 4f): Boolean =
    sampleCount > 0 && averageMs > frameBudgetMs * marginRatio
