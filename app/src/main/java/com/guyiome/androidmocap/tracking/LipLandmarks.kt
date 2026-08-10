package com.guyiome.androidmocap.tracking

import kotlin.math.abs

/**
 * Indices dans le mesh 478 points de MediaPipe Face Landmarker -- **hypothèse, PAS encore
 * confirmée sur device** (même traitement que [BrowLandmarkIndices], revue technique point 15).
 * Contrairement aux yeux/sourcils, pas de risque d'inversion gauche/droite ici : la région buccale
 * est symétrique et unique, un seul recadrage centré sur la bouche, pas deux moitiés mirrorées à
 * distinguer. Le seul risque est qu'un indice ne pointe pas exactement où on le pense -- valeurs
 * reprises de la convention la plus largement citée pour les lèvres dans les implémentations
 * MediaPipe Face Mesh (coins de bouche 61/291, lèvre supérieure/inférieure externe 0/17, lèvre
 * supérieure/inférieure interne 13/14). À confirmer via `TONGUE_DIAGNOSTIC_LOGGING`
 * (`MainViewModel`) avant de faire confiance au recadrage pour une vraie classification -- un
 * recadrage mal centré donnerait un `tonguePixelRatio` plat/bruité indépendamment de l'état de la
 * langue, signal négatif utile en soi pour repérer le problème.
 */
internal object LipLandmarkIndices {
    const val MOUTH_CORNER_LEFT = 61
    const val MOUTH_CORNER_RIGHT = 291
    const val UPPER_LIP_OUTER = 0
    const val LOWER_LIP_OUTER = 17
    const val UPPER_LIP_INNER = 13
    const val LOWER_LIP_INNER = 14

    /** Le plus grand indice référencé -- sert de garde-fou dans les fonctions ci-dessous. */
    val maxIndex: Int = maxOf(
        MOUTH_CORNER_LEFT,
        MOUTH_CORNER_RIGHT,
        UPPER_LIP_OUTER,
        LOWER_LIP_OUTER,
        UPPER_LIP_INNER,
        LOWER_LIP_INNER,
    )
}

/** Rectangle en pixels image (pas en coordonnées normalisées) à recadrer dans le bitmap caméra. */
internal data class MouthCropRegion(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Ratio d'ouverture de bouche dérivé des landmarks (distance lèvre sup./inf. internes, normalisée
 * par la largeur de bouche) -- diagnostic croisé avec le score `jawOpen` brut de MediaPipe, même
 * esprit que la comparaison EAR géométrique/blendshape déjà en place pour les yeux. 0f si les
 * landmarks sont absents/trop courts ou si les coins de bouche sont confondus.
 */
internal fun mouthOpennessRatio(landmarks: List<Pair<Float, Float>>): Float {
    if (landmarks.size <= LipLandmarkIndices.maxIndex) return 0f
    val cornerLeft = landmarks[LipLandmarkIndices.MOUTH_CORNER_LEFT]
    val cornerRight = landmarks[LipLandmarkIndices.MOUTH_CORNER_RIGHT]
    val mouthWidth = distance(cornerLeft, cornerRight)
    if (mouthWidth <= 0f) return 0f
    val upperInner = landmarks[LipLandmarkIndices.UPPER_LIP_INNER]
    val lowerInner = landmarks[LipLandmarkIndices.LOWER_LIP_INNER]
    return distance(upperInner, lowerInner) / mouthWidth
}

/**
 * Calcule le rectangle (en pixels) à recadrer dans le bitmap caméra pour l'analyse couleur de
 * l'étage 2 -- centré horizontalement sur les coins de bouche avec une marge [marginRatio],
 * étendu verticalement au-delà de la lèvre inférieure d'un facteur [extraDownwardRatio] de la
 * largeur de bouche (une langue tirée dépasse la ligne des lèvres, voir revue technique point 15).
 * Pure -- ne touche à aucun `Bitmap`, se contente de calculer des coordonnées, clampées aux bornes
 * de l'image. `null` si les landmarks sont absents/trop courts, si les dimensions image sont
 * inconnues, ou si la bouche est dégénérée (coins confondus) plutôt qu'un rectangle de taille 0.
 */
internal fun mouthCropRegion(
    landmarks: List<Pair<Float, Float>>,
    imageWidthPx: Int,
    imageHeightPx: Int,
    marginRatio: Float = 0.25f,
    extraDownwardRatio: Float = 0.6f,
): MouthCropRegion? {
    if (landmarks.size <= LipLandmarkIndices.maxIndex) return null
    if (imageWidthPx <= 0 || imageHeightPx <= 0) return null

    val cornerLeft = landmarks[LipLandmarkIndices.MOUTH_CORNER_LEFT]
    val cornerRight = landmarks[LipLandmarkIndices.MOUTH_CORNER_RIGHT]
    val mouthWidthNorm = abs(cornerRight.first - cornerLeft.first)
    if (mouthWidthNorm <= 0f) return null

    val upperLip = landmarks[LipLandmarkIndices.UPPER_LIP_OUTER]
    val lowerLip = landmarks[LipLandmarkIndices.LOWER_LIP_OUTER]
    val topLipNorm = minOf(upperLip.second, lowerLip.second)
    val bottomLipNorm = maxOf(upperLip.second, lowerLip.second)

    val centerXNorm = (cornerLeft.first + cornerRight.first) / 2f
    val halfWidthNorm = (mouthWidthNorm / 2f) * (1f + marginRatio)

    val leftNorm = (centerXNorm - halfWidthNorm).coerceIn(0f, 1f)
    val rightNorm = (centerXNorm + halfWidthNorm).coerceIn(0f, 1f)
    val topNorm = (topLipNorm - mouthWidthNorm * marginRatio).coerceIn(0f, 1f)
    val bottomNorm = (bottomLipNorm + mouthWidthNorm * extraDownwardRatio).coerceIn(0f, 1f)

    val x = (leftNorm * imageWidthPx).toInt()
    val y = (topNorm * imageHeightPx).toInt()
    val width = ((rightNorm - leftNorm) * imageWidthPx).toInt()
    val height = ((bottomNorm - topNorm) * imageHeightPx).toInt()
    if (width <= 0 || height <= 0) return null

    return MouthCropRegion(x, y, width, height)
}

private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
