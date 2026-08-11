package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Couvre [newTongueCalibrationRecording], [TongueCalibrationRecordingState.tick] et `.result()` -- purs, JVM. */
class TongueCalibrationRecordingStateTest {

    private val delta = 0.001f
    private val duration = 1000L
    private val prepare = 500L

    @Test
    fun `newTongueCalibrationRecording demarre par langue dehors, accumulateurs vides`() {
        val state = newTongueCalibrationRecording()
        assertEquals(TongueCalibrationPhase.RECORDING_TONGUE_OUT, state.phase)
        assertEquals(0, state.tongueOutAccumulator.sampleCount)
        assertEquals(0, state.tongueInAccumulator.sampleCount)
    }

    @Test
    fun `tick pendant la phase 1 accumule uniquement dans tongueOutAccumulator`() {
        val state = newTongueCalibrationRecording().tick(elapsedMs = 100, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
        assertEquals(1, state.tongueOutAccumulator.sampleCount)
        assertEquals(0, state.tongueInAccumulator.sampleCount)
        assertEquals(TongueCalibrationPhase.RECORDING_TONGUE_OUT, state.phase)
    }

    @Test
    fun `transition juste avant la frontiere reste dans la phase 1`() {
        val state = newTongueCalibrationRecording().tick(elapsedMs = 999, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
        assertEquals(TongueCalibrationPhase.RECORDING_TONGUE_OUT, state.phase)
        assertEquals(999L, state.elapsedMsInPhase)
    }

    @Test
    fun `transition a la frontiere exacte de la phase 1 passe en pause PREPARE_TONGUE_IN`() {
        val state = newTongueCalibrationRecording()
            .tick(elapsedMs = 999, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
            .tick(elapsedMs = 1, embedding = floatArrayOf(2f), durationMs = duration, prepareDurationMs = prepare)
        assertEquals(TongueCalibrationPhase.PREPARE_TONGUE_IN, state.phase)
        assertEquals(0L, state.elapsedMsInPhase)
        assertEquals(2, state.tongueOutAccumulator.sampleCount)
    }

    @Test
    fun `PREPARE_TONGUE_IN n'accumule jamais, meme avec un embedding non-null`() {
        val state = newTongueCalibrationRecording()
            .tick(elapsedMs = duration, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare) // -> PREPARE_TONGUE_IN
            .tick(elapsedMs = 100, embedding = floatArrayOf(9f), durationMs = duration, prepareDurationMs = prepare)
        assertEquals(TongueCalibrationPhase.PREPARE_TONGUE_IN, state.phase)
        assertEquals(1, state.tongueOutAccumulator.sampleCount)
        assertEquals(0, state.tongueInAccumulator.sampleCount)
    }

    @Test
    fun `transition a la frontiere de la pause passe en phase 2`() {
        val state = newTongueCalibrationRecording()
            .tick(elapsedMs = duration, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare) // -> PREPARE_TONGUE_IN
            .tick(elapsedMs = prepare, embedding = null, durationMs = duration, prepareDurationMs = prepare) // -> RECORDING_TONGUE_IN
        assertEquals(TongueCalibrationPhase.RECORDING_TONGUE_IN, state.phase)
        assertEquals(0L, state.elapsedMsInPhase)
    }

    @Test
    fun `tick pendant la phase 2 accumule uniquement dans tongueInAccumulator`() {
        val state = newTongueCalibrationRecording()
            .tick(elapsedMs = duration, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare) // -> PREPARE_TONGUE_IN
            .tick(elapsedMs = prepare, embedding = null, durationMs = duration, prepareDurationMs = prepare) // -> RECORDING_TONGUE_IN
            .tick(elapsedMs = 100, embedding = floatArrayOf(9f), durationMs = duration, prepareDurationMs = prepare)
        assertEquals(TongueCalibrationPhase.RECORDING_TONGUE_IN, state.phase)
        assertEquals(1, state.tongueOutAccumulator.sampleCount)
        assertEquals(1, state.tongueInAccumulator.sampleCount)
    }

    @Test
    fun `result null avant DONE`() {
        val state = newTongueCalibrationRecording().tick(elapsedMs = 100, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
        assertNull(state.result())
    }

    @Test
    fun `result correct une fois DONE`() {
        val state = newTongueCalibrationRecording()
            .tick(elapsedMs = duration, embedding = floatArrayOf(1f, 0f), durationMs = duration, prepareDurationMs = prepare) // -> PREPARE_TONGUE_IN
            .tick(elapsedMs = prepare, embedding = null, durationMs = duration, prepareDurationMs = prepare) // -> RECORDING_TONGUE_IN
            .tick(elapsedMs = duration, embedding = floatArrayOf(0f, 1f), durationMs = duration, prepareDurationMs = prepare) // -> DONE
        assertEquals(TongueCalibrationPhase.DONE, state.phase)
        val result = state.result()
        assertArrayEquals(floatArrayOf(1f, 0f), result?.tongueOutReference, delta)
        assertArrayEquals(floatArrayOf(0f, 1f), result?.tongueInReference, delta)
    }

    @Test
    fun `tick a embedding null avance quand meme le temps sans accumuler`() {
        val state = newTongueCalibrationRecording().tick(elapsedMs = 500, embedding = null, durationMs = duration, prepareDurationMs = prepare)
        assertEquals(0, state.tongueOutAccumulator.sampleCount)
        assertEquals(500L, state.elapsedMsInPhase)
    }

    @Test
    fun `tick apres DONE est un no-op`() {
        val done = newTongueCalibrationRecording()
            .tick(elapsedMs = duration, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
            .tick(elapsedMs = prepare, embedding = null, durationMs = duration, prepareDurationMs = prepare)
            .tick(elapsedMs = duration, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
        assertEquals(TongueCalibrationPhase.DONE, done.phase)
        val after = done.tick(elapsedMs = 100, embedding = floatArrayOf(9f), durationMs = duration, prepareDurationMs = prepare)
        assertSame(done, after)
    }

    @Test
    fun `tick sur IDLE est un no-op`() {
        val idle = TongueCalibrationRecordingState()
        val after = idle.tick(elapsedMs = 100, embedding = floatArrayOf(1f), durationMs = duration, prepareDurationMs = prepare)
        assertSame(idle, after)
    }
}
