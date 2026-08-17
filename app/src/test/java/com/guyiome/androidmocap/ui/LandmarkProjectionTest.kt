package com.guyiome.androidmocap.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couvre [LandmarkProjection.toScreenPoint] : projette un point de mesh MediaPipe (coordonnées
 * normalisées [0,1], espace de l'image droite non-mirroir envoyée au modèle -- voir
 * [com.guyiome.androidmocap.camera.CameraController]) vers l'espace écran (pixels) de l'overlay,
 * avec effet miroir optionnel pour correspondre à l'aperçu caméra (mirroré par CameraX). Fonction
 * pure (aucune dépendance Android/Compose), testable en JVM.
 *
 * La projection reproduit le
 * recadrage centré "FILL_CENTER" de `PreviewView` quand le ratio de l'image analysée diffère de
 * celui du canvas -- avant, un étirement naïf par axe donnait un mesh écrasé/décalé sur les
 * appareils dont l'écran a un ratio différent de celui de l'image caméra (constaté sur ASUS ROG
 * Phone II, pas sur tablette où les ratios sont proches).
 */
class LandmarkProjectionTest {

    private val delta = 0.01f

    @Test
    fun `ratios identiques, un point normalise se projette proportionnellement`() {
        val (x, y) = LandmarkProjection.toScreenPoint(
            normalizedX = 0.25f,
            normalizedY = 0.75f,
            imageWidthPx = 800f,
            imageHeightPx = 400f,
            canvasWidthPx = 800f,
            canvasHeightPx = 400f,
            mirror = false,
        )
        assertEquals(200f, x, delta)
        assertEquals(300f, y, delta)
    }

    @Test
    fun `ratios identiques, les coins correspondent aux coins`() {
        val topLeft = LandmarkProjection.toScreenPoint(0f, 0f, 800f, 400f, 800f, 400f, mirror = false)
        assertEquals(0f, topLeft.first, delta)
        assertEquals(0f, topLeft.second, delta)

        val bottomRight = LandmarkProjection.toScreenPoint(1f, 1f, 800f, 400f, 800f, 400f, mirror = false)
        assertEquals(800f, bottomRight.first, delta)
        assertEquals(400f, bottomRight.second, delta)
    }

    @Test
    fun `ratios identiques, avec miroir l'axe X est inverse mais pas Y`() {
        val (x, y) = LandmarkProjection.toScreenPoint(
            normalizedX = 0.25f,
            normalizedY = 0.75f,
            imageWidthPx = 800f,
            imageHeightPx = 400f,
            canvasWidthPx = 800f,
            canvasHeightPx = 400f,
            mirror = true,
        )
        // 1 - 0.25 = 0.75 -> 0.75 * 800
        assertEquals(600f, x, delta)
        assertEquals(300f, y, delta)
    }

    @Test
    fun `ratios identiques, avec miroir un point au centre ne bouge pas`() {
        val (x, y) = LandmarkProjection.toScreenPoint(0.5f, 0.5f, 800f, 400f, 800f, 400f, mirror = true)
        assertEquals(400f, x, delta)
        assertEquals(200f, y, delta)
    }

    @Test
    fun `image carree sur canvas etroit, le centre reste centre et les bords sont rognes`() {
        // Image carrée 1000x1000 projetée sur un canvas très vertical (téléphone) 500x1000 :
        // largeur rognée symétriquement des deux côtés (comme PreviewView en FILL_CENTER).
        val center = LandmarkProjection.toScreenPoint(0.5f, 0.5f, 1000f, 1000f, 500f, 1000f, mirror = false)
        assertEquals(250f, center.first, delta)
        assertEquals(500f, center.second, delta)

        // Le coin gauche de l'image tombe hors-canvas (négatif) : il est rogné, pas écrasé dedans.
        val topLeft = LandmarkProjection.toScreenPoint(0f, 0f, 1000f, 1000f, 500f, 1000f, mirror = false)
        assertEquals(-250f, topLeft.first, delta)
        assertEquals(0f, topLeft.second, delta)
    }

    @Test
    fun `image portrait sur canvas large, le centre reste centre et le haut-bas est rogne`() {
        // Image portrait 500x1000 projetée sur un canvas large (paysage) 1000x500 : hauteur rognée.
        val center = LandmarkProjection.toScreenPoint(0.5f, 0.5f, 500f, 1000f, 1000f, 500f, mirror = false)
        assertEquals(500f, center.first, delta)
        assertEquals(250f, center.second, delta)

        val topLeft = LandmarkProjection.toScreenPoint(0f, 0f, 500f, 1000f, 1000f, 500f, mirror = false)
        assertEquals(0f, topLeft.first, delta)
        assertEquals(-750f, topLeft.second, delta)
    }

    @Test
    fun `recadrage et miroir combines, le centre ne bouge pas`() {
        val center = LandmarkProjection.toScreenPoint(0.5f, 0.5f, 1000f, 1000f, 500f, 1000f, mirror = true)
        assertEquals(250f, center.first, delta)
        assertEquals(500f, center.second, delta)
    }

    @Test
    fun `dimensions image inconnues, repli sur l'etirement simple`() {
        val (x, y) = LandmarkProjection.toScreenPoint(
            normalizedX = 0.25f,
            normalizedY = 0.75f,
            imageWidthPx = 0f,
            imageHeightPx = 0f,
            canvasWidthPx = 800f,
            canvasHeightPx = 400f,
            mirror = false,
        )
        assertEquals(200f, x, delta)
        assertEquals(300f, y, delta)
    }
}
