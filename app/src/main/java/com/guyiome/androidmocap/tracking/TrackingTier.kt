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

/**
 * Compatibilité d'un [TrackingTier] avec les capacités réelles d'un appareil, pour le sélecteur
 * manuel de palier ([DiagnosticsScreen][com.guyiome.androidmocap.ui.DiagnosticsScreen]) -- voir
 * [TrackingTierSelector.compatibility]. Distingue une incompatibilité dure (le palier ne peut
 * littéralement pas fonctionner) d'un simple risque de performance dégradée (le palier
 * fonctionnera, mais est plus exigeant que ce que cet appareil justifierait normalement).
 */
enum class TierCompatibility {
    /** Le palier est dans ses conditions normales -- identique ou moins exigeant que l'automatique. */
    OK,

    /** Fonctionnera, mais plus exigeant que ce que la sélection automatique aurait choisi ici -- avertir, pas bloquer. */
    PERFORMANCE_RISK,

    /** Ne peut pas fonctionner sur cet appareil (ex. `OPTIMAL` sans ARCore) -- bloquer la sélection. */
    INCOMPATIBLE,
}

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

    /**
     * Utilisé par le sélecteur manuel de palier (garde-fou demandé explicitement, 6 août 2026) :
     * `OPTIMAL` sans support ARCore réel est la seule incompatibilité dure identifiée -- forcer ce
     * palier ne ferait de toute façon que déclencher le repli silencieux déjà en place
     * (`ArCoreHeadPoseTracker.onUnavailable`), donc autant l'empêcher clairement à la sélection
     * plutôt que de laisser l'utilisateur croire qu'il a activé ARCore. Tout palier plus exigeant
     * que ce que [autoSelectTier] aurait choisi pour ces [capabilities] est un simple risque de
     * performance (fonctionne, mais peut chauffer/ramer) -- jamais un blocage : forcer un palier
     * *moins* exigeant que l'automatique (dégrader volontairement) n'est jamais un risque.
     */
    fun compatibility(tier: TrackingTier, capabilities: DeviceCapabilities): TierCompatibility {
        if (tier == TrackingTier.OPTIMAL && !capabilities.arCoreSupported) return TierCompatibility.INCOMPATIBLE
        val autoTier = autoSelectTier(capabilities)
        return if (tier.demandRank > autoTier.demandRank) TierCompatibility.PERFORMANCE_RISK else TierCompatibility.OK
    }

    /** Rang de charge relative des paliers -- `COMPATIBLE` le plus léger, `OPTIMAL` le plus lourd. */
    private val TrackingTier.demandRank: Int
        get() = when (this) {
            TrackingTier.COMPATIBLE -> 0
            TrackingTier.STANDARD -> 1
            TrackingTier.OPTIMAL -> 2
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
