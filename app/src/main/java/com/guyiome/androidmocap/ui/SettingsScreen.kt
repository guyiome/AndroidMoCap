package com.guyiome.androidmocap.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
) {
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
                    "Réglages",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsMenuRow(
                title = "Diagnostics",
                subtitle = "Palier ${uiState.tier?.name ?: "…"} · visage ${if (faceDetected) "détecté" else "non détecté"}",
                onClick = onOpenDiagnostics,
            )
            SettingsMenuRow(
                title = "Connexion",
                subtitle = connectionSubtitle(uiState),
                onClick = onOpenConnection,
            )
            SettingsMenuRow(
                title = "Affichage & confort",
                subtitle = "Blendshapes affichés, overlay du mesh, mode éco, seuil batterie",
                onClick = onOpenDisplay,
            )
            SettingsMenuRow(
                title = "Fonctionnalités expérimentales",
                subtitle = "Aucune pour l'instant",
                onClick = onOpenExperimental,
            )

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color(0xFFFF8080))
            }
        }
    }
}

private fun connectionSubtitle(uiState: MainUiState): String = when (uiState.connectionType) {
    ConnectionType.VMC -> if (uiState.vmcEnabled) "VMC connecté à ${uiState.vmcTargetLabel}" else "VMC choisi, pas encore connecté"
    ConnectionType.IFACIALMOCAP -> if (uiState.iFacialMocapListening) "iFacialMocap en écoute" else "iFacialMocap choisi, pas encore en écoute"
    null -> "Aucun type choisi"
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
