package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [cosineSimilarity] et [classifyTongueState] -- purs, testables en JVM. */
class TongueEmbeddingClassifierTest {

    private val delta = 0.001f

    @Test
    fun `cosineSimilarity vecteurs identiques donne 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, cosineSimilarity(v, v), delta)
    }

    @Test
    fun `cosineSimilarity vecteurs orthogonaux donne 0`() {
        assertEquals(0f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), delta)
    }

    @Test
    fun `cosineSimilarity vecteurs opposes donne -1`() {
        assertEquals(-1f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), delta)
    }

    @Test
    fun `cosineSimilarity vecteur nul donne 0 sans planter`() {
        assertEquals(0f, cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 2f)), delta)
    }

    @Test
    fun `cosineSimilarity tailles differentes donne 0`() {
        assertEquals(0f, cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f)), delta)
    }

    @Test
    fun `classifyTongueState prend la reference la plus proche`() {
        // live proche de tongueOutRef=[1,0] : simOut ~0.9939, simIn ~0.1104, ecart ~0.88 >> marge
        val result = classifyTongueState(
            liveEmbedding = floatArrayOf(0.9f, 0.1f),
            tongueOutReference = floatArrayOf(1f, 0f),
            tongueInReference = floatArrayOf(0f, 1f),
        )
        assertEquals(TongueEmbeddingClassification.TONGUE_OUT, result)
    }

    @Test
    fun `classifyTongueState UNDECIDED si une reference est vide`() {
        val result = classifyTongueState(
            liveEmbedding = floatArrayOf(1f, 0f),
            tongueOutReference = FloatArray(0),
            tongueInReference = floatArrayOf(0f, 1f),
        )
        assertEquals(TongueEmbeddingClassification.UNDECIDED, result)
    }

    @Test
    fun `classifyTongueState UNDECIDED si l'ecart est sous la marge`() {
        // live=[1,1] equidistant de [1,0] et [0,1] -> simOut == simIn -> ecart 0
        val result = classifyTongueState(
            liveEmbedding = floatArrayOf(1f, 1f),
            tongueOutReference = floatArrayOf(1f, 0f),
            tongueInReference = floatArrayOf(0f, 1f),
        )
        assertEquals(TongueEmbeddingClassification.UNDECIDED, result)
    }

    @Test
    fun `classifyTongueState UNDECIDED pile a la marge (egalite stricte)`() {
        val result = classifyTongueState(
            liveEmbedding = floatArrayOf(1f, 1f),
            tongueOutReference = floatArrayOf(1f, 0f),
            tongueInReference = floatArrayOf(0f, 1f),
            margin = 0f,
        )
        assertEquals(TongueEmbeddingClassification.UNDECIDED, result)
    }
}
