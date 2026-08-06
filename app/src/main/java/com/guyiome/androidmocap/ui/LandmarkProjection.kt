package com.guyiome.androidmocap.ui

/**
 * Projette un point du mesh facial MediaPipe (coordonnées normalisées [0,1], dans l'espace de
 * l'image droite non-mirroir envoyée au modèle -- voir
 * [com.guyiome.androidmocap.camera.CameraController], qui ne mirroir jamais l'image analysée)
 * vers l'espace écran (pixels) de l'overlay dessiné par [FaceMeshOverlay].
 *
 * L'aperçu caméra affiché (`PreviewView`) est lui mirroré automatiquement par CameraX pour la
 * caméra frontale (comportement standard, cohérent avec un miroir physique) -- [mirror] doit donc
 * être `true` pour que les points se superposent correctement au visage affiché à l'écran.
 *
 * Fonction pure (aucune dépendance Android/Compose) pour rester testable en JVM. Approximation
 * volontaire pour cette première version : suppose que le calque occupe tout l'écran au même
 * rapport largeur/hauteur que l'image analysée (pas de compensation du recadrage éventuel de
 * `PreviewView` si son ratio diffère) -- à affiner après un premier essai sur device si besoin.
 */
object LandmarkProjection {

    fun toScreenPoint(
        normalizedX: Float,
        normalizedY: Float,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        mirror: Boolean,
    ): Pair<Float, Float> {
        val effectiveX = if (mirror) 1f - normalizedX else normalizedX
        return (effectiveX * canvasWidthPx) to (normalizedY * canvasHeightPx)
    }
}
