package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [eyeAspectRatio] et [eyeAspectRatioFromLandmarks] -- purs, testables en JVM. */
class EyeAspectRatioTest {

    private val delta = 0.001f

    @Test
    fun `oeil ouvert donne un EAR dans la plage plausible`() {
        // Géométrie synthétique d'un œil ouvert : coins espacés de 1.0, paupières écartées de
        // 0.2-0.3 verticalement -- reproduit la plage documentée (~0.2-0.35) pour un œil ouvert.
        val ear = eyeAspectRatio(
            cornerNear = 0f to 0.5f,
            cornerFar = 1f to 0.5f,
            upperNear = 0.25f to 0.35f,
            lowerNear = 0.25f to 0.65f,
            upperFar = 0.75f to 0.4f,
            lowerFar = 0.75f to 0.6f,
        )
        assertEquals(0.25f, ear, delta)
    }

    @Test
    fun `oeil ferme donne un EAR proche de zero`() {
        // Mêmes coins, paupières quasi confondues (clignement) -- vertical proche de zéro.
        val ear = eyeAspectRatio(
            cornerNear = 0f to 0.5f,
            cornerFar = 1f to 0.5f,
            upperNear = 0.25f to 0.49f,
            lowerNear = 0.25f to 0.51f,
            upperFar = 0.75f to 0.49f,
            lowerFar = 0.75f to 0.51f,
        )
        assertEquals(0.02f, ear, delta)
    }

    @Test
    fun `coins confondus renvoie zero plutot qu'une division par zero`() {
        val ear = eyeAspectRatio(
            cornerNear = 0.5f to 0.5f,
            cornerFar = 0.5f to 0.5f,
            upperNear = 0.5f to 0.4f,
            lowerNear = 0.5f to 0.6f,
            upperFar = 0.5f to 0.4f,
            lowerFar = 0.5f to 0.6f,
        )
        assertEquals(0f, ear, delta)
    }

    @Test
    fun `eyeAspectRatioFromLandmarks lit les bons indices de la liste`() {
        // Liste de landmarks factice (200 points à l'origine), avec les 6 points du groupe A
        // placés à leurs indices réels -- reproduit la géométrie "œil ouvert" du premier test.
        val landmarks = MutableList(200) { 0f to 0f }
        landmarks[EyeLandmarkIndices.RIGHT_EYE.cornerNear] = 0f to 0.5f
        landmarks[EyeLandmarkIndices.RIGHT_EYE.cornerFar] = 1f to 0.5f
        landmarks[EyeLandmarkIndices.RIGHT_EYE.upperNear] = 0.25f to 0.35f
        landmarks[EyeLandmarkIndices.RIGHT_EYE.lowerNear] = 0.25f to 0.65f
        landmarks[EyeLandmarkIndices.RIGHT_EYE.upperFar] = 0.75f to 0.4f
        landmarks[EyeLandmarkIndices.RIGHT_EYE.lowerFar] = 0.75f to 0.6f

        val ear = eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.RIGHT_EYE)
        assertEquals(0.25f, ear, delta)
    }

    @Test
    fun `eyeAspectRatioFromLandmarks renvoie zero si la liste est trop courte`() {
        assertEquals(0f, eyeAspectRatioFromLandmarks(emptyList(), EyeLandmarkIndices.RIGHT_EYE), delta)
        assertEquals(0f, eyeAspectRatioFromLandmarks(List(10) { 0f to 0f }, EyeLandmarkIndices.LEFT_EYE), delta)
    }
}
