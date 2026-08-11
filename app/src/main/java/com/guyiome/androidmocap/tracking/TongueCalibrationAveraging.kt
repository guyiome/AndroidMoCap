package com.guyiome.androidmocap.tracking

/**
 * Accumulateur pur pour la calibration de l'étage 3 (revue technique, point 15) : moyenne un flux
 * d'embeddings capturés pendant une fenêtre de plusieurs secondes, plutôt qu'un seul instantané
 * comme le fait `performCalibration()`/`isCalibrated` (MainViewModel) pour la pose de tête -- une
 * moyenne lisse le bruit inter-frame (angle, exposition, micro-mouvement de langue) qu'un seul
 * embedding capturerait tel quel. `sum`/`sampleCount` publics (pas de champ privé) : accédés depuis
 * les fonctions d'extension de ce même fichier, même convention que `ThermalThrottleState`/
 * `AdaptiveEarFloor` (data class à champs publics, logique portée par des extension functions).
 */
internal data class EmbeddingAccumulator(val sum: FloatArray? = null, val sampleCount: Int = 0)

/**
 * Intègre un nouvel embedding. Premier appel : initialise [EmbeddingAccumulator.sum] à sa taille
 * (copie défensive, ne modifie jamais [embedding] lui-même). Appels suivants : la taille doit
 * rester identique -- signe d'un vrai bug (le modèle d'embedding a changé en cours de session) si
 * ce n'est pas le cas, on fait planter fort plutôt que de produire une moyenne silencieusement
 * fausse (contrairement au reste de la cascade, cette fonction n'est appelée que pendant une
 * calibration explicite déclenchée par l'utilisateur, pas dans le chemin de tracking à 20-60Hz --
 * un crash ici n'affecte jamais le suivi facial normal).
 */
internal fun EmbeddingAccumulator.accumulate(embedding: FloatArray): EmbeddingAccumulator {
    val currentSum = sum
    if (currentSum == null) {
        return EmbeddingAccumulator(sum = embedding.copyOf(), sampleCount = 1)
    }
    require(currentSum.size == embedding.size) {
        "Taille d'embedding incohérente : ${currentSum.size} attendue, ${embedding.size} reçue"
    }
    val newSum = FloatArray(currentSum.size) { i -> currentSum[i] + embedding[i] }
    return EmbeddingAccumulator(sum = newSum, sampleCount = sampleCount + 1)
}

/** Moyenne courante, `null` si aucun échantillon n'a encore été intégré. */
internal fun EmbeddingAccumulator.average(): FloatArray? {
    val currentSum = sum ?: return null
    return FloatArray(currentSum.size) { i -> currentSum[i] / sampleCount }
}
