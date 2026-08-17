package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class BlendshapeWeightingTest {

    private fun scores(vararg pairs: Pair<String, Float>) = pairs.map { (name, score) -> BlendshapeScore(name, score) }

    @Test
    fun `poids neutre laisse le score inchange`() {
        val input = scores("jawOpen" to 0.4f)
        assertEquals(input, applyBlendshapeWeights(input, mapOf("jawOpen" to BLENDSHAPE_WEIGHT_DEFAULT)))
    }

    @Test
    fun `blendshape absent de la map traite comme neutre`() {
        val input = scores("jawOpen" to 0.4f)
        assertEquals(input, applyBlendshapeWeights(input, emptyMap()))
    }

    @Test
    fun `poids superieur a 1 amplifie le score`() {
        val result = applyBlendshapeWeights(scores("mouthSmileLeft" to 0.3f), mapOf("mouthSmileLeft" to 2f))
        assertEquals(0.6f, result.single().score, 0.0001f)
    }

    @Test
    fun `poids inferieur a 1 attenue le score`() {
        val result = applyBlendshapeWeights(scores("mouthSmileLeft" to 0.4f), mapOf("mouthSmileLeft" to 0.5f))
        assertEquals(0.2f, result.single().score, 0.0001f)
    }

    @Test
    fun `poids maximal borne le resultat a 1`() {
        val result = applyBlendshapeWeights(scores("eyeBlinkLeft" to 0.8f), mapOf("eyeBlinkLeft" to BLENDSHAPE_WEIGHT_MAX))
        assertEquals(1f, result.single().score, 0.0001f)
    }

    @Test
    fun `poids minimal ne descend jamais sous 0`() {
        val result = applyBlendshapeWeights(scores("cheekPuff" to 0f), mapOf("cheekPuff" to BLENDSHAPE_WEIGHT_MIN))
        assertEquals(0f, result.single().score, 0.0001f)
    }

    @Test
    fun `seuls les blendshapes concernes sont modifies, les autres passent inchanges`() {
        val input = scores("jawOpen" to 0.5f, "mouthSmileLeft" to 0.2f)
        val result = applyBlendshapeWeights(input, mapOf("mouthSmileLeft" to 2f))
        assertEquals(0.5f, result[0].score, 0.0001f) // jawOpen inchangé
        assertEquals(0.4f, result[1].score, 0.0001f) // mouthSmileLeft doublé
    }

    @Test
    fun `liste vide renvoie une liste vide`() {
        assertEquals(emptyList<BlendshapeScore>(), applyBlendshapeWeights(emptyList(), mapOf("jawOpen" to 2f)))
    }
}
