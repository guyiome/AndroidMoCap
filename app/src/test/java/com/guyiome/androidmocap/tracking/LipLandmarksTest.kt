package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Couvre [mouthOpennessRatio] et [mouthCropRegion] -- purs, testables en JVM. */
class LipLandmarksTest {

    private val delta = 0.001f

    private fun landmarksWith(vararg entries: Pair<Int, Pair<Float, Float>>): List<Pair<Float, Float>> {
        val list = MutableList(400) { 0f to 0f }
        entries.forEach { (index, value) -> list[index] = value }
        return list
    }

    @Test
    fun `mouthOpennessRatio calcule correctement sur une geometrie connue`() {
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0f to 0.5f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (1f to 0.5f),
            LipLandmarkIndices.UPPER_LIP_INNER to (0.5f to 0.45f),
            LipLandmarkIndices.LOWER_LIP_INNER to (0.5f to 0.55f),
        )
        // largeur de bouche = 1.0, ecart levres internes = 0.1 -> ratio = 0.1
        assertEquals(0.1f, mouthOpennessRatio(landmarks), delta)
    }

    @Test
    fun `mouthOpennessRatio renvoie zero si la liste est trop courte`() {
        assertEquals(0f, mouthOpennessRatio(emptyList()), delta)
        assertEquals(0f, mouthOpennessRatio(List(10) { 0f to 0f }), delta)
    }

    @Test
    fun `mouthOpennessRatio renvoie zero si les coins de bouche sont confondus`() {
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0.5f to 0.5f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (0.5f to 0.5f),
        )
        assertEquals(0f, mouthOpennessRatio(landmarks), delta)
    }

    @Test
    fun `mouthCropRegion calcule un rectangle coherent sur une geometrie connue`() {
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0.4f to 0.5f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (0.6f to 0.5f),
            LipLandmarkIndices.UPPER_LIP_OUTER to (0.5f to 0.45f),
            LipLandmarkIndices.LOWER_LIP_OUTER to (0.5f to 0.55f),
        )
        // mouthWidthNorm = 0.2, marge 0.25 -> demi-largeur = 0.125 -> [0.375, 0.625]
        // top = 0.45 - 0.2*0.25 = 0.4 ; bottom = 0.55 + 0.2*0.6 = 0.67
        val region = mouthCropRegion(landmarks, imageWidthPx = 1000, imageHeightPx = 1000)
        assertTrue("region attendue non nulle", region != null)
        region!!
        assertTrue("x proche de 375, obtenu ${region.x}", abs(region.x - 375) <= 1)
        assertTrue("y proche de 400, obtenu ${region.y}", abs(region.y - 400) <= 1)
        assertTrue("width proche de 250, obtenu ${region.width}", abs(region.width - 250) <= 1)
        assertTrue("height proche de 270, obtenu ${region.height}", abs(region.height - 270) <= 1)
    }

    @Test
    fun `mouthCropRegion clampe aux bornes de l'image quand la bouche est proche du bord`() {
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0.02f to 0.02f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (0.08f to 0.02f),
            LipLandmarkIndices.UPPER_LIP_OUTER to (0.05f to 0.0f),
            LipLandmarkIndices.LOWER_LIP_OUTER to (0.05f to 0.04f),
        )
        val region = mouthCropRegion(landmarks, imageWidthPx = 1000, imageHeightPx = 1000)
        assertTrue("region attendue non nulle", region != null)
        region!!
        assertTrue("x clampe a 0 ou proche, obtenu ${region.x}", region.x >= 0)
        assertTrue("y clampe a 0 ou proche, obtenu ${region.y}", region.y >= 0)
    }

    @Test
    fun `mouthCropRegion renvoie null si la liste est trop courte`() {
        assertNull(mouthCropRegion(emptyList(), imageWidthPx = 1000, imageHeightPx = 1000))
        assertNull(mouthCropRegion(List(10) { 0f to 0f }, imageWidthPx = 1000, imageHeightPx = 1000))
    }

    @Test
    fun `mouthCropRegion renvoie null si les dimensions image sont invalides`() {
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0.4f to 0.5f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (0.6f to 0.5f),
            LipLandmarkIndices.UPPER_LIP_OUTER to (0.5f to 0.45f),
            LipLandmarkIndices.LOWER_LIP_OUTER to (0.5f to 0.55f),
        )
        assertNull(mouthCropRegion(landmarks, imageWidthPx = 0, imageHeightPx = 1000))
        assertNull(mouthCropRegion(landmarks, imageWidthPx = 1000, imageHeightPx = 0))
    }

    @Test
    fun `mouthCropRegion renvoie null si le rectangle est plus petit que MIN_CROP_DIMENSION_PX -- bouche pressee-mordue`() {
        // Reproduit la géométrie mesurée sur device le 11 août 2026 (revue technique, point
        // 15quater) : bouche fermée, coins de bouche très proches horizontalement (lèvre pressée/
        // mordue), qui donnait un recadrage de 7-14px de large avant ce garde-fou.
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0.478f to 0.5f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (0.488f to 0.5f),
            LipLandmarkIndices.UPPER_LIP_OUTER to (0.483f to 0.498f),
            LipLandmarkIndices.LOWER_LIP_OUTER to (0.483f to 0.502f),
        )
        // mouthWidthNorm = 0.01 -> largeur brute avant marge = 0.01*1.25*1000 = 12.5px < 15px
        assertNull(mouthCropRegion(landmarks, imageWidthPx = 1000, imageHeightPx = 1000))
    }

    @Test
    fun `mouthCropRegion renvoie null si les coins de bouche sont confondus`() {
        val landmarks = landmarksWith(
            LipLandmarkIndices.MOUTH_CORNER_LEFT to (0.5f to 0.5f),
            LipLandmarkIndices.MOUTH_CORNER_RIGHT to (0.5f to 0.5f),
            LipLandmarkIndices.UPPER_LIP_OUTER to (0.5f to 0.45f),
            LipLandmarkIndices.LOWER_LIP_OUTER to (0.5f to 0.55f),
        )
        assertNull(mouthCropRegion(landmarks, imageWidthPx = 1000, imageHeightPx = 1000))
    }
}
