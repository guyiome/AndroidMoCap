package com.guyiome.androidmocap.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [snapToRotationBucket] -- pur, testable en JVM. */
class RotationBucketTest {

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
}
