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
import androidx.compose.material.icons.filled.SystemUpdate
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
 * ou VTube Studio) et se teinte en ambre pendant qu'une connexion progresse, quel que soit le type
 * (résolution DNS pour VMC, en écoute mais VBridger pas encore connecté pour iFacialMocap, cycle
 * d'authentification/création de paramètres pour VTube Studio) -- plutôt que de rester indiscernable
 * d'un simple "non connecté" comme avant le point 40 de la revue technique. Chaque icône pivote sur
 * elle-même selon [iconRotationDegrees] pour rester lisible quel que soit l'angle auquel le
 * téléphone est tenu/posé.
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
    onOpenExperimental: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    // "En cours" par type (revue technique, point 40) :
    // - VMC : résolution DNS/construction du socket (connectVmcTarget) -- généralement bref, mais
    //   affiché pour rester cohérent avec les deux autres types.
    // - iFacialMocap : en écoute mais VBridger pas encore connecté (l'app attend passivement le
    //   handshake -- voir IFacialMocapSender).
    // - VTube Studio : cycle de connexion en plusieurs étapes asynchrones (voir
    //   VTubeStudioConnectionState) -- ParametersRegistered seul compte comme "connecté", tout état
    //   intermédiaire (ni Disconnected ni Failed) compte comme "en cours".
    // Toutes les trois étaient auparavant indiscernables d'un simple "non connecté".
    val vtsState = uiState.vtsConnectionState
    val isVtsConnecting = uiState.connectionType == ConnectionType.VTUBE_STUDIO &&
        vtsState != VTubeStudioConnectionState.Disconnected &&
        vtsState != VTubeStudioConnectionState.ParametersRegistered &&
        vtsState !is VTubeStudioConnectionState.Failed
    val isIFacialMocapWaiting = uiState.iFacialMocapListening && uiState.iFacialMocapConnectedTo == null
    val isConnecting = uiState.vmcConnecting || isIFacialMocapWaiting || isVtsConnecting
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

            // Débit réduit pour cause de chauffe (voir MainViewModel.startThermalPolling) et/ou
            // appareil qui peine sur la détection de langue tirée (point 15, revue technique) --
            // même icône pour les deux causes (souvent corrélées), couplée sur ce petit indicateur
            // déjà existant plutôt que sur un avertissement séparé plein écran (ancien
            // TonguePerformanceWarning, jugé trop imposant par l'utilisateur, retiré). Cliquable
            // uniquement pour la cause langue tirée (amène au réglage pour la désactiver) -- rien
            // à régler côté chauffe, ça se résorbe tout seul. Purement informatif sinon, comme le
            // reste de ce bandeau.
            val tongueStrain = uiState.tongueOutDetectionEnabled && uiState.inferenceRunningHigh
            if (uiState.isThermalThrottling || tongueStrain) {
                Spacer(Modifier.width(18.dp))
                IconButton(onClick = onOpenExperimental, enabled = tongueStrain, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = if (tongueStrain) {
                            stringResource(R.string.cd_tongue_performance_warning)
                        } else {
                            stringResource(R.string.cd_thermal_throttling)
                        },
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.rotate(-iconRotationDegrees),
                    )
                }
            }
            Spacer(Modifier.width(18.dp))

            // Connexion -- appuyer connecte/déconnecte selon le type choisi dans les réglages.
            // Teinte ambre pendant qu'une connexion progresse, quel que soit le type (même couleur
            // que l'avertissement thermique ci-dessus, cohérence visuelle). Un appui dans cet état
            // annule la tentative pour iFacialMocap/VTube Studio (MainViewModel.toggleActiveConnection
            // s'en charge déjà) ; pour VMC, la fenêtre "en cours" est si brève (pas de vrai lookup
            // réseau pour une IP) qu'un appui pendant celle-ci ne fait dans les faits que relancer
            // une connexion déjà sur le point d'aboutir -- sans conséquence en pratique.
            IconButton(onClick = onToggleConnection, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.Link else Icons.Filled.LinkOff,
                    contentDescription = when {
                        isConnecting -> stringResource(R.string.cd_connecting_tap_cancel)
                        isConnected -> stringResource(R.string.cd_connected_tap_disconnect)
                        else -> stringResource(R.string.cd_disconnected_tap_connect)
                    },
                    tint = when {
                        isConnecting -> Color(0xFFFFB74D)
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
            // Mise à jour disponible (point 14) -- purement informatif jusqu'au tap, qui ouvre la
            // page de la Release GitHub dans le navigateur (pas d'install silencieuse possible hors
            // store, voir UpdateChecker/MainViewModel.checkForUpdate). Reste affichée tant que l'app
            // n'est pas mise à jour, décision explicite (pas d'état "ignorer cette version").
            if (uiState.updateAvailableUrl != null) {
                Spacer(Modifier.width(18.dp))
                IconButton(onClick = onOpenUpdate, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = stringResource(R.string.cd_update_available),
                        tint = Color(0xFF9FE7B0),
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
