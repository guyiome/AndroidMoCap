package com.guyiome.androidmocap.capabilities

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.google.ar.core.ArCoreApk

/**
 * Photo instantanée des capacités de l'appareil, utilisée par [com.guyiome.androidmocap.tracking.TrackingTierSelector]
 * pour choisir automatiquement le meilleur pipeline de capture possible sans jamais bloquer l'utilisateur
 * sur un appareil "non premium".
 */
data class DeviceCapabilities(
    /** ARCore est installé et supporté matériellement (indépendamment du support spécifique d'Augmented Faces). */
    val arCoreSupported: Boolean,
    /** Classe de performance officielle Android (API 31+, sinon 0 = inconnue -> on retombe sur l'heuristique RAM/cœurs). */
    val mediaPerformanceClass: Int,
    val cpuCoreCount: Int,
    val totalRamMb: Long,
) {
    /** Heuristique simple pour les appareils sans Media Performance Class déclarée (API < 31 ou non renseignée). */
    val looksHighEnd: Boolean
        get() = mediaPerformanceClass >= Build.VERSION_CODES.S ||
            (mediaPerformanceClass == 0 && cpuCoreCount >= 8 && totalRamMb >= 6000)
}

object DeviceCapabilityDetector {

    fun detect(context: Context): DeviceCapabilities {
        val arCoreSupported = try {
            ArCoreApk.getInstance().checkAvailability(context).isSupported
        } catch (e: Exception) {
            // ARCore absent du device (Play Services for AR non installable) : on continue sans lui.
            false
        }

        val performanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.VERSION.MEDIA_PERFORMANCE_CLASS
        } else {
            0
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024))

        return DeviceCapabilities(
            arCoreSupported = arCoreSupported,
            mediaPerformanceClass = performanceClass,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            totalRamMb = totalRamMb,
        )
    }

    /**
     * À appeler périodiquement pendant la capture (ex. toutes les quelques secondes) pour détecter
     * un throttling thermique et déclencher une rétrogradation dynamique de palier.
     */
    fun isThermalThrottling(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    }
}
