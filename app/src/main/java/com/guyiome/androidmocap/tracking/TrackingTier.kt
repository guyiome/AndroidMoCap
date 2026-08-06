package com.guyiome.androidmocap.tracking

import com.guyiome.androidmocap.capabilities.DeviceCapabilities

/** Palier de pipeline choisi automatiquement selon les capacités de l'appareil (voir [TrackingTierSelector]). */
enum class TrackingTier {
    /** ARCore supporté + appareil haut de gamme : pose de tête ARCore + MediaPipe "full" GPU ~60fps. */
    OPTIMAL,

    /** Pas d'ARCore certifié mais appareil correct : MediaPipe "full" GPU pour pose ET blendshapes. */
    STANDARD,

    /** Appareil d'entrée de gamme / pas de GPU délégué : MediaPipe "lite" CPU, résolution réduite. */
    COMPATIBLE,
}

data class TierConfig(
    val tier: TrackingTier,
    /** true = on tente le délégué GPU (avec repli automatique CPU si indisponible, voir FaceLandmarkerHelper). */
    val preferGpuDelegate: Boolean,
    val targetFps: Int,
    /** true = pose de tête fusionnée avec ARCore (voir `tracking/ArCoreHeadPoseTracker.kt`). */
    val useArCorePose: Boolean,
)

object TrackingTierSelector {

    /**
     * [override], s'il est non nul, court-circuite complètement la sélection automatique --
     * utilisé pour forcer un palier depuis les réglages (voir `DiagnosticsScreen`,
     * `AppSettingsStore.tierOverride`), pensé comme outil de diagnostic/test (ex. forcer
     * `STANDARD` sur un appareil qui qualifierait normalement pour `OPTIMAL`, pour tester le
     * chemin CameraX sans dépendre d'un second appareil), pas une fonctionnalité utilisateur
     * normale. Le `TierConfig` retourné pour un palier donné est le même qu'il soit choisi
     * automatiquement ou forcé -- seule la décision *quel* palier change.
     */
    fun select(capabilities: DeviceCapabilities, override: TrackingTier? = null): TierConfig {
        val tier = override ?: autoSelectTier(capabilities)
        return configFor(tier)
    }

    internal fun autoSelectTier(capabilities: DeviceCapabilities): TrackingTier = when {
        capabilities.arCoreSupported && capabilities.looksHighEnd -> TrackingTier.OPTIMAL
        capabilities.looksHighEnd -> TrackingTier.STANDARD
        else -> TrackingTier.COMPATIBLE
    }

    private fun configFor(tier: TrackingTier): TierConfig = when (tier) {
        TrackingTier.OPTIMAL -> TierConfig(
            tier = tier,
            preferGpuDelegate = true,
            targetFps = 60,
            useArCorePose = true,
        )

        TrackingTier.STANDARD -> TierConfig(
            tier = tier,
            preferGpuDelegate = true,
            targetFps = 30,
            useArCorePose = false,
        )

        TrackingTier.COMPATIBLE -> TierConfig(
            tier = tier,
            preferGpuDelegate = false,
            targetFps = 20,
            useArCorePose = false,
        )
    }
}
