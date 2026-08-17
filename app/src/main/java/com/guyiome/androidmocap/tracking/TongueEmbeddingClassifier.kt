package com.guyiome.androidmocap.tracking

import kotlin.math.sqrt

/**
 * Étage 3 de la cascade de détection de la langue tirée : classement
 * par comparaison à deux embeddings de référence enregistrés lors d'une calibration personnelle
 * ("langue dehors" / "langue rentrée") -- plus proche voisin à 2 classes via similarité cosinus.
 * Construit après 7 sessions de test device ayant confirmé que l'étage 2 (couleur seule) ne
 * discrimine pas de façon fiable, sur aucun des deux chemins caméra. Pur -- ne connaît ni
 * `ImageEmbedder` ni `Bitmap`, prend un `FloatArray` déjà calculé en entrée.
 */
internal enum class TongueEmbeddingClassification { TONGUE_OUT, TONGUE_IN, UNDECIDED }

/**
 * Similarité cosinus pure -- réimplémentée plutôt que d'appeler `ImageEmbedder.cosineSimilarity`
 * statique, pour garder ce fichier testable en JVM pur sans dépendance MediaPipe (même discipline
 * que `rgbToHsv` dans `MouthColorAnalysis.kt` réimplémentant `Color.RGBToHSV`). Fonction totale :
 * `0f` si les deux tableaux n'ont pas la même taille ou si l'une des deux normes est nulle, plutôt
 * qu'une exception ou un NaN -- appelée à chaque frame candidate dans le chemin de tracking, ne
 * doit jamais faire planter le pipeline.
 */
internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    if (normA <= 0f || normB <= 0f) return 0f
    return dot / (sqrt(normA) * sqrt(normB))
}

/** Marge minimale entre les deux similarités pour trancher plutôt que renvoyer `UNDECIDED` --
 *  seuil provisoire, non calé sur device (même statut que `DEFAULT_COLOR_RATIO_THRESHOLD` avant son
 *  premier test réel). */
internal const val DEFAULT_CLASSIFICATION_MARGIN = 0.05f

/**
 * Classe [liveEmbedding] par comparaison à [tongueOutReference]/[tongueInReference].
 * `UNDECIDED` si l'une des deux références est vide (calibration jamais faite -- garde vérifiée
 * AVANT tout calcul de similarité, pas de division accidentelle sur des vecteurs de tailles
 * différentes) ou si l'écart entre les deux similarités ne dépasse pas strictement [margin] --
 * un signal ambigu ne doit jamais forcer une classe, même discipline "honnêteté du signal" que le
 * reste de la cascade (voir `TongueOutGate.kt`/`MouthColorAnalysis.kt`).
 */
internal fun classifyTongueState(
    liveEmbedding: FloatArray,
    tongueOutReference: FloatArray,
    tongueInReference: FloatArray,
    margin: Float = DEFAULT_CLASSIFICATION_MARGIN,
): TongueEmbeddingClassification {
    if (tongueOutReference.isEmpty() || tongueInReference.isEmpty()) {
        return TongueEmbeddingClassification.UNDECIDED
    }
    val simOut = cosineSimilarity(liveEmbedding, tongueOutReference)
    val simIn = cosineSimilarity(liveEmbedding, tongueInReference)
    val diff = simOut - simIn
    return when {
        diff > margin -> TongueEmbeddingClassification.TONGUE_OUT
        diff < -margin -> TongueEmbeddingClassification.TONGUE_IN
        else -> TongueEmbeddingClassification.UNDECIDED
    }
}
