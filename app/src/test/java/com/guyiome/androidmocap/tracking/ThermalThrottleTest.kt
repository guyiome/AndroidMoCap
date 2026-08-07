package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [ThermalThrottleState.next] : fonction pure, aucune dépendance Android/timing réel. */
class ThermalThrottleTest {

    @Test
    fun `etat initial reste au debit nominal, pas en throttling`() {
        val state = ThermalThrottleState.initial(nominalFps = 60)
        assertEquals(60, state.currentFps)
        assertFalse(state.isThrottling)
        assertEquals(0, state.consecutiveThrottledTicks)
        assertFalse(state.downgradeSuggested)
    }

    @Test
    fun `un sondage sans chauffe garde le debit nominal`() {
        val state = ThermalThrottleState.initial(nominalFps = 60).next(nominalFps = 60, isThermalThrottling = false)
        assertEquals(60, state.currentFps)
        assertFalse(state.isThrottling)
        assertEquals(0, state.consecutiveThrottledTicks)
    }

    @Test
    fun `un sondage en chauffe divise le debit nominal par deux`() {
        val state = ThermalThrottleState.initial(nominalFps = 60).next(nominalFps = 60, isThermalThrottling = true)
        assertEquals(30, state.currentFps)
        assertTrue(state.isThrottling)
        assertEquals(1, state.consecutiveThrottledTicks)
    }

    @Test
    fun `le debit reduit ne descend jamais sous le plancher`() {
        val state = ThermalThrottleState.initial(nominalFps = 15).next(nominalFps = 15, isThermalThrottling = true)
        assertEquals(MIN_THROTTLED_FPS, state.currentFps)
    }

    @Test
    fun `le debit remonte immediatement des que la chauffe retombe`() {
        var state = ThermalThrottleState.initial(nominalFps = 60)
        repeat(3) { state = state.next(nominalFps = 60, isThermalThrottling = true) }
        assertEquals(30, state.currentFps)

        state = state.next(nominalFps = 60, isThermalThrottling = false)
        assertEquals(60, state.currentFps)
        assertFalse(state.isThrottling)
        assertEquals(0, state.consecutiveThrottledTicks)
    }

    @Test
    fun `downgradeSuggested reste faux avant le seuil de sondages consecutifs`() {
        var state = ThermalThrottleState.initial(nominalFps = 60)
        repeat(SUSTAINED_THROTTLE_TICKS - 1) { state = state.next(nominalFps = 60, isThermalThrottling = true) }
        assertFalse(state.downgradeSuggested)
    }

    @Test
    fun `downgradeSuggested devient vrai exactement au seuil de sondages consecutifs`() {
        var state = ThermalThrottleState.initial(nominalFps = 60)
        repeat(SUSTAINED_THROTTLE_TICKS) { state = state.next(nominalFps = 60, isThermalThrottling = true) }
        assertTrue(state.downgradeSuggested)
    }

    @Test
    fun `downgradeSuggested est sticky -- reste vrai apres un refroidissement`() {
        var state = ThermalThrottleState.initial(nominalFps = 60)
        repeat(SUSTAINED_THROTTLE_TICKS) { state = state.next(nominalFps = 60, isThermalThrottling = true) }
        assertTrue(state.downgradeSuggested)

        state = state.next(nominalFps = 60, isThermalThrottling = false)
        assertTrue(state.downgradeSuggested)
        assertEquals(60, state.currentFps)
    }

    @Test
    fun `des rafales courtes et separees de chauffe ne declenchent pas downgradeSuggested`() {
        var state = ThermalThrottleState.initial(nominalFps = 60)
        repeat(3) {
            repeat(SUSTAINED_THROTTLE_TICKS - 1) { state = state.next(nominalFps = 60, isThermalThrottling = true) }
            state = state.next(nominalFps = 60, isThermalThrottling = false) // interrompt la rafale, remet le compteur a zero
        }
        assertFalse(state.downgradeSuggested)
    }
}
