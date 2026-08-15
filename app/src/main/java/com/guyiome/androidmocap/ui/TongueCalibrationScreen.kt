package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.tracking.TongueCalibrationPhase

/**
 * Calibration personnelle de l'étage 3 : deux enregistrements de quelques secondes ("langue
 * dehors" puis "langue rentrée") qui produisent les deux vecteurs de référence utilisés par
 * [com.guyiome.androidmocap.tracking.classifyTongueState]. Même esprit UX que la calibration de
 * pose neutre (`MainHud`, `calibrationCountdownSeconds`) -- compte à rebours puis capture -- mais
 * étendu à un enregistrement multi-secondes accumulé plutôt qu'un instantané unique, voir
 * `TongueCalibrationRecordingState.kt`.
 *
 * Aperçu caméra volontairement laissé visible (pas de fond opaque, contrairement aux autres écrans
 * de réglages) -- l'utilisateur doit pouvoir voir comment il est cadré pendant qu'il tire la
 * langue/ouvre la mâchoire, pas seulement lire des instructions sur fond noir. Le message de phase
 * et le compte à rebours pivotent par pas de 90° selon [rotationDegrees] (verticale/horizontale
 * seulement) -- **pas** de rotation continue comme les icônes de `MainHud` : un bloc de texte en
 * rotation continue serait illisible pendant la transition, exactement le même choix déjà fait pour
 * [BlendshapePanel] (voir son kdoc) et la même valeur pré-calculée dans `MainScreen.kt`
 * (`panelRotationDegrees`, via `snapToRotationBucket()`), réutilisée telle quelle ici.
 */
@Composable
fun TongueCalibrationScreen(
    phase: TongueCalibrationPhase,
    secondsRemaining: Int?,
    isCalibrated: Boolean,
    recordingDurationMs: Long,
    classificationMargin: Float,
    rotationDegrees: Float,
    onStartCalibration: () -> Unit,
    onCancel: () -> Unit,
    onSetRecordingDurationMs: (Long) -> Unit,
    onSetClassificationMargin: (Float) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // État local, volontairement non hoisté/persisté -- même patron que AdvancedSettingsScreen (voir
    // DebugPanelUnlock.kt) : le geste (7 taps sur le titre) ne coûte que quelques secondes à refaire.
    var debugUnlock by remember { mutableStateOf(DebugPanelUnlockState()) }

    // Fermeture automatique une fois la calibration réellement terminée : la machine à état
    // (TongueCalibrationRecordingState) traite DONE et retombe sur IDLE dans le même tick côté
    // MainViewModel, donc cet écran n'observe jamais DONE tel quel -- seule la transition
    // RECORDING_TONGUE_IN -> IDLE, combinée à isCalibrated déjà vrai, signale une fin réelle plutôt
    // qu'un simple retour à l'écran de menu. Limite connue et assumée (refonte cosmétique
    // uniquement, pas de nouveau signal côté ViewModel) : annuler précisément pendant cette
    // dernière phase, alors qu'une calibration antérieure existait déjà, déclenche aussi cette
    // fermeture -- rare, sans conséquence fonctionnelle.
    var previousPhase by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (previousPhase == TongueCalibrationPhase.RECORDING_TONGUE_IN &&
            phase == TongueCalibrationPhase.IDLE &&
            isCalibrated
        ) {
            onClose()
        }
        previousPhase = phase
    }

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        // Message de phase -- pastille semi-transparente (même style que le bandeau d'icônes de
        // MainHud) plutôt qu'un plein écran noir, pour laisser l'aperçu caméra visible en dessous.
        // Pivote comme un seul bloc (graphicsLayer, pas juste le texte dans un cadre fixe) par pas
        // de 90° -- même pattern que BlendshapePanel, pivot au bas du bloc (TransformOrigin(0.5f, 1f)
        // plutôt que le centre par défaut) pour que la rotation déborde vers l'intérieur de l'écran,
        // pas au-dessus du bord physique (voir kdoc de BlendshapePanel pour le raisonnement complet).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 16.dp)
                .graphicsLayer(rotationZ = rotationDegrees, transformOrigin = TransformOrigin(0.5f, 1f))
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 320.dp),
        ) {
            Text(
                stringResource(R.string.tongue_calibration_title),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable {
                    debugUnlock = debugUnlock.registerTap(System.currentTimeMillis())
                },
            )

            Spacer(Modifier.height(6.dp))
            when (phase) {
                TongueCalibrationPhase.IDLE, TongueCalibrationPhase.DONE -> {
                    Text(
                        stringResource(R.string.tongue_calibration_explanation),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isCalibrated) {
                            stringResource(R.string.tongue_calibration_status_calibrated)
                        } else {
                            stringResource(R.string.tongue_calibration_status_never)
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                else -> {
                    val statusText = when (phase) {
                        TongueCalibrationPhase.PREPARE_TONGUE_OUT -> stringResource(R.string.tongue_calibration_status_prepare_out)
                        TongueCalibrationPhase.RECORDING_TONGUE_OUT -> stringResource(R.string.tongue_calibration_status_recording_out)
                        TongueCalibrationPhase.PREPARE_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_prepare_in)
                        TongueCalibrationPhase.RECORDING_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_recording_in)
                        // IDLE/DONE déjà traités dans la branche du when englobant -- le compilateur
                        // sait ce when-ci exhaustif sans eux (smart-cast), pas de else nécessaire.
                    }
                    Text(
                        statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Compte à rebours : gros chiffres en préparation (l'utilisateur doit encore se
            // positionner, un repère bien visible aide), petits pendant la prise de vues elle-même
            // (ne doit pas distraire du geste à tenir) -- demande explicite de l'utilisateur.
            if (secondsRemaining != null) {
                val isPreparePhase = phase == TongueCalibrationPhase.PREPARE_TONGUE_OUT ||
                    phase == TongueCalibrationPhase.PREPARE_TONGUE_IN
                Spacer(Modifier.height(if (isPreparePhase) 8.dp else 4.dp))
                Text(
                    "${secondsRemaining}s",
                    color = Color.White,
                    style = if (isPreparePhase) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleLarge,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .fillMaxWidth(),
        ) {
            if (debugUnlock.unlocked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        stringResource(R.string.tongue_calibration_debug_section_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    // Unité de base, pas la durée réelle d'une phase (chaque phase la multiplie par
                    // son propre facteur -- voir TONGUE_OUT_RECORDING_MULTIPLIER/
                    // TONGUE_IN_RECORDING_MULTIPLIER) -- pas de rebuild pour retenter un réglage.
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
                Spacer(Modifier.height(12.dp))
            }

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
                TongueCalibrationPhase.PREPARE_TONGUE_OUT,
                TongueCalibrationPhase.RECORDING_TONGUE_OUT,
                TongueCalibrationPhase.PREPARE_TONGUE_IN,
                TongueCalibrationPhase.RECORDING_TONGUE_IN -> {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tongue_calibration_button_cancel))
                    }
                }
            }
        }
    }
}
