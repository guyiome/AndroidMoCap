package com.guyiome.androidmocap.tracking

import com.guyiome.androidmocap.capabilities.DeviceCapabilities

/**
 * Palier de pipeline choisi automatiquement selon les capacités de l'appareil.
 * Voir l'étude "AndroidMoCap_etude_options.md" section "Pipeline adaptatif par paliers".
 */
enum class TrackingTier {
    /** ARCore supporté + appareil haut de gamme : pose de tête ARCore (phase 2) + MediaPipe "full" GPU ~60fps. */
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
    /** true = pose de tête fusionnée avec ARCore (branchement prévu en phase 2, pas encore actif dans ce PoC). */
    val useArCorePose: Boolean,
)

object TrackingTierSelector {

    fun select(capabilities: DeviceCapabilities): TierConfig {
        val tier = when {
            capabilities.arCoreSupported && capabilities.looksHighEnd -> TrackingTier.OPTIMAL
            capabilities.looksHighEnd -> TrackingTier.STANDARD
            else -> TrackingTier.COMPATIBLE
        }

        return when (tier) {
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
}
