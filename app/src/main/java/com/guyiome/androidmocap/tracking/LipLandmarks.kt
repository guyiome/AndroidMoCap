package com.guyiome.androidmocap.tracking

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Indices dans le mesh 478 points de MediaPipe Face Landmarker. **Coins de bouche et lèvre
 * externe confirmés sur device le 11 août 2026** (première hypothèse -- 61/291 comme coins,
 * 0/17 comme lèvre sup./inf. externe -- infirmée par les coordonnées réelles : `mouthCropRegion`
 * produisait un rectangle de 2x3px constant, révélant que 61/291 sont en réalité quasi superposés
 * en X (Δx≈0,001, Δy≈0,046 -- une paire VERTICALE, pas des coins) alors que 0/17 partagent
 * exactement le même Y (Δx≈0,052, Δy≈0,000 -- une paire HORIZONTALE, les vrais coins). Indices
 * corrigés en conséquence ci-dessous. **13/14 restent une hypothèse non confirmée par inspection
 * visuelle directe** : les coordonnées observées les montrent eux aussi plutôt horizontaux
 * (Δx≈0,029, Δy≈0,002), pas la paire verticale "lèvre interne" attendue. Malgré ça,
 * [mouthOpennessRatio] (qui en dépend) est devenu un gate dur de l'étage 1 le 11 août 2026
 * (`TongueOutGate.mouthGeometricGateOpen`) : quels que soient
 * les indices 13/14 réels, le ratio qu'ils produisent sépare nettement et de façon reproductible
 * bouche pressée (~0,001-0,12) de bouche réellement ouverte (~0,44+) sur plusieurs sessions device
 * indépendantes -- validation comportementale, pas une confirmation des indices eux-mêmes. Si ce
 * gate se montre un jour peu fiable, revérifier 13/14 en premier.
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
 * que fiable -- trouvé sur device le 11 août 2026 : bouche fermée
 * + lèvre inférieure mordue/rentrée produit un recadrage de 7-14px de large (la ligne de contact
 * entre les lèvres pressées, pas l'intérieur de la bouche), contre 27-63px pour une bouche
 * réellement ouverte sur les mêmes sessions -- lu comme "100% couleur langue" à tort par l'étage 2
 * ET l'étage 3, alors que jawOpen (blendshape ML) restait élevé malgré une bouche géométriquement
 * fermée (mouthOpennessRatio proche de 0).
 *
 * **Historique de calage, pour ne pas répéter l'erreur** : un premier seuil à 15px réduisait le
 * faux positif sans le résoudre (faux `TONGUE_OUT` encore fréquents à 15-17px). Remonté à 25px sur
 * la base d'un seul relevé non contrôlé (`logcat -d` sur 6s mélangeant plusieurs gestes, pas un test
 * isolé propre) -- surcorrection confirmée par l'utilisateur : plus aucune détection possible même
 * langue réellement tirée. Redescendu à 15px, dernier état confirmé fonctionnel. **La largeur de
 * recadrage en pixels absolus n'était pas le bon signal principal** -- elle dépend de la
 * distance/position par rapport à la caméra, pas juste du geste. Le vrai correctif retenu est
 * `TongueOutGate.mouthGeometricGateOpen()` (gate dur sur `mouthOpennessRatio`, normalisé donc
 * indépendant de la distance caméra) -- ce garde-fou en
 * pixels **reste en place** comme filet de sécurité complémentaire (pas redondant : les deux
 * signaux ne mesurent pas la même chose, et ce garde-fou reste la seule protection pendant la
 * calibration elle-même, dont l'accès bitmap n'est volontairement pas gaté par
 * `mouthGeometricGateOpen` -- voir kdoc de `MainViewModel.sampleMouthTongueEmbedding`). Même leçon
 * que le seuil couleur de l'étage 2 : ne pas re-deviner un scalaire
 * sans un test contrôlé propre à l'appui.
 */
internal const val MIN_CROP_DIMENSION_PX = 15

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
 * largeur de bouche (une langue tirée dépasse la ligne des lèvres).
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
    return sqrt(dx * dx + dy * dy)
}
