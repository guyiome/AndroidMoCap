package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Type de connexion + réglages du type actif -- une des quatre catégories de [SettingsScreen]
 * (voir rapport technique, point 21). Contrairement à l'ancien écran unique, seul le sous-bloc du
 * type choisi est affiché (affichage conditionnel plutôt que VMC et iFacialMocap systématiquement
 * visibles tous les deux) -- décision actée en discussion, moins de contenu à parcourir pour
 * arriver à ce qui est effectivement utilisé.
 */
@Composable
fun ConnectionSettingsScreen(
    uiState: MainUiState,
    onClose: () -> Unit,
    onSelectConnectionType: (ConnectionType) -> Unit,
    onConnectVmc: (String) -> Unit,
    onDisconnectVmc: () -> Unit,
    onStartIFacialMocap: () -> Unit,
    onStopIFacialMocap: () -> Unit,
) {
    var vmcHostInput by remember { mutableStateOf(uiState.savedVmcHost.ifBlank { "192.168.1.100" }) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Connexion",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
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

            when (uiState.connectionType) {
                ConnectionType.VMC -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "VMC (VTube Studio / Blender / Unity)",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                }

                ConnectionType.IFACIALMOCAP -> {
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
                }

                null -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Choisis un type de connexion ci-dessus pour voir ses réglages.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
