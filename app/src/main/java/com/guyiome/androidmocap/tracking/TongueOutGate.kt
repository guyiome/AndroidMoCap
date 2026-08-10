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
