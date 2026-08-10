package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [browRaiseRatio] et [browRaiseRatioFromLandmarks] -- purs, testables en JVM. */
class BrowRaiseTest {

    private val delta = 0.001f

    @Test
    fun `sourcil leve donne un ratio plus grand qu'un sourcil abaisse`() {
        val raised = browRaiseRatio(
            inner = 0.4f to 0.3f,
            outer = 0.6f to 0.3f,
            eyeCornerNear = 0f to 0.5f,
            eyeCornerFar = 1f to 0.5f,
        )
        val lowered = browRaiseRatio(
            inner = 0.4f to 0.48f,
            outer = 0.6f to 0.48f,
            eyeCornerNear = 0f to 0.5f,
            eyeCornerFar = 1f to 0.5f,
        )
        assertTrue("sourcil levé ($raised) devrait dépasser sourcil abaissé ($lowered)", raised > lowered)
    }

    @Test
    fun `ratio calcule correctement sur une geometrie connue`() {
        // Coins d'œil espacés de 1.0 (largeur = 1.0), centre en (0.5, 0.5). Inner/outer symétriques,
        // distance au centre = sqrt(0.01+0.04) = sqrt(0.05) pour chacun.
        val ratio = browRaiseRatio(
            inner = 0.4f to 0.3f,
            outer = 0.6f to 0.3f,
            eyeCornerNear = 0f to 0.5f,
            eyeCornerFar = 1f to 0.5f,
        )
        assertEquals(kotlin.math.sqrt(0.05f), ratio, delta)
    }

    @Test
    fun `coins d'oeil confondus renvoie zero plutot qu'une division par zero`() {
        val ratio = browRaiseRatio(
            inner = 0.4f to 0.3f,
            outer = 0.6f to 0.3f,
            eyeCornerNear = 0.5f to 0.5f,
            eyeCornerFar = 0.5f to 0.5f,
        )
        assertEquals(0f, ratio, delta)
    }

    @Test
    fun `browRaiseRatioFromLandmarks lit les bons indices de la liste`() {
        val landmarks = MutableList(400) { 0f to 0f }
        landmarks[BrowLandmarkIndices.RIGHT_EYEBROW.inner] = 0.4f to 0.3f
        landmarks[BrowLandmarkIndices.RIGHT_EYEBROW.outer] = 0.6f to 0.3f
        landmarks[BrowLandmarkIndices.RIGHT_EYEBROW.eyeCornerNear] = 0f to 0.5f
        landmarks[BrowLandmarkIndices.RIGHT_EYEBROW.eyeCornerFar] = 1f to 0.5f

        val ratio = browRaiseRatioFromLandmarks(landmarks, BrowLandmarkIndices.RIGHT_EYEBROW)
        assertEquals(kotlin.math.sqrt(0.05f), ratio, delta)
    }

    @Test
    fun `browRaiseRatioFromLandmarks renvoie zero si la liste est trop courte`() {
        assertEquals(0f, browRaiseRatioFromLandmarks(emptyList(), BrowLandmarkIndices.RIGHT_EYEBROW), delta)
        assertEquals(0f, browRaiseRatioFromLandmarks(List(10) { 0f to 0f }, BrowLandmarkIndices.LEFT_EYEBROW), delta)
    }
}
