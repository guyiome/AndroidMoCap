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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.settings.AppLanguage

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
    onSetKeepMeshOverlayInPowerSave: (Boolean) -> Unit,
    onSetMirrorMode: (Boolean) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
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
                    stringResource(R.string.display_settings_title),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBlendshapeSelection() }
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.blendshape_selection_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.blendshape_selected_count, uiState.selectedBlendshapeNames.size) +
                            if (uiState.persistBlendshapeSelectionEnabled) {
                                stringResource(R.string.blendshape_selection_persisted_suffix)
                            } else {
                                stringResource(R.string.blendshape_selection_not_persisted_suffix)
                            },
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.display_persist_selection_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = uiState.persistBlendshapeSelectionEnabled, onCheckedChange = onSetPersistBlendshapeSelection)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.display_mesh_overlay_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.display_mesh_overlay_description),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = uiState.faceMeshOverlayEnabled, onCheckedChange = onSetFaceMeshOverlay)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.display_keep_mesh_overlay_in_power_save_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = uiState.keepMeshOverlayInPowerSave, onCheckedChange = onSetKeepMeshOverlayInPowerSave)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.display_mirror_mode_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.display_mirror_mode_description),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = uiState.mirrorModeEnabled, onCheckedChange = onSetMirrorMode)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.display_power_save_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.display_power_save_description),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = uiState.powerSaveModeEnabled, onCheckedChange = onSetPowerSaveMode)
            }
            if (uiState.powerSaveModeEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.display_power_save_delay, uiState.powerSaveDelaySeconds),
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
                stringResource(R.string.display_low_battery_threshold, uiState.lowBatteryThresholdPercent),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = uiState.lowBatteryThresholdPercent.toFloat(),
                onValueChange = { onSetLowBatteryThreshold(it.toInt()) },
                valueRange = 5f..50f,
                modifier = Modifier.fillMaxWidth(),
            )

            // Sélecteur de langue en-app (point 30) : fonctionne sur toutes les versions d'Android,
            // contrairement au sélecteur système natif (réglages système > langues de l'app,
            // Android 13+ seulement, voir res/xml/locales_config.xml) -- utile en particulier sur
            // les deux appareils de test de ce projet (Android 11), qui n'ont pas ce réglage système.
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.display_language_label),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = uiState.appLanguage == AppLanguage.SYSTEM,
                    onClick = { onSetAppLanguage(AppLanguage.SYSTEM) },
                    label = { Text(stringResource(R.string.display_language_system)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.appLanguage == AppLanguage.FRENCH,
                    onClick = { onSetAppLanguage(AppLanguage.FRENCH) },
                    label = { Text(stringResource(R.string.display_language_french)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.appLanguage == AppLanguage.ENGLISH,
                    onClick = { onSetAppLanguage(AppLanguage.ENGLISH) },
                    label = { Text(stringResource(R.string.display_language_english)) },
                )
            }
        }
    }
}
