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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

/**
 * Diagnostics en lecture seule (palier de tracking, délégué, détection, latence, calibration) --
 * une des quatre catégories de [SettingsScreen] (voir rapport technique, point 21). Reçoit l'état
 * chaud ([faceDetected]/[inferenceTimeMs], issus de [MainViewModel.trackingFrame]) séparément de
 * [uiState], même séparation froid/chaud que partout ailleurs dans l'app.
 */
@Composable
fun DiagnosticsScreen(
    uiState: MainUiState,
    faceDetected: Boolean,
    inferenceTimeMs: Long,
    onClose: () -> Unit,
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
                    stringResource(R.string.diagnostics_title),
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
                stringResource(R.string.diagnostics_tier, uiState.tier?.name ?: stringResource(R.string.placeholder_unknown)),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.diagnostics_delegate, if (uiState.activeDelegateIsGpu) "GPU" else "CPU"),
                color = Color.White,
            )
            Text(
                stringResource(
                    R.string.diagnostics_face_detected,
                    if (faceDetected) stringResource(R.string.bool_yes) else stringResource(R.string.bool_no),
                ),
                color = Color.White,
            )
            Text(stringResource(R.string.diagnostics_inference_latency, inferenceTimeMs), color = Color.White)
            Text(
                if (uiState.isCalibrated) {
                    stringResource(R.string.diagnostics_calibrated)
                } else {
                    stringResource(R.string.diagnostics_not_calibrated)
                },
                color = if (uiState.isCalibrated) Color(0xFF9FE7B0) else Color.White,
            )
        }
    }
}
