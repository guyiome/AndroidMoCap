package com.guyiome.androidmocap.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couvre [CameraController.rotatedDimensions] (extrait en fonction pure, sans dépendance
 * android.graphics) -- le reste de la logique de rotation (Matrix/Canvas/Bitmap) a besoin
 * d'Android et n'est pas couvert par des tests JVM purs, voir AndroidMoCap_tests_unitaires.md.
 */
class CameraControllerTest {

    @Test
    fun `rotation 0 degre -- dimensions inchangees`() {
        val (width, height) = CameraController.rotatedDimensions(1280, 720, 0)
        assertEquals(1280, width)
        assertEquals(720, height)
    }

    @Test
    fun `rotation 180 degres -- dimensions inchangees`() {
        val (width, height) = CameraController.rotatedDimensions(1280, 720, 180)
        assertEquals(1280, width)
        assertEquals(720, height)
    }

    @Test
    fun `rotation 90 degres -- dimensions inversees`() {
        val (width, height) = CameraController.rotatedDimensions(1280, 720, 90)
        assertEquals(720, width)
        assertEquals(1280, height)
    }

    @Test
    fun `rotation 270 degres -- dimensions inversees`() {
        val (width, height) = CameraController.rotatedDimensions(1280, 720, 270)
        assertEquals(720, width)
        assertEquals(1280, height)
    }
}
