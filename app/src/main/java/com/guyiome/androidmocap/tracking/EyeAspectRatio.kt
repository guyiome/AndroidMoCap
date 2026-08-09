package com.guyiome.androidmocap.tracking

import kotlin.math.sqrt

/**
 * Eye Aspect Ratio (EAR) -- Soukupová & Čech, 2016, "Real-Time Eye Blink Detection Using Facial
 * Landmarks". Mesure géométrique pure (position des paupières), indépendante de la texture de
 * l'œil -- piste explorée en complément du blendshape `eyeBlink` de MediaPipe (un classifieur ML,
 * sensible aux reflets de verres de lunettes) pour la fiabilisation du clignement avec lunettes
 * (revue technique, point 28). Ce fichier ne fait QUE le calcul géométrique -- diagnostic
 * temporaire dans `DiagnosticsScreen` pour l'instant, pas encore branché à une vraie logique de
 * fusion/correction.
 *
 * `EAR = (‖p2-p6‖ + ‖p3-p5‖) / (2·‖p1-p4‖)` -- p1/p4 : coins de l'œil (mesure horizontale) ; p2/p3 :
 * paupière supérieure ; p6/p5 : paupière inférieure, appariés verticalement avec p2/p3
 * respectivement (p2 le plus proche de p1, p3 le plus proche de p4). Œil ouvert : EAR ~0,2-0,35
 * (dépend de la morphologie) ; clignement : EAR -> 0.
 */
internal fun eyeAspectRatio(
    cornerNear: Pair<Float, Float>,
    cornerFar: Pair<Float, Float>,
    upperNear: Pair<Float, Float>,
    upperFar: Pair<Float, Float>,
    lowerNear: Pair<Float, Float>,
    lowerFar: Pair<Float, Float>,
): Float {
    val horizontal = distance(cornerNear, cornerFar)
    if (horizontal <= 0f) return 0f
    val verticalNear = distance(upperNear, lowerNear)
    val verticalFar = distance(upperFar, lowerFar)
    return (verticalNear + verticalFar) / (2f * horizontal)
}

private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return sqrt(dx * dx + dy * dy)
}

/**
 * Indices des 6 points utilisés par [eyeAspectRatio] dans le mesh 478 points de MediaPipe Face
 * Landmarker. Vérifiés croisés sur deux sources indépendantes (blog sanderdesnaijer.com/blog/
 * mediapipe-face-mesh-landmarks + les indices communément repris dans les implémentations EAR sur
 * MediaPipe), mais **pas encore vérifiés sur ce projet contre nos propres blendshapes
 * `eyeBlinkLeft`/`eyeBlinkRight` (convention ARKit)** -- objet précis du diagnostic temporaire
 * ajouté à `DiagnosticsScreen` (voir revue technique, point 28) : nommés `GROUP_A`/`GROUP_B` par
 * indices plutôt que `LEFT`/`RIGHT` tant que cette correspondance n'a pas été confirmée en test réel
 * (les tutoriels communautaires ne s'accordent pas tous sur la convention gauche/droite -- sujet vs
 * image -- pour les mêmes indices, pas une supposition à laisser passer sans vérification).
 */
internal object EyeLandmarkIndices {
    /** Groupe d'indices autour des coins de mesh 33/133. */
    val GROUP_A = EyeIndices(cornerNear = 33, cornerFar = 133, upperNear = 160, upperFar = 158, lowerNear = 144, lowerFar = 153)

    /** Groupe d'indices autour des coins de mesh 263/362. */
    val GROUP_B = EyeIndices(cornerNear = 263, cornerFar = 362, upperNear = 387, upperFar = 385, lowerNear = 373, lowerFar = 380)
}

internal data class EyeIndices(
    val cornerNear: Int,
    val cornerFar: Int,
    val upperNear: Int,
    val upperFar: Int,
    val lowerNear: Int,
    val lowerFar: Int,
) {
    /** Le plus grand indice référencé -- sert de garde-fou dans [eyeAspectRatioFromLandmarks]. */
    val maxIndex: Int get() = maxOf(cornerNear, cornerFar, upperNear, upperFar, lowerNear, lowerFar)
}

/**
 * Calcule [eyeAspectRatio] à partir d'une liste de landmarks normalisés au format déjà utilisé par
 * [FaceTrackingResult.faceLandmarks] -- 0f si la liste est vide ou trop courte pour contenir tous
 * les indices attendus (ex. landmarks pas encore extraits, voir
 * `FaceLandmarkerHelper.landmarksNeeded`), plutôt que de planter sur un accès hors limites.
 */
internal fun eyeAspectRatioFromLandmarks(landmarks: List<Pair<Float, Float>>, indices: EyeIndices): Float {
    if (landmarks.size <= indices.maxIndex) return 0f
    return eyeAspectRatio(
        cornerNear = landmarks[indices.cornerNear],
        cornerFar = landmarks[indices.cornerFar],
        upperNear = landmarks[indices.upperNear],
        upperFar = landmarks[indices.upperFar],
        lowerNear = landmarks[indices.lowerNear],
        lowerFar = landmarks[indices.lowerFar],
    )
}
