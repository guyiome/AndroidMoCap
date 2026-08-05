package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Recouvre l'aperçu caméra d'un fond noir, utilisé quand le mode économie d'énergie est actif --
 * la caméra continue de tourner (analyse MediaPipe intacte, uniquement l'usage "Preview" est
 * coupé côté [com.guyiome.androidmocap.camera.CameraController]), seul l'affichage change pour
 * limiter le rendu écran et la chauffe associée. Pas d'indicateur d'état ici : l'icône "Visage
 * détecté" du bandeau ([MainHud]) reste visible par-dessus et remplit déjà ce rôle.
 */
@Composable
fun PowerSaveOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    )
}
