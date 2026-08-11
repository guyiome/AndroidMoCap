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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.tracking.TongueCalibrationPhase

/**
 * Calibration personnelle de l'étage 3 (revue technique, point 15) : deux enregistrements de
 * quelques secondes ("langue dehors" puis "langue rentrée") qui produisent les deux vecteurs de
 * référence utilisés par [com.guyiome.androidmocap.tracking.classifyTongueState]. Même esprit
 * UX que la calibration de pose neutre (`MainHud`, `calibrationCountdownSeconds`) -- compte à
 * rebours puis capture -- mais étendu à un enregistrement multi-secondes accumulé plutôt qu'un
 * instantané unique, voir `TongueCalibrationRecordingState.kt`.
 */
@Composable
fun TongueCalibrationScreen(
    phase: TongueCalibrationPhase,
    isCalibrated: Boolean,
    onStartCalibration: () -> Unit,
    onCancel: () -> Unit,
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
                    stringResource(R.string.tongue_calibration_title),
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
                stringResource(R.string.tongue_calibration_explanation),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(24.dp))
            val statusText = when (phase) {
                TongueCalibrationPhase.IDLE ->
                    if (isCalibrated) {
                        stringResource(R.string.tongue_calibration_status_calibrated)
                    } else {
                        stringResource(R.string.tongue_calibration_status_never)
                    }
                TongueCalibrationPhase.RECORDING_TONGUE_OUT -> stringResource(R.string.tongue_calibration_status_recording_out)
                TongueCalibrationPhase.RECORDING_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_recording_in)
                TongueCalibrationPhase.DONE -> stringResource(R.string.tongue_calibration_status_calibrated)
            }
            Text(statusText, color = Color.White, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(24.dp))
            when (phase) {
                TongueCalibrationPhase.IDLE, TongueCalibrationPhase.DONE -> {
                    Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (isCalibrated) {
                                stringResource(R.string.tongue_calibration_button_recalibrate)
                            } else {
                                stringResource(R.string.tongue_calibration_button_start)
                            }
                        )
                    }
                }
                TongueCalibrationPhase.RECORDING_TONGUE_OUT, TongueCalibrationPhase.RECORDING_TONGUE_IN -> {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tongue_calibration_button_cancel))
                    }
                }
            }
        }
    }
}
