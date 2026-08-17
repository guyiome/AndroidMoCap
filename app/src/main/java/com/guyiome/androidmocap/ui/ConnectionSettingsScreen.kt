package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.guyiome.androidmocap.network.VTubeStudioConnectionState
import com.guyiome.androidmocap.network.VTubeStudioSender
import com.guyiome.androidmocap.settings.ConnectionType

/**
 * Type de connexion + réglages du type actif -- une des quatre catégories de [SettingsScreen]
 * Contrairement à l'ancien écran unique, seul le sous-bloc du
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
    onConnectVts: (String, Int) -> Unit,
    onDisconnectVts: () -> Unit,
    onForgetVtsToken: () -> Unit,
    onSetUseVBridgerTranslation: (Boolean) -> Unit,
    onOpenVtsFormulas: () -> Unit,
) {
    var vmcHostInput by remember { mutableStateOf(uiState.savedVmcHost.ifBlank { "192.168.1.100" }) }
    var vtsHostInput by remember { mutableStateOf(uiState.savedVtsHost.ifBlank { "192.168.1.100" }) }
    var vtsPortInput by remember { mutableStateOf(VTubeStudioSender.DEFAULT_PORT.toString()) }

    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .blockClicksBehind()
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
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.connectionType == ConnectionType.VTUBE_STUDIO,
                    onClick = { onSelectConnectionType(ConnectionType.VTUBE_STUDIO) },
                    label = { Text(stringResource(R.string.connection_type_vts)) },
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

                ConnectionType.VTUBE_STUDIO -> {
                    val state = uiState.vtsConnectionState
                    val isBusy = state != VTubeStudioConnectionState.Disconnected && state !is VTubeStudioConnectionState.Failed

                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.connection_vts_section_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = vtsHostInput,
                            onValueChange = { vtsHostInput = it },
                            label = { Text(stringResource(R.string.connection_vts_ip_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = vtsPortInput,
                            onValueChange = { vtsPortInput = it },
                            label = { Text(stringResource(R.string.connection_vts_port_label)) },
                            singleLine = true,
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isBusy) {
                        Button(onClick = onDisconnectVts) { Text(stringResource(R.string.action_stop)) }
                    } else {
                        Button(
                            onClick = {
                                val port = vtsPortInput.toIntOrNull() ?: VTubeStudioSender.DEFAULT_PORT
                                onConnectVts(vtsHostInput, port)
                            },
                        ) { Text(stringResource(R.string.action_send)) }
                    }
                    Text(
                        vtsStatusText(state, uiState.vtsTargetLabel),
                        color = when {
                            state == VTubeStudioConnectionState.ParametersRegistered -> Color(0xFF9FE7B0)
                            state is VTubeStudioConnectionState.Failed -> Color(0xFFFF8080)
                            else -> Color.White.copy(alpha = 0.8f)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (state == VTubeStudioConnectionState.ParametersRegistered) {
                        Text(
                            stringResource(R.string.connection_vts_mapping_hint),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    // Pas affiché pendant une connexion en cours (isBusy) -- oublier le jeton en
                    // plein milieu d'une tentative n'a pas de sens, seulement avant/après.
                    if (!isBusy) {
                        TextButton(onClick = onForgetVtsToken, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(R.string.connection_vts_forget_token))
                        }
                    }

                    // Traduction VBridger -> paramètres par défaut VTS -- affiché en
                    // permanence dans ce bloc, pas seulement une fois connecté, pour rester visible/
                    // modifiable avant même de se connecter.
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.connection_vts_translation_label),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = uiState.vtsUseVBridgerTranslation, onCheckedChange = onSetUseVBridgerTranslation)
                    }
                    Text(
                        stringResource(R.string.connection_vts_translation_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenVtsFormulas, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.connection_vts_view_formulas_button))
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

/**
 * Texte de statut affiché pendant la connexion VTube Studio -- plusieurs étapes asynchrones (voir
 * [VTubeStudioConnectionState]), contrairement à VMC qui n'a que connecté/non connecté.
 */
@Composable
private fun vtsStatusText(state: VTubeStudioConnectionState, targetLabel: String): String = when (state) {
    VTubeStudioConnectionState.Disconnected -> stringResource(R.string.connection_vts_status_disconnected)
    VTubeStudioConnectionState.Connecting -> stringResource(R.string.connection_vts_status_connecting)
    VTubeStudioConnectionState.AwaitingUserApproval -> stringResource(R.string.connection_vts_status_awaiting_approval)
    VTubeStudioConnectionState.Authenticating -> stringResource(R.string.connection_vts_status_authenticating)
    VTubeStudioConnectionState.Authenticated -> stringResource(R.string.connection_vts_status_registering_params)
    // Régression du 8 août 2026 : oubliée à l'origine, l'argument %1$s de cette chaîne (l'IP:port
    // cible) restait affiché tel quel plutôt que remplacé -- stringResource() ne détecte pas un
    // placeholder non fourni, il faut explicitement passer chaque argument attendu par la chaîne.
    VTubeStudioConnectionState.ParametersRegistered -> stringResource(R.string.connection_vts_status_connected, targetLabel)
    is VTubeStudioConnectionState.Failed -> stringResource(R.string.error_vts_connection_failed, state.reason)
}
