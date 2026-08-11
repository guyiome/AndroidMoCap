package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [jawOpenGateOpen] et [mouthGeometricGateOpen] -- purs, testables en JVM. */
class TongueOutGateTest {

    @Test
    fun `porte fermee sous le seuil`() {
        assertFalse(jawOpenGateOpen(0.1f, threshold = 0.3f))
    }

    @Test
    fun `porte ouverte pile au seuil`() {
        assertTrue(jawOpenGateOpen(0.3f, threshold = 0.3f))
    }

    @Test
    fun `porte ouverte au-dessus du seuil`() {
        assertTrue(jawOpenGateOpen(0.8f, threshold = 0.3f))
    }

    @Test
    fun `borne basse 0f fermee avec le seuil par defaut`() {
        assertFalse(jawOpenGateOpen(0f))
    }

    @Test
    fun `borne haute 1f ouverte avec le seuil par defaut`() {
        assertTrue(jawOpenGateOpen(1f))
    }

    @Test
    fun `porte geometrique fermee sous le seuil -- reproduit le cas bouche pressee-mordue`() {
        // Valeurs reproduisant la plage mesurée sur device le 11 août 2026 (bouche pressée/mordue,
        // jawOpen ML trompé mais mouthGeo restant bas) -- voir revue technique, point 15quinquies.
        assertFalse(mouthGeometricGateOpen(0.02f, threshold = 0.2f))
        assertFalse(mouthGeometricGateOpen(0.113f, threshold = 0.2f))
    }

    @Test
    fun `porte geometrique ouverte pile au seuil`() {
        assertTrue(mouthGeometricGateOpen(0.2f, threshold = 0.2f))
    }

    @Test
    fun `porte geometrique ouverte au-dessus du seuil -- reproduit une vraie langue tiree tenue`() {
        assertTrue(mouthGeometricGateOpen(0.55f, threshold = 0.2f))
    }

    @Test
    fun `porte geometrique bornes avec le seuil par defaut`() {
        assertFalse(mouthGeometricGateOpen(0f))
        assertTrue(mouthGeometricGateOpen(1f))
    }
}
