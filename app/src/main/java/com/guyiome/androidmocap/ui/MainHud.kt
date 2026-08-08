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
import androidx.compose.material.icons.filled.WarningAmber
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
import com.guyiome.androidmocap.network.VTubeStudioConnectionState
import com.guyiome.androidmocap.settings.ConnectionType

/**
 * Bandeau d'icônes minimal affiché en permanence sur l'écran caméra -- les 4 indicateurs/boutons
 * (visage détecté, connexion, réglages, mise à zéro) regroupés en un seul bloc en bas, sans
 * texte. Le bouton de connexion se base sur le type choisi dans [SettingsScreen] (VMC, iFacialMocap
 * ou VTube Studio -- ce dernier a un cycle de connexion en plusieurs étapes asynchrones, teinté en
 * ambre pendant qu'il progresse plutôt que de rester indiscernable d'un simple "non connecté",
 * revue technique point 40). Chaque icône pivote sur elle-même selon [iconRotationDegrees] pour
 * rester lisible quel que soit l'angle auquel le téléphone est tenu/posé.
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
    // VTube Studio (contrairement à VMC/iFacialMocap) a un cycle de connexion en plusieurs étapes
    // asynchrones (voir VTubeStudioConnectionState, revue technique point 39/40) -- ParametersRegistered
    // seul compte comme "connecté" ; tout état intermédiaire (ni Disconnected ni Failed) compte comme
    // "en cours", affiché avec une teinte distincte plutôt que de rester indiscernable d'un simple
    // "non connecté" comme avant ce point.
    val vtsState = uiState.vtsConnectionState
    val isVtsConnecting = uiState.connectionType == ConnectionType.VTUBE_STUDIO &&
        vtsState != VTubeStudioConnectionState.Disconnected &&
        vtsState != VTubeStudioConnectionState.ParametersRegistered &&
        vtsState !is VTubeStudioConnectionState.Failed
    val isConnected = uiState.vmcEnabled || uiState.iFacialMocapConnectedTo != null ||
        vtsState == VTubeStudioConnectionState.ParametersRegistered
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

            // Débit réduit pour cause de chauffe (voir MainViewModel.startThermalPolling) --
            // purement informatif, n'apparaît que le temps que la chauffe dure (remonte tout seul
            // ensuite). Même couleur/icône que l'avertissement "risque de performance" du
            // sélecteur de palier (DiagnosticsScreen) -- cohérence visuelle, même famille de signal.
            if (uiState.isThermalThrottling) {
                Spacer(Modifier.width(18.dp))
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = stringResource(R.string.cd_thermal_throttling),
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.rotate(-iconRotationDegrees),
                )
            }
            Spacer(Modifier.width(18.dp))

            // Connexion -- appuyer connecte/déconnecte selon le type choisi dans les réglages.
            // Teinte ambre pendant une connexion VTube Studio en cours (même couleur que
            // l'avertissement thermique ci-dessus, cohérence visuelle) -- un appui pendant cet état
            // annule la tentative (MainViewModel.toggleActiveConnection s'en charge déjà).
            IconButton(onClick = onToggleConnection, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.Link else Icons.Filled.LinkOff,
                    contentDescription = when {
                        isVtsConnecting -> stringResource(R.string.cd_connecting_tap_cancel)
                        isConnected -> stringResource(R.string.cd_connected_tap_disconnect)
                        else -> stringResource(R.string.cd_disconnected_tap_connect)
                    },
                    tint = when {
                        isVtsConnecting -> Color(0xFFFFB74D)
                        isConnected -> Color(0xFF9FE7B0)
                        else -> Color.White
                    },
                    modifier = Modifier.rotate(-iconRotationDegrees),
                )
            }
            Spacer(Modifier.width(18.dp))

            // Mise à zéro, avec l'anneau de compte à rebours autour. Teinte rouge si une anomalie
            // de calibrage a été détectée (voir tracking/CalibrationAnomaly.kt, revue technique
            // point 19) -- suspendue pendant le compte à rebours (recalibrage imminent, rouge +
            // anneau simultanés serait confus) ; se résout uniquement par un nouveau calibrage.
            Box(contentAlignment = Alignment.Center) {
                if (countdown != null) {
                    CircularProgressIndicator(
                        progress = { countdown / 5f },
                        modifier = Modifier.size(44.dp),
                        color = Color(0xFF7CE0FF),
                    )
                }
                val calibrationAnomalyActive = uiState.calibrationAnomalyFlagged && countdown == null
                IconButton(
                    onClick = onCalibrate,
                    enabled = countdown == null,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = if (calibrationAnomalyActive) {
                            stringResource(R.string.cd_calibrate_anomaly_detected)
                        } else {
                            stringResource(R.string.cd_calibrate)
                        },
                        tint = if (calibrationAnomalyActive) Color(0xFFFF8080) else Color.White,
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
