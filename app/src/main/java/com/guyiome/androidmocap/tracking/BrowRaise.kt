package com.guyiome.androidmocap.tracking

import kotlin.math.sqrt

/**
 * Mesure géométrique pure de la hauteur du sourcil, même principe que l'Eye Aspect Ratio
 * (`EyeAspectRatio.kt`, point 28) appliqué cette fois aux sourcils -- discussion du 9 août 2026,
 * suite à une comparaison avec le pipeline caméra RTX de VTube Studio (NVIDIA Broadcast/Maxine AR
 * SDK) : ce pipeline calcule lui aussi ses coefficients d'expression (yeux, sourcils) à partir des
 * landmarks détectés plutôt que de faire confiance à un classifieur direct -- même principe que
 * l'EAR, pas une astuce qu'on n'aurait pas. `browDownLeft/Right`/`browInnerUp`/`browOuterUpLeft/Right`
 * ne sont PAS dans `BlendshapeCatalog.unreliable` (contrairement à `tongueOut`) -- MediaPipe les
 * produit, le sujet ici est la finesse/précision perçue, pas une absence de signal.
 *
 * `browRaiseRatio = (‖inner-centreOeil‖ + ‖outer-centreOeil‖) / (2·largeurOeil)` -- inner/outer :
 * points intérieur/extérieur du sourcil ; centreOeil : milieu des deux coins de l'œil (pas un
 * nouveau point à identifier, réutilise les coins déjà confirmés par [EyeLandmarkIndices]) ;
 * largeurOeil : distance entre les deux coins (même rôle que le dénominateur horizontal de l'EAR).
 * Sourcil levé -> ratio plus grand ; sourcil froncé/abaissé -> ratio plus petit.
 */
internal fun browRaiseRatio(
    inner: Pair<Float, Float>,
    outer: Pair<Float, Float>,
    eyeCornerNear: Pair<Float, Float>,
    eyeCornerFar: Pair<Float, Float>,
): Float {
    val eyeWidth = distance(eyeCornerNear, eyeCornerFar)
    if (eyeWidth <= 0f) return 0f
    val eyeCenter = midpoint(eyeCornerNear, eyeCornerFar)
    return (distance(inner, eyeCenter) + distance(outer, eyeCenter)) / (2f * eyeWidth)
}

private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return sqrt(dx * dx + dy * dy)
}

private fun midpoint(a: Pair<Float, Float>, b: Pair<Float, Float>): Pair<Float, Float> =
    (a.first + b.first) / 2f to (a.second + b.second) / 2f

/**
 * Indices dans le mesh 478 points de MediaPipe Face Landmarker -- **hypothèse, PAS encore confirmée
 * sur device** (contrairement à [EyeLandmarkIndices], dont la correspondance gauche/droite a été
 * vérifiée par test réel). Point de départ : blog sanderdesnaijer.com/blog/mediapipe-face-mesh-landmarks,
 * qui étiquette « sourcil gauche » = 70 (extérieur)/107 (intérieur) et « sourcil droit » = 300
 * (extérieur)/336 (intérieur). Ce même blog étiquette aussi les coins d'œil 33/133 comme "œil
 * gauche" -- **confirmé à l'envers sur device pour les yeux** (33/133 est en réalité l'œil droit,
 * voir kdoc d'[EyeLandmarkIndices]). Les indices de sourcils ci-dessous sont donc inversés par
 * rapport à l'étiquetage du blog, en cohérence avec cette même inversion déjà démontrée -- mais ça
 * reste une déduction par analogie, pas une mesure : à confirmer par le même protocole que les yeux
 * (lever un sourcil isolément, recouper avec `browInnerUp`/`browOuterUpLeft`/`Right` bruts) avant de
 * faire confiance à cette correspondance pour une vraie correction.
 */
internal object BrowLandmarkIndices {
    val RIGHT_EYEBROW = BrowIndices(
        inner = 107,
        outer = 70,
        eyeCornerNear = EyeLandmarkIndices.RIGHT_EYE.cornerNear,
        eyeCornerFar = EyeLandmarkIndices.RIGHT_EYE.cornerFar,
    )
    val LEFT_EYEBROW = BrowIndices(
        inner = 336,
        outer = 300,
        eyeCornerNear = EyeLandmarkIndices.LEFT_EYE.cornerNear,
        eyeCornerFar = EyeLandmarkIndices.LEFT_EYE.cornerFar,
    )
}

internal data class BrowIndices(
    val inner: Int,
    val outer: Int,
    val eyeCornerNear: Int,
    val eyeCornerFar: Int,
) {
    /** Le plus grand indice référencé -- sert de garde-fou dans [browRaiseRatioFromLandmarks]. */
    val maxIndex: Int get() = maxOf(inner, outer, eyeCornerNear, eyeCornerFar)
}

/**
 * Calcule [browRaiseRatio] à partir d'une liste de landmarks normalisés au format déjà utilisé par
 * [FaceTrackingResult.faceLandmarks] -- 0f si la liste est vide ou trop courte pour contenir tous
 * les indices attendus, plutôt que de planter sur un accès hors limites (même garde qu'
 * [eyeAspectRatioFromLandmarks]).
 */
internal fun browRaiseRatioFromLandmarks(landmarks: List<Pair<Float, Float>>, indices: BrowIndices): Float {
    if (landmarks.size <= indices.maxIndex) return 0f
    return browRaiseRatio(
        inner = landmarks[indices.inner],
        outer = landmarks[indices.outer],
        eyeCornerNear = landmarks[indices.eyeCornerNear],
        eyeCornerFar = landmarks[indices.eyeCornerFar],
    )
}
