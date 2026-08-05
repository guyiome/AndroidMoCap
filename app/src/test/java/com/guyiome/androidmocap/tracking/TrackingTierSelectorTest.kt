package com.guyiome.androidmocap.tracking

import com.guyiome.androidmocap.capabilities.DeviceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [TrackingTierSelector.select] : fonction pure, aucune dépendance Android. */
class TrackingTierSelectorTest {

    @Test
    fun `appareil haut de gamme avec ARCore donne le palier OPTIMAL`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = true,
            mediaPerformanceClass = 31, // >= S
            cpuCoreCount = 8,
            totalRamMb = 8000,
        )
        val config = TrackingTierSelector.select(capabilities)
        assertEquals(TrackingTier.OPTIMAL, config.tier)
        assertTrue(config.preferGpuDelegate)
        assertTrue(config.useArCorePose)
        assertEquals(60, config.targetFps)
    }

    @Test
    fun `appareil haut de gamme sans ARCore donne le palier STANDARD`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = false,
            mediaPerformanceClass = 31,
            cpuCoreCount = 8,
            totalRamMb = 8000,
        )
        val config = TrackingTierSelector.select(capabilities)
        assertEquals(TrackingTier.STANDARD, config.tier)
        assertTrue(config.preferGpuDelegate)
        assertFalse(config.useArCorePose)
        assertEquals(30, config.targetFps)
    }

    @Test
    fun `appareil d'entree de gamme donne le palier COMPATIBLE, pas de delegue GPU`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = false,
            mediaPerformanceClass = 0,
            cpuCoreCount = 4,
            totalRamMb = 3000,
        )
        val config = TrackingTierSelector.select(capabilities)
        assertEquals(TrackingTier.COMPATIBLE, config.tier)
        assertFalse(config.preferGpuDelegate)
        assertFalse(config.useArCorePose)
        assertEquals(20, config.targetFps)
    }

    @Test
    fun `heuristique RAM-coeurs sans Media Performance Class declaree donne haut de gamme`() {
        // mediaPerformanceClass == 0 (API < 31 ou non renseigné) : on retombe sur l'heuristique
        // RAM/cœurs (>= 8 cœurs et >= 6 Go), voir DeviceCapabilities.looksHighEnd.
        val capabilities = DeviceCapabilities(
            arCoreSupported = false,
            mediaPerformanceClass = 0,
            cpuCoreCount = 8,
            totalRamMb = 6000,
        )
        assertTrue(capabilities.looksHighEnd)
        assertEquals(TrackingTier.STANDARD, TrackingTierSelector.select(capabilities).tier)
    }

    @Test
    fun `ARCore supporte mais appareil bas de gamme donne quand meme le palier COMPATIBLE`() {
        // ARCore seul ne suffit pas : il faut aussi looksHighEnd pour monter à OPTIMAL/STANDARD.
        val capabilities = DeviceCapabilities(
            arCoreSupported = true,
            mediaPerformanceClass = 0,
            cpuCoreCount = 4,
            totalRamMb = 3000,
        )
        assertEquals(TrackingTier.COMPATIBLE, TrackingTierSelector.select(capabilities).tier)
    }
}
