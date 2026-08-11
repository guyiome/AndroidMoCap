package com.guyiome.androidmocap.tracking

/**
 * Étage 1 de la cascade de détection de la langue tirée (revue technique, point 15) : porte
 * géométrique quasi gratuite, réutilise le score `jawOpen` déjà produit par MediaPipe à chaque
 * frame (aucun coût supplémentaire) pour éviter d'aller plus loin (recadrage caméra, analyse
 * couleur) tant que la bouche n'est pas assez ouverte pour qu'une langue tirée soit visuellement
 * possible. Seuil provisoire, à affiner sur device via `TONGUE_DIAGNOSTIC_LOGGING`
 * (`MainViewModel`) -- même traitement que les autres constantes non calées sur device de ce
 * projet (`DEFAULT_COLOR_RATIO_THRESHOLD`, etc.).
 */
internal const val DEFAULT_JAW_OPEN_GATE_THRESHOLD = 0.3f

/** `true` si `jawOpenScore` dépasse le seuil d'ouverture de bouche minimal. */
internal fun jawOpenGateOpen(jawOpenScore: Float, threshold: Float = DEFAULT_JAW_OPEN_GATE_THRESHOLD): Boolean =
    jawOpenScore >= threshold

/**
 * Confirmation géométrique de l'étage 1 -- `jawOpen` (blendshape ML) seul peut être trompé : bouche
 * pressée/lèvre inférieure mordue/rentrée produit un `jawOpen` élevé (0,3-0,5+) alors que la bouche
 * est réellement fermée, reproduit deux fois sur device le 11 août 2026 (revue technique, point
 * 15quater/15quinquies) -- une fois en mordant délibérément la lèvre, une fois en position neutre.
 * [mouthOpennessRatio] (`LipLandmarks.kt`), normalisé par la largeur de bouche donc indépendant de
 * la distance/position par rapport à la caméra (contrairement à une taille de recadrage en pixels
 * absolus, dont un premier essai de garde-fou s'est révélé trop fragile -- voir kdoc de
 * `MIN_CROP_DIMENSION_PX`), sépare nettement les deux cas sur toutes les données collectées ce
 * jour-là : bouche pressée/fermée ≈ 0,001-0,12, bouche réellement ouverte (avec ou sans langue) ≈
 * 0,44+. Seuil choisi à mi-chemin avec une marge confortable des deux côtés, pas finement calé.
 */
internal const val DEFAULT_MOUTH_GEOMETRIC_GATE_THRESHOLD = 0.2f

/** `true` si `mouthGeometricRatio` confirme géométriquement une bouche ouverte -- voir kdoc ci-dessus. */
internal fun mouthGeometricGateOpen(mouthGeometricRatio: Float, threshold: Float = DEFAULT_MOUTH_GEOMETRIC_GATE_THRESHOLD): Boolean =
    mouthGeometricRatio >= threshold
