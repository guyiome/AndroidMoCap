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
 * Reproduit le recadrage centré ("FILL_CENTER", comportement par défaut de `PreviewView") quand le
 * ratio de l'image analysée ([imageWidthPx]/[imageHeightPx]) diffère de celui du canvas
 * ([canvasWidthPx]/[canvasHeightPx]) : l'image est mise à l'échelle pour remplir tout le canvas en
 * conservant son ratio, l'excédent étant rogné de façon centrée sur l'axe qui déborde. Avant ce
 * correctif, un étirement naïf par axe donnait
 * un mesh écrasé et décalé par rapport au visage sur les appareils dont l'écran a un ratio
 * sensiblement différent de celui de l'image caméra (constaté sur ASUS ROG Phone II ; invisible sur
 * tablette où les deux ratios sont proches).
 *
 * Si les dimensions de l'image ne sont pas encore connues ([imageWidthPx] ou [imageHeightPx] <= 0,
 * par exemple avant la toute première frame analysée), repli sur un étirement simple par axe.
 *
 * Fonction pure (aucune dépendance Android/Compose) pour rester testable en JVM.
 */
object LandmarkProjection {

    fun toScreenPoint(
        normalizedX: Float,
        normalizedY: Float,
        imageWidthPx: Float,
        imageHeightPx: Float,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        mirror: Boolean,
    ): Pair<Float, Float> {
        val effectiveX = if (mirror) 1f - normalizedX else normalizedX
        if (imageWidthPx <= 0f || imageHeightPx <= 0f) {
            return (effectiveX * canvasWidthPx) to (normalizedY * canvasHeightPx)
        }
        val scale = maxOf(canvasWidthPx / imageWidthPx, canvasHeightPx / imageHeightPx)
        val offsetX = (imageWidthPx * scale - canvasWidthPx) / 2f
        val offsetY = (imageHeightPx * scale - canvasHeightPx) / 2f
        val screenX = effectiveX * imageWidthPx * scale - offsetX
        val screenY = normalizedY * imageHeightPx * scale - offsetY
        return screenX to screenY
    }
}
