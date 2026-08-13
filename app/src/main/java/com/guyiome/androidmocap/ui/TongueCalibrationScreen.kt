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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    secondsRemaining: Int?,
    isCalibrated: Boolean,
    recordingDurationMs: Long,
    classificationMargin: Float,
    onStartCalibration: () -> Unit,
    onCancel: () -> Unit,
    onSetRecordingDurationMs: (Long) -> Unit,
    onSetClassificationMargin: (Float) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // État local, volontairement non hoisté/persisté -- même patron que DiagnosticsScreen (voir
    // DebugPanelUnlock.kt) : le geste (7 taps sur le titre) ne coûte que quelques secondes à refaire.
    var debugUnlock by remember { mutableStateOf(DebugPanelUnlockState()) }
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
                    modifier = Modifier
                        .weight(1f)
                        .clickable { debugUnlock = debugUnlock.registerTap(System.currentTimeMillis()) },
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
                TongueCalibrationPhase.PREPARE_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_prepare_in)
                TongueCalibrationPhase.RECORDING_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_recording_in)
                TongueCalibrationPhase.DONE -> stringResource(R.string.tongue_calibration_status_calibrated)
            }
            Text(statusText, color = Color.White, style = MaterialTheme.typography.titleMedium)

            // Compte à rebours numérique, dérivé du même état réel qui pilote l'accumulation
            // d'embeddings (pas un minuteur indépendant qui pourrait dériver) -- demande explicite
            // de l'utilisateur (11 août 2026) pour un meilleur retour pendant la calibration.
            if (secondsRemaining != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${secondsRemaining}s",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.displaySmall,
                )
            }

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
                TongueCalibrationPhase.RECORDING_TONGUE_OUT, TongueCalibrationPhase.PREPARE_TONGUE_IN, TongueCalibrationPhase.RECORDING_TONGUE_IN -> {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tongue_calibration_button_cancel))
                    }
                }
            }

            if (debugUnlock.unlocked) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.tongue_calibration_debug_section_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )

                // Unité de base, pas la durée réelle d'une phase (chaque phase la multiplie par son
                // propre facteur -- ×2 pour les deux depuis le 13 août 2026, voir kdoc
                // TONGUE_OUT_RECORDING_MULTIPLIER/TONGUE_IN_RECORDING_MULTIPLIER) -- pas de rebuild
                // pour retenter un autre réglage, vu le nombre de sessions qu'ont demandé les étages 1/2.
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.tongue_calibration_debug_duration, recordingDurationMs / 1000f),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSetRecordingDurationMs((recordingDurationMs - 500L).coerceAtLeast(500L)) }) {
                        Text("-0.5s")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onSetRecordingDurationMs(recordingDurationMs + 500L) }) {
                        Text("+0.5s")
                    }
                }

                // Marge de classification (voir TongueEmbeddingClassifier.classifyTongueState).
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.tongue_calibration_debug_margin, classificationMargin),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSetClassificationMargin((classificationMargin - 0.01f).coerceAtLeast(0f)) }) {
                        Text("-0.01")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onSetClassificationMargin(classificationMargin + 0.01f) }) {
                        Text("+0.01")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tongue_calibration_debug_hint),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
