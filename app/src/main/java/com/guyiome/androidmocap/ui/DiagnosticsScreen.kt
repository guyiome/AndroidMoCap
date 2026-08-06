package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Diagnostics",
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
        }
    }
}
