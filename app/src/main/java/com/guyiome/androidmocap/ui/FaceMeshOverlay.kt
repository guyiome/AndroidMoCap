package com.guyiome.androidmocap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Superpose les points du mesh facial MediaPipe (478 points -- surface + iris) sur l'aperçu
 * caméra, pour vérifier visuellement la qualité du tracking directement dans l'app sans dépendre
 * d'un récepteur externe (VTube Studio, Blender...). Option activable/désactivable depuis les
 * réglages ([MainUiState.faceMeshOverlayEnabled]) -- indépendante du futur aperçu 3D d'avatar
 * (piste séparée, plus lourde, pas encore implémentée).
 *
 * La projection écran ([LandmarkProjection.toScreenPoint]) reproduit le recadrage centré
 * ("FILL_CENTER") appliqué par `PreviewView` à partir des dimensions réelles de l'image analysée
 * ([imageWidthPx]/[imageHeightPx]) -- nécessaire pour un alignement correct dès que le ratio de
 * l'image caméra diffère de celui de l'écran (corrigé suite à un décalage constaté sur
 * ASUS ROG Phone II).
 */
@Composable
fun FaceMeshOverlay(
    landmarks: List<Pair<Float, Float>>,
    imageWidthPx: Int,
    imageHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    if (landmarks.isEmpty()) return

    Canvas(modifier = modifier) {
        val pointRadius = 2.5f
        for ((normalizedX, normalizedY) in landmarks) {
            val (screenX, screenY) = LandmarkProjection.toScreenPoint(
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                imageWidthPx = imageWidthPx.toFloat(),
                imageHeightPx = imageHeightPx.toFloat(),
                canvasWidthPx = size.width,
                canvasHeightPx = size.height,
                // Caméra frontale : l'aperçu (PreviewView) est mirroré par CameraX, l'image
                // envoyée au modèle ne l'est pas (voir CameraController) -- il faut donc mirrorer
                // ici pour que les points se superposent au visage affiché à l'écran.
                mirror = true,
            )
            drawCircle(
                color = Color(0xFF7CE0FF),
                radius = pointRadius,
                center = Offset(screenX, screenY),
            )
        }
    }
}
