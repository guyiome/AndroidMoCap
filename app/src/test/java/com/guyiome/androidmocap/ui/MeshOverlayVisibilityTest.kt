package com.guyiome.androidmocap.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshOverlayVisibilityTest {

    @Test
    fun `ni le reglage ni ARCore actifs, hors mode eco -- masque`() {
        assertFalse(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = false,
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
                usingArCoreCameraSource = false,
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = false,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `ARCore actif meme reglage desactive, hors mode eco -- affiche force`() {
        assertTrue(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = true,
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = false,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `reglage active mais mode eco actif sans garder-en-eco -- masque quand meme`() {
        assertFalse(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = false,
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `ARCore actif mais mode eco actif sans garder-en-eco -- masque quand meme`() {
        assertFalse(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = true,
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = false,
            )
        )
    }

    @Test
    fun `reglage active, mode eco actif, garder-en-eco active -- reste affiche`() {
        assertTrue(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = false,
                faceMeshOverlayEnabled = true,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }

    @Test
    fun `ARCore actif, mode eco actif, garder-en-eco active -- reste affiche`() {
        assertTrue(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = true,
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }

    @Test
    fun `rien du tout actif, garder-en-eco active mais aucune base visible -- masque`() {
        assertFalse(
            computeMeshOverlayVisible(
                usingArCoreCameraSource = false,
                faceMeshOverlayEnabled = false,
                isPowerSaveActive = true,
                keepMeshOverlayInPowerSave = true,
            )
        )
    }
}
