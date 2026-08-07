package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [meanAbsoluteBlendshapeDelta] : fonction pure, aucune dépendance Android. */
class BlendshapeStabilityTest {

    @Test
    fun `liste precedente vide renvoie la sentinelle`() {
        val current = listOf(BlendshapeScore("jawOpen", 0.5f))
        assertEquals(UNKNOWN_BLENDSHAPE_VARIANCE, meanAbsoluteBlendshapeDelta(emptyList(), current))
    }

    @Test
    fun `liste courante vide renvoie la sentinelle`() {
        val previous = listOf(BlendshapeScore("jawOpen", 0.5f))
        assertEquals(UNKNOWN_BLENDSHAPE_VARIANCE, meanAbsoluteBlendshapeDelta(previous, emptyList()))
    }

    @Test
    fun `listes identiques renvoient un delta de zero`() {
        val shapes = listOf(BlendshapeScore("jawOpen", 0.3f), BlendshapeScore("eyeBlinkLeft", 0.1f))
        assertEquals(0f, meanAbsoluteBlendshapeDelta(shapes, shapes))
    }

    @Test
    fun `delta moyen calcule correctement sur des noms communs`() {
        val previous = listOf(BlendshapeScore("jawOpen", 0.2f), BlendshapeScore("eyeBlinkLeft", 0.0f))
        val current = listOf(BlendshapeScore("jawOpen", 0.6f), BlendshapeScore("eyeBlinkLeft", 0.4f))
        // |0.6-0.2| = 0.4, |0.4-0.0| = 0.4 -> moyenne 0.4
        assertEquals(0.4f, meanAbsoluteBlendshapeDelta(previous, current), 0.0001f)
    }

    @Test
    fun `blendshapes sans correspondance de nom sont ignores`() {
        val previous = listOf(BlendshapeScore("jawOpen", 0.2f))
        val current = listOf(BlendshapeScore("jawOpen", 0.6f), BlendshapeScore("mouthPucker", 0.9f))
        // "mouthPucker" absent de previous -> ignore, seul jawOpen compte : |0.6-0.2| = 0.4
        assertEquals(0.4f, meanAbsoluteBlendshapeDelta(previous, current), 0.0001f)
    }

    @Test
    fun `ordre different dans les deux listes n'affecte pas le resultat`() {
        val previous = listOf(BlendshapeScore("jawOpen", 0.2f), BlendshapeScore("eyeBlinkLeft", 0.0f))
        val current = listOf(BlendshapeScore("eyeBlinkLeft", 0.4f), BlendshapeScore("jawOpen", 0.6f))
        assertEquals(0.4f, meanAbsoluteBlendshapeDelta(previous, current), 0.0001f)
    }

    @Test
    fun `aucun nom en commun renvoie la sentinelle`() {
        val previous = listOf(BlendshapeScore("jawOpen", 0.2f))
        val current = listOf(BlendshapeScore("mouthPucker", 0.9f))
        assertEquals(UNKNOWN_BLENDSHAPE_VARIANCE, meanAbsoluteBlendshapeDelta(previous, current))
    }
}
