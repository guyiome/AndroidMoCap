package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R

/**
 * Bandeau d'icônes minimal affiché en permanence sur l'écran caméra -- les 4 indicateurs/boutons
 * (visage détecté, connexion, réglages, mise à zéro) regroupés en un seul bloc en bas, sans
 * texte. Le bouton de connexion se base sur le type choisi dans [SettingsScreen] (VMC ou
 * iFacialMocap). Chaque icône pivote sur elle-même selon [iconRotationDegrees] pour rester
 * lisible quel que soit l'angle auquel le téléphone est tenu/posé.
 *
 * [faceDetected] est reçu séparément de [uiState] (issu de [MainViewModel.trackingFrame], mis à
 * jour à chaque frame) plutôt qu'inclus dedans -- ça garde [uiState] stable pour Compose, qui peut
 * alors sauter la recomposition de ce composable tant que ni l'icône de détection ni le reste de
 * l'état froid n'ont changé, au lieu de tout redessiner à 20-60 Hz.
 */
@Composable
fun MainHud(
    uiState: MainUiState,
    faceDetected: Boolean,
    iconRotationDegrees: Float,
    onCalibrate: () -> Unit,
    onToggleConnection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isConnected = uiState.vmcEnabled || uiState.iFacialMocapConnectedTo != null
    val countdown = uiState.calibrationCountdownSeconds

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Visage détecté -- purement informatif.
            Icon(
                imageVector = Icons.Filled.Face,
                contentDescription = stringResource(R.string.cd_face_detected),
                tint = if (faceDetected) Color(0xFF9FE7B0) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.rotate(-iconRotationDegrees),
            )
            Spacer(Modifier.width(18.dp))

            // Connexion -- appuyer connecte/déconnecte selon le type choisi dans les réglages.
            IconButton(onClick = onToggleConnection, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.Link else Icons.Filled.LinkOff,
                    contentDescription = if (isConnected) {
                        stringResource(R.string.cd_connected_tap_disconnect)
                    } else {
                        stringResource(R.string.cd_disconnected_tap_connect)
                    },
                    tint = if (isConnected) Color(0xFF9FE7B0) else Color.White,
                    modifier = Modifier.rotate(-iconRotationDegrees),
                )
            }
            Spacer(Modifier.width(18.dp))

            // Mise à zéro, avec l'anneau de compte à rebours autour.
            Box(contentAlignment = Alignment.Center) {
                if (countdown != null) {
                    CircularProgressIndicator(
                        progress = { countdown / 5f },
                        modifier = Modifier.size(44.dp),
                        color = Color(0xFF7CE0FF),
                    )
                }
                IconButton(
                    onClick = onCalibrate,
                    enabled = countdown == null,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = stringResource(R.string.cd_calibrate),
                        tint = Color.White,
                        modifier = Modifier.rotate(-iconRotationDegrees),
                    )
                }
            }
            Spacer(Modifier.width(18.dp))

            // Réglages -- placé en dernier dans le bandeau.
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = Color.White,
                    modifier = Modifier.rotate(-iconRotationDegrees),
                )
            }
        }
    }
}
