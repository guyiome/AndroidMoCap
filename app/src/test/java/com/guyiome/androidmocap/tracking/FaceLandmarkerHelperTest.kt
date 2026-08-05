package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couvre [FaceLandmarkerHelper.computeEyeGazeDegrees] (rendue `internal` pour ce test) -- la
 * conversion blendshapes eyeLookX -> angle approximatif, un point déjà fragile côté nommage
 * (cf. eyeSquint) donc à couvrir dès que possible.
 */
class FaceLandmarkerHelperTest {

    private val delta = 0.01f

    private fun score(name: String, value: Float) = BlendshapeScore(name, value)

    @Test
    fun `aucun blendshape de regard -- angle nul des deux cotes`() {
        val (left, right) = FaceLandmarkerHelper.computeEyeGazeDegrees(emptyList())
        assertEquals(0f, left[0], delta)
        assertEquals(0f, left[1], delta)
        assertEquals(0f, right[0], delta)
        assertEquals(0f, right[1], delta)
    }

    @Test
    fun `regard vers le haut a gauche -- pitch positif, cote gauche uniquement`() {
        val blendshapes = listOf(score("eyeLookUpLeft", 1f))
        val (left, right) = FaceLandmarkerHelper.computeEyeGazeDegrees(blendshapes)
        assertEquals(30f, left[0], delta)
        assertEquals(0f, left[1], delta)
        assertEquals(0f, right[0], delta)
        assertEquals(0f, right[1], delta)
    }

    @Test
    fun `regard vers le bas -- pitch negatif`() {
        val blendshapes = listOf(score("eyeLookDownRight", 1f))
        val (_, right) = FaceLandmarkerHelper.computeEyeGazeDegrees(blendshapes)
        assertEquals(-30f, right[0], delta)
    }

    @Test
    fun `eyeLookOutLeft et eyeLookInRight donnent un yaw de meme signe -- regard vers l'exterieur`() {
        // "Out" pour l'œil gauche et "In" pour l'œil droit correspondent tous les deux à un
        // regard vers la gauche du sujet (convention documentée : yaw positif = vers la gauche).
        val blendshapes = listOf(score("eyeLookOutLeft", 1f), score("eyeLookInRight", 1f))
        val (left, right) = FaceLandmarkerHelper.computeEyeGazeDegrees(blendshapes)
        assertEquals(30f, left[1], delta)
        assertEquals(30f, right[1], delta)
    }

    @Test
    fun `intensite partielle -- angle proportionnel`() {
        val blendshapes = listOf(score("eyeLookUpLeft", 0.5f))
        val (left, _) = FaceLandmarkerHelper.computeEyeGazeDegrees(blendshapes)
        assertEquals(15f, left[0], delta)
    }
}
