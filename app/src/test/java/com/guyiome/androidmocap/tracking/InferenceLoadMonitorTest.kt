package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [InferenceLoadState.next] et [InferenceLoadState.isRunningHigh] -- purs, testables en JVM. */
class InferenceLoadMonitorTest {

    private val delta = 0.01f

    @Test
    fun `premiere frame prend la valeur brute sans lissage`() {
        val state = InferenceLoadState().next(20L)
        assertEquals(20f, state.averageMs, delta)
        assertEquals(1, state.sampleCount)
    }

    @Test
    fun `deuxieme frame applique le lissage EMA`() {
        // premiere frame = 20 (brut) ; deuxieme = alpha*40 + (1-alpha)*20 = 0.2*40 + 0.8*20 = 24
        val state = InferenceLoadState().next(20L).next(40L, alpha = 0.2f)
        assertEquals(24f, state.averageMs, delta)
        assertEquals(2, state.sampleCount)
    }

    @Test
    fun `converge progressivement vers une valeur constante repetee`() {
        var state = InferenceLoadState()
        repeat(50) { state = state.next(30L, alpha = 0.2f) }
        assertEquals(30f, state.averageMs, 0.5f)
    }

    @Test
    fun `isRunningHigh faux tant qu'aucun echantillon n'est integre`() {
        assertFalse(InferenceLoadState().isRunningHigh(frameBudgetMs = 16L))
    }

    @Test
    fun `isRunningHigh faux sous la marge`() {
        // budget 16ms, marge 1.2 -> seuil 19.2ms
        val state = InferenceLoadState().next(18L)
        assertFalse(state.isRunningHigh(frameBudgetMs = 16L, marginRatio = 1.2f))
    }

    @Test
    fun `isRunningHigh vrai au-dessus de la marge`() {
        val state = InferenceLoadState().next(25L)
        assertTrue(state.isRunningHigh(frameBudgetMs = 16L, marginRatio = 1.2f))
    }
}
