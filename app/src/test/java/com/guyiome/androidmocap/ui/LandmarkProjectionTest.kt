package com.guyiome.androidmocap.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couvre [LandmarkProjection.toScreenPoint] : projette un point de mesh MediaPipe (coordonnées
 * normalisées [0,1], espace de l'image droite non-mirroir envoyée au modèle -- voir
 * [com.guyiome.androidmocap.camera.CameraController]) vers l'espace écran (pixels) de l'overlay,
 * avec effet miroir optionnel pour correspondre à l'aperçu caméra (mirroré par CameraX). Fonction
 * pure (aucune dépendance Android/Compose), testable en JVM.
 */
class LandmarkProjectionTest {

    private val delta = 0.01f

    @Test
    fun `sans miroir, un point normalise se projette proportionnellement`() {
        val (x, y) = LandmarkProjection.toScreenPoint(
            normalizedX = 0.25f,
            normalizedY = 0.75f,
            canvasWidthPx = 800f,
            canvasHeightPx = 400f,
            mirror = false,
        )
        assertEquals(200f, x, delta)
        assertEquals(300f, y, delta)
    }

    @Test
    fun `sans miroir, les coins correspondent aux coins`() {
        val topLeft = LandmarkProjection.toScreenPoint(0f, 0f, 800f, 400f, mirror = false)
        assertEquals(0f, topLeft.first, delta)
        assertEquals(0f, topLeft.second, delta)

        val bottomRight = LandmarkProjection.toScreenPoint(1f, 1f, 800f, 400f, mirror = false)
        assertEquals(800f, bottomRight.first, delta)
        assertEquals(400f, bottomRight.second, delta)
    }

    @Test
    fun `avec miroir, l'axe X est inverse mais pas Y`() {
        val (x, y) = LandmarkProjection.toScreenPoint(
            normalizedX = 0.25f,
            normalizedY = 0.75f,
            canvasWidthPx = 800f,
            canvasHeightPx = 400f,
            mirror = true,
        )
        // 1 - 0.25 = 0.75 -> 0.75 * 800
        assertEquals(600f, x, delta)
        assertEquals(300f, y, delta)
    }

    @Test
    fun `avec miroir, un point au centre ne bouge pas`() {
        val (x, y) = LandmarkProjection.toScreenPoint(0.5f, 0.5f, 800f, 400f, mirror = true)
        assertEquals(400f, x, delta)
        assertEquals(200f, y, delta)
    }
}
