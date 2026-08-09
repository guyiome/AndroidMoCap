package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.network.VTubeStudioConnectionState
import com.guyiome.androidmocap.settings.ConnectionType

/**
 * Menu des réglages, affiché par-dessus l'écran caméra quand on appuie sur l'icône réglages de
 * [MainHud] -- regroupe l'ensemble en quatre catégories (voir rapport technique, point 21) plutôt
 * qu'un unique long panneau qui défile : [DiagnosticsScreen] (lecture seule),
 * [ConnectionSettingsScreen], [DisplaySettingsScreen] (affichage & confort) et
 * [ExperimentalFeaturesScreen]. Même patron de navigation que celui déjà utilisé pour
 * [BlendshapeSelectionScreen] : une ligne cliquable avec chevron ouvre l'écran dédié.
 */
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    // Reçu séparément de [uiState] (issu de [MainViewModel.trackingFrame], mis à jour à chaque
    // frame) -- uniquement pour le sous-titre de la ligne "Diagnostics" ci-dessous ; le détail
    // complet (latence incluse) reste dans DiagnosticsScreen lui-même.
    faceDetected: Boolean,
    onClose: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenLogging: () -> Unit,
) {
    // Bouton système/geste retour équivalent au bouton de fermeture affiché -- voir rapport
    // technique, point 22 (ergonomie navigation).
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsMenuRow(
                title = stringResource(R.string.diagnostics_title),
                subtitle = stringResource(
                    R.string.settings_diagnostics_subtitle,
                    uiState.tier?.name ?: stringResource(R.string.placeholder_unknown),
                    if (faceDetected) stringResource(R.string.state_face_detected) else stringResource(R.string.state_face_not_detected),
                ),
                onClick = onOpenDiagnostics,
            )
            SettingsMenuRow(
                title = stringResource(R.string.connection_title),
                subtitle = connectionSubtitle(uiState),
                onClick = onOpenConnection,
            )
            SettingsMenuRow(
                title = stringResource(R.string.display_settings_title),
                subtitle = stringResource(R.string.settings_display_subtitle),
                onClick = onOpenDisplay,
            )
            SettingsMenuRow(
                title = stringResource(R.string.experimental_features_title),
                subtitle = stringResource(R.string.settings_experimental_subtitle),
                onClick = onOpenExperimental,
            )
            SettingsMenuRow(
                title = stringResource(R.string.logging_settings_title),
                subtitle = stringResource(R.string.settings_logging_subtitle),
                onClick = onOpenLogging,
            )

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color(0xFFFF8080))
            }
        }
    }
}

@Composable
private fun connectionSubtitle(uiState: MainUiState): String = when (uiState.connectionType) {
    ConnectionType.VMC -> if (uiState.vmcEnabled) {
        stringResource(R.string.settings_vmc_connected_subtitle, uiState.vmcTargetLabel)
    } else {
        stringResource(R.string.settings_vmc_not_connected_subtitle)
    }
    ConnectionType.IFACIALMOCAP -> if (uiState.iFacialMocapListening) {
        stringResource(R.string.settings_udp_listening_subtitle)
    } else {
        stringResource(R.string.settings_udp_not_listening_subtitle)
    }
    ConnectionType.VTUBE_STUDIO -> if (uiState.vtsConnectionState == VTubeStudioConnectionState.ParametersRegistered) {
        stringResource(R.string.settings_vts_connected_subtitle, uiState.vtsTargetLabel)
    } else {
        stringResource(R.string.settings_vts_not_connected_subtitle)
    }
    null -> stringResource(R.string.settings_no_connection_type_subtitle)
}

@Composable
private fun SettingsMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
    }
}
