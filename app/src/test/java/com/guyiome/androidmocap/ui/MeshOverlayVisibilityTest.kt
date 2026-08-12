package com.guyiome.androidmocap.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshOverlayVisibilityTest {

    @Test
    fun `reglage desactive, hors mode eco -- masque`() {
        assertFalse(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = false,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `reglage active, hors mode eco -- affiche`() {
        assertTrue(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = false,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `reglage desactive, mode eco actif, garder-en-eco active -- reste masque`() {
        assertFalse(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }

    @Test
    fun `reglage active mais mode eco actif sans garder-en-eco -- masque quand meme`() {
        assertFalse(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `reglage active, mode eco actif, garder-en-eco active -- reste affiche`() {
        assertTrue(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }

    @Test
    fun `reglage desactive, mode eco actif, garder-en-eco desactive -- masque`() {
        assertFalse(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `reglage desactive, hors mode eco, garder-en-eco active -- masque (sans effet hors eco)`() {
        assertFalse(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = false,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }

    @Test
    fun `reglage active, hors mode eco, garder-en-eco active -- affiche (sans effet hors eco)`() {
        assertTrue(
            computeMeshOverlayVisible(
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = false,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }
}
