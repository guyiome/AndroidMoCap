package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [CalibrationAnomalyState.next] : fonction pure, aucune dépendance Android/timing réel. */
class CalibrationAnomalyTest {

    private fun restDrift(state: CalibrationAnomalyState) =
        state.next(isAtRest = true, poseMagnitudeDegrees = DRIFT_POSE_THRESHOLD_DEGREES, faceDetected = true)

    private fun restNoDrift(state: CalibrationAnomalyState) =
        state.next(isAtRest = true, poseMagnitudeDegrees = 0f, faceDetected = true)

    private fun activeHighPose(state: CalibrationAnomalyState) =
        state.next(isAtRest = false, poseMagnitudeDegrees = DRIFT_POSE_THRESHOLD_DEGREES * 3, faceDetected = true)

    @Test
    fun `etat initial n'est pas flagged`() {
        assertFalse(CalibrationAnomalyState.INITIAL.flagged)
    }

    @Test
    fun `repos plus pose decalee en dessous du seuil ne declenche pas flagged`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(SUSTAINED_DRIFT_FRAMES - 1) { state = restDrift(state) }
        assertFalse(state.flagged)
    }

    @Test
    fun `repos plus pose decalee au seuil declenche flagged`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(SUSTAINED_DRIFT_FRAMES) { state = restDrift(state) }
        assertTrue(state.flagged)
    }

    @Test
    fun `flagged est sticky -- reste vrai meme si la pose revient a zero ensuite`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(SUSTAINED_DRIFT_FRAMES) { state = restDrift(state) }
        assertTrue(state.flagged)

        state = restNoDrift(state)
        assertTrue(state.flagged)
    }

    @Test
    fun `rafales courtes et separees de repos plus derive ne declenchent pas flagged`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(3) {
            repeat(SUSTAINED_DRIFT_FRAMES - 1) { state = restDrift(state) }
            state = restNoDrift(state) // interrompt la rafale, remet le compteur a zero
        }
        assertFalse(state.flagged)
    }

    @Test
    fun `pose hors zero sans etre au repos ne compte pas -- expression active`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(SUSTAINED_DRIFT_FRAMES * 2) { state = activeHighPose(state) }
        assertFalse(state.flagged)
    }

    @Test
    fun `repos avec pose proche de zero ne compte pas -- etat stable post-calibration`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(SUSTAINED_DRIFT_FRAMES * 2) { state = restNoDrift(state) }
        assertFalse(state.flagged)
    }

    @Test
    fun `perte de visage courte suivie d'une redetection ne declenche pas flagged`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION - 1) {
            state = state.next(isAtRest = true, poseMagnitudeDegrees = 0f, faceDetected = false)
        }
        state = state.next(isAtRest = true, poseMagnitudeDegrees = 0f, faceDetected = true)
        assertFalse(state.flagged)
    }

    @Test
    fun `perte de visage suffisamment longue suivie d'une redetection declenche flagged immediatement`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION) {
            state = state.next(isAtRest = true, poseMagnitudeDegrees = 0f, faceDetected = false)
        }
        state = state.next(isAtRest = true, poseMagnitudeDegrees = 0f, faceDetected = true)
        assertTrue(state.flagged)
    }

    @Test
    fun `redetection apres perte longue declenche meme sans etre au repos`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION) {
            state = state.next(isAtRest = false, poseMagnitudeDegrees = 0f, faceDetected = false)
        }
        state = state.next(isAtRest = false, poseMagnitudeDegrees = 0f, faceDetected = true)
        assertTrue(state.flagged)
    }

    @Test
    fun `consecutiveRestDriftFrames se reinitialise correctement`() {
        var state = CalibrationAnomalyState.INITIAL
        repeat(5) { state = restDrift(state) }
        assertEquals(5, state.consecutiveRestDriftFrames)

        state = restNoDrift(state)
        assertEquals(0, state.consecutiveRestDriftFrames)
    }
}
