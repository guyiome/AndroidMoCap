package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [correctMouthDimpleLeftRight] -- pur, testable en JVM. */
class MouthDimpleCorrectionTest {

    private val delta = 0.001f

    @Test
    fun `echange les scores entre mouthDimpleLeft et mouthDimpleRight`() {
        val input = listOf(
            BlendshapeScore("mouthDimpleLeft", 0.8f),
            BlendshapeScore("mouthDimpleRight", 0.1f),
        )
        val corrected = correctMouthDimpleLeftRight(input)
        val byName = corrected.associate { it.name to it.score }
        assertEquals(0.1f, byName.getValue("mouthDimpleLeft"), delta)
        assertEquals(0.8f, byName.getValue("mouthDimpleRight"), delta)
    }

    @Test
    fun `laisse les autres blendshapes inchanges`() {
        val input = listOf(
            BlendshapeScore("mouthDimpleLeft", 0.8f),
            BlendshapeScore("mouthDimpleRight", 0.1f),
            BlendshapeScore("jawOpen", 0.5f),
            BlendshapeScore("eyeBlinkLeft", 0.3f),
        )
        val corrected = correctMouthDimpleLeftRight(input)
        val byName = corrected.associate { it.name to it.score }
        assertEquals(0.5f, byName.getValue("jawOpen"), delta)
        assertEquals(0.3f, byName.getValue("eyeBlinkLeft"), delta)
    }

    @Test
    fun `ne modifie rien si l une des deux entrees est absente`() {
        val onlyLeft = listOf(BlendshapeScore("mouthDimpleLeft", 0.8f), BlendshapeScore("jawOpen", 0.5f))
        assertEquals(onlyLeft, correctMouthDimpleLeftRight(onlyLeft))

        val neither = listOf(BlendshapeScore("jawOpen", 0.5f))
        assertEquals(neither, correctMouthDimpleLeftRight(neither))
    }

    @Test
    fun `applique deux fois redonne les scores d'origine`() {
        val input = listOf(
            BlendshapeScore("mouthDimpleLeft", 0.7f),
            BlendshapeScore("mouthDimpleRight", 0.2f),
        )
        val twice = correctMouthDimpleLeftRight(correctMouthDimpleLeftRight(input))
        val byName = twice.associate { it.name to it.score }
        assertEquals(0.7f, byName.getValue("mouthDimpleLeft"), delta)
        assertEquals(0.2f, byName.getValue("mouthDimpleRight"), delta)
    }
}
