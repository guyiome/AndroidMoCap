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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
 * Catégorie dédiée aux fonctionnalités expérimentales -- une des quatre catégories de
 * [SettingsScreen] (voir rapport technique, point 21), créée par anticipation des points 15
 * (détection de la langue tirée) et 16 (détection des joues gonflées). Point 16 reste un
 * placeholder pur ; point 15 a désormais un vrai réglage (phase 1 de la cascade, purement
 * diagnostique -- voir `AppSettingsStore.tongueOutDetectionEnabled`).
 */
@Composable
fun ExperimentalFeaturesScreen(
    uiState: MainUiState,
    onClose: () -> Unit,
    onSetTongueOutDetectionEnabled: (Boolean) -> Unit,
    onOpenTongueCalibration: () -> Unit,
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
                    stringResource(R.string.experimental_features_title),
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
                    Text(
                        stringResource(R.string.experimental_tongue_detection_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.experimental_tongue_detection_description),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = uiState.tongueOutDetectionEnabled, onCheckedChange = onSetTongueOutDetectionEnabled)
            }
            if (uiState.tongueOutDetectionEnabled) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenTongueCalibration, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.experimental_tongue_calibration_button))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.experimental_features_placeholder),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
