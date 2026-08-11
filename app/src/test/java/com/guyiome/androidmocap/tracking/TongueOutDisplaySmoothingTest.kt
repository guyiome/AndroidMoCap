package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [TongueOutDisplayState.next] -- pur, testable en JVM. */
class TongueOutDisplaySmoothingTest {

    private val hold = 300L

    @Test
    fun `etat initial affiche false`() {
        assertFalse(TongueOutDisplayState().displayedAsOut)
    }

    @Test
    fun `TONGUE_OUT met displayedAsOut a true et reinitialise le minuteur`() {
        val state = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 200, holdMs = hold)
        assertTrue(state.displayedAsOut)
        assertEquals(0L, state.msSinceLastPositive)
    }

    @Test
    fun `UNDECIDED reste affiche tant que le delai de maintien n'est pas ecoule`() {
        val afterPositive = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 100, holdMs = hold)
        val state = afterPositive.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 200, holdMs = hold)
        // 200 < 300 -> encore affiche
        assertTrue(state.displayedAsOut)
        assertEquals(200L, state.msSinceLastPositive)
    }

    @Test
    fun `UNDECIDED repasse a false une fois le delai de maintien depasse`() {
        val afterPositive = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 100, holdMs = hold)
        val state = afterPositive.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 350, holdMs = hold)
        // 350 >= 300 -> ne s'affiche plus
        assertFalse(state.displayedAsOut)
        assertEquals(350L, state.msSinceLastPositive)
    }

    @Test
    fun `plusieurs UNDECIDED consecutifs accumulent le temps ecoule jusqu'a expiration`() {
        var state = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 50, holdMs = hold)
        state = state.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 100, holdMs = hold) // cumul 100, encore true
        assertTrue(state.displayedAsOut)
        state = state.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 100, holdMs = hold) // cumul 200, encore true
        assertTrue(state.displayedAsOut)
        state = state.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 150, holdMs = hold) // cumul 350, expire
        assertFalse(state.displayedAsOut)
    }

    @Test
    fun `un TONGUE_OUT reinitialise le minuteur meme apres une serie de UNDECIDED`() {
        var state = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 50, holdMs = hold)
        state = state.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 250, holdMs = hold)
        assertTrue(state.displayedAsOut) // 250 < 300, encore affiche
        state = state.next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 10, holdMs = hold)
        assertEquals(0L, state.msSinceLastPositive)
        assertTrue(state.displayedAsOut)
    }

    @Test
    fun `TONGUE_IN traite comme non positif, meme comportement de maintien que UNDECIDED`() {
        val afterPositive = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 100, holdMs = hold)
        val state = afterPositive.next(TongueEmbeddingClassification.TONGUE_IN, elapsedMs = 200, holdMs = hold)
        assertTrue(state.displayedAsOut)
        val expired = state.next(TongueEmbeddingClassification.TONGUE_IN, elapsedMs = 200, holdMs = hold)
        assertFalse(expired.displayedAsOut)
    }

    @Test
    fun `classification null (etage 3 jamais atteint) traitee comme non positive`() {
        val afterPositive = TongueOutDisplayState().next(TongueEmbeddingClassification.TONGUE_OUT, elapsedMs = 100, holdMs = hold)
        val state = afterPositive.next(null, elapsedMs = 350, holdMs = hold)
        assertFalse(state.displayedAsOut)
    }

    @Test
    fun `sans classification positive, reste a false`() {
        var state = TongueOutDisplayState()
        state = state.next(TongueEmbeddingClassification.UNDECIDED, elapsedMs = 500, holdMs = hold)
        assertFalse(state.displayedAsOut)
    }
}
