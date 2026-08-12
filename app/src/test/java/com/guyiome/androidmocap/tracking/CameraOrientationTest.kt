package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [snapToRotationBucket], [surfaceRotationEquivalentDegrees] et
 *  [frontCameraRotationDegrees] -- purs, testables en JVM. */
class CameraOrientationTest {

    @Test
    fun `snapToRotationBucket -- palier 0, bornes basses`() {
        assertEquals(0, snapToRotationBucket(0))
        assertEquals(0, snapToRotationBucket(44))
    }

    @Test
    fun `snapToRotationBucket -- palier 0, bornes hautes (via 315+)`() {
        assertEquals(0, snapToRotationBucket(315))
        assertEquals(0, snapToRotationBucket(359))
    }

    @Test
    fun `snapToRotationBucket -- palier 90`() {
        assertEquals(90, snapToRotationBucket(45))
        assertEquals(90, snapToRotationBucket(89))
        assertEquals(90, snapToRotationBucket(90))
        assertEquals(90, snapToRotationBucket(134))
    }

    @Test
    fun `snapToRotationBucket -- palier 180 (trou corrige par rapport a l'ancienne logique MainScreen)`() {
        assertEquals(180, snapToRotationBucket(135))
        assertEquals(180, snapToRotationBucket(179))
        assertEquals(180, snapToRotationBucket(180))
        assertEquals(180, snapToRotationBucket(224))
    }

    @Test
    fun `snapToRotationBucket -- palier 270`() {
        assertEquals(270, snapToRotationBucket(225))
        assertEquals(270, snapToRotationBucket(269))
        assertEquals(270, snapToRotationBucket(270))
        assertEquals(270, snapToRotationBucket(314))
    }

    @Test
    fun `snapToRotationBucket -- valeurs hors plage normalisees (negatives, superieures a 360)`() {
        assertEquals(0, snapToRotationBucket(-10)) // -10 -> 350 -> palier 0
        assertEquals(0, snapToRotationBucket(370)) // 370 -> 10 -> palier 0
        assertEquals(180, snapToRotationBucket(-180)) // -180 -> 180 -> palier 180
    }

    @Test
    fun `surfaceRotationEquivalentDegrees -- 0 et 180 sont des points fixes`() {
        assertEquals(0, surfaceRotationEquivalentDegrees(0))
        assertEquals(180, surfaceRotationEquivalentDegrees(180))
    }

    @Test
    fun `surfaceRotationEquivalentDegrees -- 90 et 270 s'echangent`() {
        assertEquals(270, surfaceRotationEquivalentDegrees(90))
        assertEquals(90, surfaceRotationEquivalentDegrees(270))
    }

    @Test
    fun `frontCameraRotationDegrees -- destination 0 preserve le comportement actuel (sensorOrientation telle quelle)`() {
        assertEquals(270, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = 0))
        assertEquals(90, frontCameraRotationDegrees(sensorOrientationDegrees = 90, destinationRotationDegrees = 0))
    }

    @Test
    fun `frontCameraRotationDegrees -- combinaisons avec repli modulo 360`() {
        assertEquals(0, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = 90))
        assertEquals(90, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = 180))
        assertEquals(180, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = 270))
        assertEquals(180, frontCameraRotationDegrees(sensorOrientationDegrees = 90, destinationRotationDegrees = 90))
    }

    @Test
    fun `chaine complete -- angle brut jusqu'a la rotation caméra finale, sensorOrientation=270 (front camera typique)`() {
        // raw=100 -> palier 90 (tenue paysage) -> equivalent Surface 270 -> (270+270)%360 = 180
        val bucket1 = snapToRotationBucket(100)
        assertEquals(90, bucket1)
        val surface1 = surfaceRotationEquivalentDegrees(bucket1)
        assertEquals(270, surface1)
        assertEquals(180, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = surface1))

        // raw=200 -> palier 180 (tete en bas) -> equivalent Surface 180 -> (270+180)%360 = 90
        val bucket2 = snapToRotationBucket(200)
        assertEquals(180, bucket2)
        val surface2 = surfaceRotationEquivalentDegrees(bucket2)
        assertEquals(180, surface2)
        assertEquals(90, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = surface2))

        // raw=290 -> palier 270 (paysage oppose) -> equivalent Surface 90 -> (270+90)%360 = 0
        val bucket3 = snapToRotationBucket(290)
        assertEquals(270, bucket3)
        val surface3 = surfaceRotationEquivalentDegrees(bucket3)
        assertEquals(90, surface3)
        assertEquals(0, frontCameraRotationDegrees(sensorOrientationDegrees = 270, destinationRotationDegrees = surface3))
    }
}
