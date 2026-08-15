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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R

/**
 * Catégorie "Affichage" -- une des six catégories de [SettingsScreen] (refonte des menus,
 * regroupement par intention plutôt que par couche technique). Slimmée à ce qui concerne
 * uniquement le rendu visuel du tracking (overlay du mesh, mode miroir) -- la sélection de
 * blendshapes affichés a été promue en catégorie de premier niveau ([BlendshapesScreen]), et
 * l'énergie/batterie/langue déplacées dans [ComfortSettingsScreen].
 */
@Composable
fun DisplaySettingsScreen(
    uiState: MainUiState,
    onClose: () -> Unit,
    onSetFaceMeshOverlay: (Boolean) -> Unit,
    onSetKeepMeshOverlayInPowerSave: (Boolean) -> Unit,
    onSetMirrorMode: (Boolean) -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .blockClicksBehind()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
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
        }
    }
}
