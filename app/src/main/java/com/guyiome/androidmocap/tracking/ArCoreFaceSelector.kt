package com.guyiome.androidmocap.tracking

/**
 * Représentation minimale d'un visage suivi par ARCore Augmented Faces (voir
 * [ArCoreHeadPoseTracker]), volontairement découplée du type Android
 * `com.google.ar.core.AugmentedFace` pour rester testable en JVM pur -- même principe que
 * [BlendshapeScore] côté MediaPipe.
 */
data class ArCoreFaceCandidate(
    val isTracking: Boolean,
    /** Quaternion (x, y, z, w) de `Pose#getRotationQuaternion()`, voir [RotationMath.rotation3x3FromQuaternion]. */
    val rotationQuaternion: FloatArray,
) {
    // FloatArray n'a pas d'equals() structurel par défaut -- surchargé pour que les data class
    // dérivées (utilisées telles quelles dans les tests) comparent le contenu, pas la référence.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArCoreFaceCandidate) return false
        return isTracking == other.isTracking && rotationQuaternion.contentEquals(other.rotationQuaternion)
    }

    override fun hashCode(): Int = 31 * isTracking.hashCode() + rotationQuaternion.contentHashCode()
}

object ArCoreFaceSelector {
    /**
     * ARCore Augmented Faces peut suivre plusieurs visages simultanément (jusqu'à 4) -- l'app n'en
     * utilise qu'un seul (l'utilisateur face à la caméra). Premier visage effectivement en cours de
     * suivi (`TrackingState.TRACKING` côté ARCore) ; les visages détectés mais pas/plus suivis
     * (perdus, pas encore stabilisés) sont ignorés plutôt que de faire clignoter la pose envoyée.
     */
    fun pickPrimary(candidates: List<ArCoreFaceCandidate>): ArCoreFaceCandidate? =
        candidates.firstOrNull { it.isTracking }
}
