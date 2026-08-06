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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Réglages d'affichage et de confort d'usage -- une des quatre catégories de [SettingsScreen]
 * (voir rapport technique, point 21) : sélection des blendshapes affichés (+ sa persistance,
 * point 18), overlay du mesh, mode économie d'énergie, seuil de batterie faible.
 */
@Composable
fun DisplaySettingsScreen(
    uiState: MainUiState,
    onClose: () -> Unit,
    onOpenBlendshapeSelection: () -> Unit,
    onSetPersistBlendshapeSelection: (Boolean) -> Unit,
    onSetLowBatteryThreshold: (Int) -> Unit,
    onSetPowerSaveMode: (Boolean) -> Unit,
    onSetPowerSaveDelay: (Int) -> Unit,
    onSetFaceMeshOverlay: (Boolean) -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Affichage & confort",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
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
                        "${uiState.selectedBlendshapeNames.size} sélectionnée(s)" +
                            if (uiState.persistBlendshapeSelectionEnabled) " -- mémorisée" else " -- non conservé au redémarrage",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Mémoriser cette sélection au prochain lancement",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = uiState.persistBlendshapeSelectionEnabled, onCheckedChange = onSetPersistBlendshapeSelection)
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
        }
    }
}
