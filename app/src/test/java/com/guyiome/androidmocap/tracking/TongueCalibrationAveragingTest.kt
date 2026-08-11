package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Couvre [EmbeddingAccumulator.accumulate] et [EmbeddingAccumulator.average] -- purs, JVM. */
class TongueCalibrationAveragingTest {

    private val delta = 0.001f

    @Test
    fun `average null sur un accumulateur vide`() {
        assertNull(EmbeddingAccumulator().average())
    }

    @Test
    fun `un seul echantillon donne une moyenne egale a lui-meme`() {
        val state = EmbeddingAccumulator().accumulate(floatArrayOf(1f, 2f, 3f))
        assertEquals(1, state.sampleCount)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), state.average(), delta)
    }

    @Test
    fun `deux echantillons donnent la moyenne par dimension`() {
        // [1,2] + [3,4] -> somme [4,6] -> moyenne [2,3]
        val state = EmbeddingAccumulator()
            .accumulate(floatArrayOf(1f, 2f))
            .accumulate(floatArrayOf(3f, 4f))
        assertEquals(2, state.sampleCount)
        assertArrayEquals(floatArrayOf(2f, 3f), state.average(), delta)
    }

    @Test
    fun `accumulate ne modifie pas le tableau passe en entree`() {
        val input = floatArrayOf(5f, 6f)
        EmbeddingAccumulator().accumulate(input)
        assertArrayEquals(floatArrayOf(5f, 6f), input, delta)
    }

    @Test
    fun `accumulate leve une exception sur une taille incoherente`() {
        val state = EmbeddingAccumulator().accumulate(floatArrayOf(1f, 2f))
        assertThrows(IllegalArgumentException::class.java) {
            state.accumulate(floatArrayOf(1f, 2f, 3f))
        }
    }
}
