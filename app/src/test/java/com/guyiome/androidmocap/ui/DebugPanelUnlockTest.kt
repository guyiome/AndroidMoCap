package com.guyiome.androidmocap.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [DebugPanelUnlockState.registerTap] : fonction pure, aucune dépendance Android. */
class DebugPanelUnlockTest {

    @Test
    fun `etat initial verrouille, tous les taps restent a faire`() {
        val state = DebugPanelUnlockState()
        assertFalse(state.unlocked)
        assertEquals(DEBUG_PANEL_UNLOCK_TAP_COUNT, state.remainingTaps)
    }

    @Test
    fun `un seul tap ne deverrouille pas`() {
        val state = DebugPanelUnlockState().registerTap(nowMs = 1000L)
        assertFalse(state.unlocked)
        assertEquals(1, state.tapCount)
        assertEquals(DEBUG_PANEL_UNLOCK_TAP_COUNT - 1, state.remainingTaps)
    }

    @Test
    fun `des taps consecutifs dans la fenetre deverrouillent au seuil`() {
        var state = DebugPanelUnlockState()
        var now = 0L
        repeat(DEBUG_PANEL_UNLOCK_TAP_COUNT) {
            state = state.registerTap(now)
            now += 100L
        }
        assertTrue(state.unlocked)
        assertEquals(0, state.remainingTaps)
    }

    @Test
    fun `un tap hors fenetre repart de 1 plutot que d'accumuler`() {
        var state = DebugPanelUnlockState()
        repeat(3) { state = state.registerTap(nowMs = it * 100L) } // 3 taps rapprochés
        assertEquals(3, state.tapCount)

        state = state.registerTap(nowMs = 300L + DEBUG_PANEL_UNLOCK_TAP_WINDOW_MS + 1)
        assertEquals(1, state.tapCount)
        assertFalse(state.unlocked)
    }

    @Test
    fun `un tap exactement a la limite de la fenetre compte toujours`() {
        val state = DebugPanelUnlockState()
            .registerTap(nowMs = 0L)
            .registerTap(nowMs = DEBUG_PANEL_UNLOCK_TAP_WINDOW_MS)
        assertEquals(2, state.tapCount)
    }

    @Test
    fun `registerTap est un no-op une fois deverrouille`() {
        var state = DebugPanelUnlockState()
        var now = 0L
        repeat(DEBUG_PANEL_UNLOCK_TAP_COUNT) {
            state = state.registerTap(now)
            now += 100L
        }
        assertTrue(state.unlocked)

        val stateAfterExtraTap = state.registerTap(now + 100L)
        assertEquals(state, stateAfterExtraTap)
    }
}
