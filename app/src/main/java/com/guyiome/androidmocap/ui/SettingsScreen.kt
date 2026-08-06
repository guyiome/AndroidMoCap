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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.settings.ConnectionType

/**
 * Panneau de diagnostics et de réglages détaillés, affiché par-dessus l'écran caméra quand on
 * appuie sur l'icône réglages de [MainHud] -- tout ce qui n'a pas sa place dans le bandeau
 * minimal : palier de tracking, latence, connexions VMC/iFacialMocap, seuil batterie, accès à la
 * sélection des blendshapes (voir [BlendshapeSelectionScreen]).
 */
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    // Reçus séparément de [uiState] (issus de [MainViewModel.trackingFrame], mis à jour à chaque
    // frame) -- ce panneau n'est affiché qu'à la demande, mais on garde la même séparation
    // état froid/chaud que partout ailleurs plutôt que de la casser ici.
    faceDetected: Boolean,
    inferenceTimeMs: Long,
    onClose: () -> Unit,
    onSelectConnectionType: (ConnectionType) -> Unit,
    onConnectVmc: (String) -> Unit,
    onDisconnectVmc: () -> Unit,
    onStartIFacialMocap: () -> Unit,
    onStopIFacialMocap: () -> Unit,
    onSetLowBatteryThreshold: (Int) -> Unit,
    onOpenBlendshapeSelection: () -> Unit,
    onSetPowerSaveMode: (Boolean) -> Unit,
    onSetPowerSaveDelay: (Int) -> Unit,
    onSetFaceMeshOverlay: (Boolean) -> Unit,
) {
    var vmcHostInput by remember { mutableStateOf(uiState.savedVmcHost.ifBlank { "192.168.1.100" }) }

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
                    "Réglages & diagnostics",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Palier : ${uiState.tier?.name ?: "…"}", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Délégué : ${if (uiState.activeDelegateIsGpu) "GPU" else "CPU"}", color = Color.White)
            Text("Visage détecté : ${if (faceDetected) "oui" else "non"}", color = Color.White)
            Text("Latence inférence : $inferenceTimeMs ms", color = Color.White)
            Text(
                if (uiState.isCalibrated) "Pose neutre : calibrée" else "Pose neutre : non calibrée",
                color = if (uiState.isCalibrated) Color(0xFF9FE7B0) else Color.White,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBlendshapeSelection() }
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blendshapes affichées", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${uiState.selectedBlendshapeNames.size} sélectionnée(s) -- non conservé au redémarrage",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Seuil d'alerte batterie faible : ${uiState.lowBatteryThresholdPercent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = uiState.lowBatteryThresholdPercent.toFloat(),
                onValueChange = { onSetLowBatteryThreshold(it.toInt()) },
                valueRange = 5f..50f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mode économie d'énergie", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Après le délai ci-dessous sans toucher l'écran : écran assombri, aperçu caméra " +
                            "masqué (le tracking et l'envoi continuent normalement). Un simple toucher " +
                            "de l'écran en ressort.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = uiState.powerSaveModeEnabled, onCheckedChange = onSetPowerSaveMode)
            }
            if (uiState.powerSaveModeEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Délai d'inactivité : ${uiState.powerSaveDelaySeconds} s",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = uiState.powerSaveDelaySeconds.toFloat(),
                    onValueChange = { onSetPowerSaveDelay(it.toInt()) },
                    valueRange = 5f..120f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Overlay du mesh de tracking", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Superpose les points détectés par MediaPipe sur l'aperçu caméra -- pratique " +
                            "pour vérifier la qualité du tracking sans dépendre de VTube Studio/Blender.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = uiState.faceMeshOverlayEnabled, onCheckedChange = onSetFaceMeshOverlay)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Type de connexion (bouton de l'écran principal)",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = uiState.connectionType == ConnectionType.VMC,
                    onClick = { onSelectConnectionType(ConnectionType.VMC) },
                    label = { Text("VMC") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.connectionType == ConnectionType.IFACIALMOCAP,
                    onClick = { onSelectConnectionType(ConnectionType.IFACIALMOCAP) },
                    label = { Text("iFacialMocap") },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("VMC (VTube Studio / Blender / Unity)", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = vmcHostInput,
                    onValueChange = { vmcHostInput = it },
                    label = { Text("IP du PC") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (uiState.vmcEnabled) {
                    Button(onClick = onDisconnectVmc) { Text("Stop") }
                } else {
                    Button(onClick = { onConnectVmc(vmcHostInput) }) { Text("Envoyer") }
                }
            }
            if (uiState.vmcEnabled) {
                Text("Envoi VMC actif vers ${uiState.vmcTargetLabel}", color = Color(0xFF9FE7B0))
            }

            Spacer(Modifier.height(24.dp))
            Text("iFacialMocap / VBridger", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                "IP du téléphone à saisir côté VBridger : ${uiState.localIpAddress}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (uiState.iFacialMocapListening) {
                Button(onClick = onStopIFacialMocap) { Text("Stop") }
            } else {
                Button(onClick = onStartIFacialMocap) { Text("Écouter") }
            }
            if (uiState.iFacialMocapListening) {
                val status = uiState.iFacialMocapConnectedTo
                    ?.let { "connecté à $it" }
                    ?: "en attente du handshake VBridger…"
                Text("iFacialMocap : $status", color = Color(0xFF9FE7B0), modifier = Modifier.padding(top = 4.dp))
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color(0xFFFF8080))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
