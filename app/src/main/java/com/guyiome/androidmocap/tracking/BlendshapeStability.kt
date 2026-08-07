package com.guyiome.androidmocap.tracking

/**
 * Sentinelle renvoyée par [meanAbsoluteBlendshapeDelta] quand la comparaison est impossible
 * (aucune frame précédente, ou aucun nom en commun) -- volontairement une valeur *au-dessus* de
 * tout seuil de repos plausible plutôt que 0f, pour qu'un manque de données ne soit jamais
 * interprété à tort comme "visage au repos" (ce qui ferait démarrer le compteur de dérive sur des
 * données absentes, ex. la toute première frame après une (re)calibration).
 */
internal const val UNKNOWN_BLENDSHAPE_VARIANCE = Float.MAX_VALUE

/**
 * Delta absolu moyen frame-à-frame entre deux jeux de blendshapes, appariés par [BlendshapeScore.name]
 * (l'ordre/la composition de la liste MediaPipe n'est pas garanti stable d'une frame à l'autre --
 * voir [BlendshapeCatalog]). Métrique volontairement simple (pas d'écart-type/variance statistique
 * au sens strict) : le point 19 de la revue technique demande juste de distinguer "visage qui
 * bouge" de "visage immobile", une moyenne de deltas suffit et reste bon marché à calculer à 60 fps.
 */
internal fun meanAbsoluteBlendshapeDelta(
    previous: List<BlendshapeScore>,
    current: List<BlendshapeScore>,
): Float {
    if (previous.isEmpty() || current.isEmpty()) return UNKNOWN_BLENDSHAPE_VARIANCE
    val previousByName = previous.associate { it.name to it.score }
    var sum = 0f
    var matched = 0
    for (c in current) {
        val p = previousByName[c.name] ?: continue
        sum += kotlin.math.abs(c.score - p)
        matched++
    }
    return if (matched == 0) UNKNOWN_BLENDSHAPE_VARIANCE else sum / matched
}
