package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [jawOpenGateOpen] -- pur, testable en JVM. */
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
}
