package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
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

    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.connection_title),
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
            Text(
                stringResource(R.string.connection_type_label),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = uiState.connectionType == ConnectionType.VMC,
                    onClick = { onSelectConnectionType(ConnectionType.VMC) },
                    label = { Text(stringResource(R.string.connection_type_vmc)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.connectionType == ConnectionType.IFACIALMOCAP,
                    onClick = { onSelectConnectionType(ConnectionType.IFACIALMOCAP) },
                    label = { Text(stringResource(R.string.connection_type_udp_vbridger)) },
                )
            }

            when (uiState.connectionType) {
                ConnectionType.VMC -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.connection_vmc_section_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = vmcHostInput,
                            onValueChange = { vmcHostInput = it },
                            label = { Text(stringResource(R.string.connection_vmc_ip_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (uiState.vmcEnabled) {
                            Button(onClick = onDisconnectVmc) { Text(stringResource(R.string.action_stop)) }
                        } else {
                            Button(onClick = { onConnectVmc(vmcHostInput) }) { Text(stringResource(R.string.action_send)) }
                        }
                    }
                    if (uiState.vmcEnabled) {
                        Text(
                            stringResource(R.string.connection_vmc_active_status, uiState.vmcTargetLabel),
                            color = Color(0xFF9FE7B0),
                        )
                    }
                }

                ConnectionType.IFACIALMOCAP -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.connection_udp_section_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.connection_udp_ip_hint, uiState.localIpAddress),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (uiState.iFacialMocapListening) {
                        Button(onClick = onStopIFacialMocap) { Text(stringResource(R.string.action_stop)) }
                    } else {
                        Button(onClick = onStartIFacialMocap) { Text(stringResource(R.string.action_listen)) }
                    }
                    if (uiState.iFacialMocapListening) {
                        val status = uiState.iFacialMocapConnectedTo
                            ?.let { stringResource(R.string.connection_udp_connected_to, it) }
                            ?: stringResource(R.string.connection_udp_waiting_handshake)
                        Text(
                            stringResource(R.string.connection_udp_status, status),
                            color = Color(0xFF9FE7B0),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                null -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.connection_choose_type_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
