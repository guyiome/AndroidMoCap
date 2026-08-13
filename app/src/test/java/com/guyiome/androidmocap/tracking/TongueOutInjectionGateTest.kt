package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [TongueOutInjectionState.next]/[TongueOutInjectionState.confirmed] -- purs, JVM. */
class TongueOutInjectionGateTest {

    @Test
    fun `etat initial non confirme`() {
        assertFalse(TongueOutInjectionState().confirmed())
    }

    @Test
    fun `TONGUE_OUT incremente le compteur`() {
        val state = TongueOutInjectionState().next(TongueEmbeddingClassification.TONGUE_OUT)
        assertEquals(1, state.consecutiveOutFrames)
        assertFalse(state.confirmed())
    }

    @Test
    fun `confirme a partir de 3 TONGUE_OUT consecutifs (seuil par defaut)`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        assertFalse(state.confirmed())
        state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        assertTrue(state.confirmed())
    }

    @Test
    fun `une frame isolee ne suffit pas -- reproduit le residu observe le 13 aout 2026`() {
        var state = TongueOutInjectionState()
            .next(TongueEmbeddingClassification.TONGUE_IN)
            .next(TongueEmbeddingClassification.TONGUE_OUT) // frame isolee, comme 16:00:04.279
            .next(TongueEmbeddingClassification.TONGUE_IN)
        assertFalse(state.confirmed())
    }

    @Test
    fun `une paire consecutive ne suffit pas non plus -- reproduit l'autre residu observe`() {
        var state = TongueOutInjectionState()
            .next(TongueEmbeddingClassification.TONGUE_OUT)
            .next(TongueEmbeddingClassification.TONGUE_OUT) // paire, comme 16:00:10.759/10.888
        assertFalse(state.confirmed())
    }

    @Test
    fun `TONGUE_IN remet le compteur a zero`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        state = state.next(TongueEmbeddingClassification.TONGUE_IN)
        assertEquals(0, state.consecutiveOutFrames)
    }

    @Test
    fun `UNDECIDED remet le compteur a zero`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        state = state.next(TongueEmbeddingClassification.UNDECIDED)
        assertEquals(0, state.consecutiveOutFrames)
    }

    @Test
    fun `null remet le compteur a zero`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        state = state.next(null)
        assertEquals(0, state.consecutiveOutFrames)
    }

    @Test
    fun `seuil personnalise respecte`() {
        var state = TongueOutInjectionState()
        repeat(4) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        assertFalse(state.confirmed(requiredConsecutiveFrames = 5))
        state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        assertTrue(state.confirmed(requiredConsecutiveFrames = 5))
    }

    @Test
    fun `une tenue soutenue reste confirmee frame apres frame`() {
        var state = TongueOutInjectionState()
        repeat(10) {
            state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
            if (it >= 2) assertTrue("frame $it devrait etre confirmee", state.confirmed())
        }
    }
}
