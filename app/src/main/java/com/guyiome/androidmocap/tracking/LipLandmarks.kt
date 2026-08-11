package com.guyiome.androidmocap.tracking

import kotlin.math.abs

/**
 * Indices dans le mesh 478 points de MediaPipe Face Landmarker. **Coins de bouche et lèvre
 * externe confirmés sur device le 11 août 2026** (première hypothèse -- 61/291 comme coins,
 * 0/17 comme lèvre sup./inf. externe -- infirmée par les coordonnées réelles : `mouthCropRegion`
 * produisait un rectangle de 2x3px constant, révélant que 61/291 sont en réalité quasi superposés
 * en X (Δx≈0,001, Δy≈0,046 -- une paire VERTICALE, pas des coins) alors que 0/17 partagent
 * exactement le même Y (Δx≈0,052, Δy≈0,000 -- une paire HORIZONTALE, les vrais coins). Indices
 * corrigés en conséquence ci-dessous. **13/14 restent une hypothèse non confirmée** : les
 * coordonnées observées les montrent eux aussi plutôt horizontaux (Δx≈0,029, Δy≈0,002), pas la
 * paire verticale "lèvre interne" attendue -- à revérifier avant de faire confiance à
 * [mouthOpennessRatio] pour autre chose qu'un diagnostic relatif.
 */
internal object LipLandmarkIndices {
    const val MOUTH_CORNER_LEFT = 0
    const val MOUTH_CORNER_RIGHT = 17
    const val UPPER_LIP_OUTER = 61
    const val LOWER_LIP_OUTER = 291
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
 * Taille minimale (largeur ET hauteur, px) sous laquelle un recadrage est considéré dégénéré plutôt
 * que fiable -- trouvé sur device le 11 août 2026 (revue technique, point 15quater) : bouche fermée
 * + lèvre inférieure mordue/rentrée produit un recadrage de 7-14px de large (la ligne de contact
 * entre les lèvres pressées, pas l'intérieur de la bouche), contre 27-63px pour une bouche
 * réellement ouverte sur les mêmes sessions -- lu comme "100% couleur langue" à tort par l'étage 2
 * ET l'étage 3, alors que jawOpen (blendshape ML) restait élevé malgré une bouche géométriquement
 * fermée (mouthOpennessRatio proche de 0). Un premier seuil à 15px a réduit le problème sans le
 * résoudre -- 67% des faux `TONGUE_OUT` restants tombaient à 15-17px, juste au-dessus de ce seuil
 * trop juste -- remonté à 25px, nettement au-dessus de ce groupe résiduel. Valeur provisoire, pas
 * finement calée -- à réviser si de futures données montrent un chevauchement (même esprit que
 * InferenceLoadMonitor.marginRatio, "garde-fou conservateur non validé" documenté comme tel plutôt
 * que présenté comme définitif).
 */
internal const val MIN_CROP_DIMENSION_PX = 25

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
 * inconnues, si la bouche est dégénérée (coins confondus) plutôt qu'un rectangle de taille 0, ou si
 * le rectangle résultant est plus petit que [MIN_CROP_DIMENSION_PX] dans une dimension ou l'autre
 * (bouche pressée/lèvre mordue -- voir son kdoc).
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
    if (width < MIN_CROP_DIMENSION_PX || height < MIN_CROP_DIMENSION_PX) return null

    return MouthCropRegion(x, y, width, height)
}

private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
