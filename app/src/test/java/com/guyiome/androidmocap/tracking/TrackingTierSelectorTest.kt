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

    @Test
    fun `override non nul court-circuite completement la selection automatique`() {
        // Appareil qui qualifierait normalement pour OPTIMAL (ARCore + haut de gamme) --
        // l'override force STANDARD quand meme, meme TierConfig que la selection automatique
        // aurait produit pour STANDARD sur un autre appareil.
        val highEndArCoreCapabilities = DeviceCapabilities(
            arCoreSupported = true,
            mediaPerformanceClass = 31,
            cpuCoreCount = 8,
            totalRamMb = 8000,
        )
        val config = TrackingTierSelector.select(highEndArCoreCapabilities, override = TrackingTier.STANDARD)
        assertEquals(TrackingTier.STANDARD, config.tier)
        assertTrue(config.preferGpuDelegate)
        assertFalse(config.useArCorePose)
        assertEquals(30, config.targetFps)
    }

    @Test
    fun `override null se comporte comme l'absence de parametre (selection automatique)`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = false,
            mediaPerformanceClass = 0,
            cpuCoreCount = 4,
            totalRamMb = 3000,
        )
        assertEquals(
            TrackingTierSelector.select(capabilities),
            TrackingTierSelector.select(capabilities, override = null),
        )
    }

    // --- compatibility() : garde-fou du sélecteur manuel de palier (DiagnosticsScreen) ---

    @Test
    fun `OPTIMAL sans ARCore est INCOMPATIBLE, meme sur un appareil haut de gamme`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = false,
            mediaPerformanceClass = 31,
            cpuCoreCount = 8,
            totalRamMb = 8000,
        )
        assertEquals(
            TierCompatibility.INCOMPATIBLE,
            TrackingTierSelector.compatibility(TrackingTier.OPTIMAL, capabilities),
        )
    }

    @Test
    fun `palier identique a la selection automatique est OK`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = true,
            mediaPerformanceClass = 31,
            cpuCoreCount = 8,
            totalRamMb = 8000,
        )
        // Sélection automatique = OPTIMAL ici -- le forcer explicitement n'est pas un risque.
        assertEquals(
            TierCompatibility.OK,
            TrackingTierSelector.compatibility(TrackingTier.OPTIMAL, capabilities),
        )
    }

    @Test
    fun `palier moins exigeant que l'automatique (degradation volontaire) est OK, jamais un risque`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = true,
            mediaPerformanceClass = 31,
            cpuCoreCount = 8,
            totalRamMb = 8000,
        )
        // Sélection automatique = OPTIMAL ici -- forcer STANDARD ou COMPATIBLE dégrade
        // volontairement, jamais un risque de performance.
        assertEquals(
            TierCompatibility.OK,
            TrackingTierSelector.compatibility(TrackingTier.STANDARD, capabilities),
        )
        assertEquals(
            TierCompatibility.OK,
            TrackingTierSelector.compatibility(TrackingTier.COMPATIBLE, capabilities),
        )
    }

    @Test
    fun `OPTIMAL avec ARCore sur appareil bas de gamme est un risque de performance, pas un blocage`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = true,
            mediaPerformanceClass = 0,
            cpuCoreCount = 4,
            totalRamMb = 3000,
        )
        // ARCore supporté (donc pas INCOMPATIBLE) mais appareil bas de gamme (sélection
        // automatique = COMPATIBLE ici) -- OPTIMAL fonctionnera mais est plus exigeant.
        assertEquals(
            TierCompatibility.PERFORMANCE_RISK,
            TrackingTierSelector.compatibility(TrackingTier.OPTIMAL, capabilities),
        )
    }

    @Test
    fun `STANDARD sur appareil bas de gamme est un risque de performance`() {
        val capabilities = DeviceCapabilities(
            arCoreSupported = false,
            mediaPerformanceClass = 0,
            cpuCoreCount = 4,
            totalRamMb = 3000,
        )
        assertEquals(
            TierCompatibility.PERFORMANCE_RISK,
            TrackingTierSelector.compatibility(TrackingTier.STANDARD, capabilities),
        )
    }

    @Test
    fun `COMPATIBLE n'est jamais ni incompatible ni un risque, quel que soit l'appareil`() {
        val lowEnd = DeviceCapabilities(arCoreSupported = false, mediaPerformanceClass = 0, cpuCoreCount = 4, totalRamMb = 3000)
        val highEnd = DeviceCapabilities(arCoreSupported = true, mediaPerformanceClass = 31, cpuCoreCount = 8, totalRamMb = 8000)
        assertEquals(TierCompatibility.OK, TrackingTierSelector.compatibility(TrackingTier.COMPATIBLE, lowEnd))
        assertEquals(TierCompatibility.OK, TrackingTierSelector.compatibility(TrackingTier.COMPATIBLE, highEnd))
    }
}
