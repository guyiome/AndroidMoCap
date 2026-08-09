package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.guyiome.androidmocap.BuildConfig
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.tracking.TierCompatibility
import com.guyiome.androidmocap.tracking.TrackingTier
import com.guyiome.androidmocap.tracking.TrackingTierSelector

/**
 * Diagnostics -- une des quatre catégories de [SettingsScreen] (voir rapport technique, point 21).
 * Très majoritairement en lecture seule (palier de tracking, délégué, détection, latence,
 * calibration), à l'exception du forçage de palier ([onSetTierOverride]) et du panneau de mocks de
 * debug caché ci-dessous -- outils de diagnostic, pas des fonctionnalités utilisateur normales.
 * Reçoit l'état chaud ([faceDetected]/[inferenceTimeMs], issus de [MainViewModel.trackingFrame])
 * séparément de [uiState], même séparation froid/chaud que partout ailleurs dans l'app.
 *
 * **Panneau de mocks de debug** (voir revue technique, point 35) : révélé en tapant
 * [DEBUG_PANEL_UNLOCK_TAP_COUNT] fois sur la ligne "Version de l'app" en bas de l'écran, même
 * principe que le menu développeur Android. Permet de mocker trois comportements difficiles à
 * déclencher naturellement sur un appareil donné (chauffe, ARCore, délégué GPU) -- voir
 * [onSetDebugThermalOverride]/[onSetDebugForceArCoreUnavailable]/[onSetDebugForceGpuUnavailable].
 * Le bandeau d'avertissement piloté par [onResetDebugOverrides] reste volontairement visible même
 * sans déverrouiller le panneau : un mock persistant resté actif par erreur (ARCore/GPU forcés,
 * survivent à un redémarrage -- voir leur doc côté `AppSettingsStore`) doit rester réparable sans
 * avoir à refaire le geste.
 */
@Composable
fun DiagnosticsScreen(
    uiState: MainUiState,
    faceDetected: Boolean,
    inferenceTimeMs: Long,
    // Diagnostic Eye Aspect Ratio (revue technique, point 28) -- affiché à côté du blendshape
    // eyeBlink brut (à sélectionner dans le panneau de blendshapes de l'écran principal pour
    // comparer). A servi à diagnostiquer les correctifs de fiabilisation du clignement
    // (EyeBlinkCorrection.kt) ; conservé comme outil de diagnostic permanent, utile si un futur
    // souci de clignement doit être investigué sans repasser par adb logcat. GROUP_A/GROUP_B
    // désignent les mêmes indices que EyeLandmarkIndices.RIGHT_EYE/LEFT_EYE.
    eyeAspectRatioGroupA: Float,
    eyeAspectRatioGroupB: Float,
    onClose: () -> Unit,
    onSetTierOverride: (TrackingTier?) -> Unit,
    onSetDebugForceArCoreUnavailable: (Boolean) -> Unit,
    onSetDebugForceGpuUnavailable: (Boolean) -> Unit,
    onSetDebugThermalOverride: (Boolean?) -> Unit,
    onResetDebugOverrides: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // État local, volontairement non hoisté au ViewModel/persisté -- voir kdoc de DebugPanelUnlockState.
    var debugUnlock by remember { mutableStateOf(DebugPanelUnlockState()) }
    val anyDebugOverrideActive = uiState.debugForceArCoreUnavailable ||
        uiState.debugForceGpuUnavailable ||
        uiState.debugThermalOverride != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
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

            // Toujours visible (pas gaté par debugUnlock.unlocked) : un mock persistant resté actif
            // par erreur doit rester réparable même si le geste de déverrouillage est oublié.
            if (anyDebugOverrideActive) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.diagnostics_debug_override_active_warning),
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onResetDebugOverrides) {
                        Text(stringResource(R.string.action_reset))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.diagnostics_tier, uiState.tier?.name ?: stringResource(R.string.placeholder_unknown)),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.diagnostics_tier_override_label),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = uiState.tierOverride == null,
                    onClick = { onSetTierOverride(null) },
                    label = { Text(stringResource(R.string.diagnostics_tier_override_auto)) },
                )
                // Garde-fou (demande explicite, 6 août 2026) : un palier réellement incompatible
                // avec l'appareil (ex. OPTIMAL sans ARCore -- déclencherait de toute façon le repli
                // silencieux déjà en place, autant l'empêcher clairement) désactive sa puce plutôt
                // que de laisser sélectionner quelque chose qui ne fera pas ce que l'utilisateur
                // croit. Un simple risque de performance (palier plus exigeant que ce que
                // l'automatique aurait choisi pour cet appareil) reste sélectionnable, juste
                // signalé par une icône d'avertissement -- même code couleur que
                // BlendshapeSelectionScreen pour les blendshapes peu fiables.
                val capabilities = uiState.capabilities
                TrackingTier.entries.forEach { tier ->
                    Spacer(Modifier.width(8.dp))
                    val compatibility = capabilities?.let { TrackingTierSelector.compatibility(tier, it) }
                        ?: TierCompatibility.OK
                    FilterChip(
                        selected = uiState.tierOverride == tier,
                        onClick = { onSetTierOverride(tier) },
                        enabled = compatibility != TierCompatibility.INCOMPATIBLE,
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tier.name)
                                if (compatibility == TierCompatibility.PERFORMANCE_RISK) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = stringResource(R.string.cd_tier_performance_risk),
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.diagnostics_tier_override_hint),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
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
            // Diagnostic temporaire EAR (voir kdoc du paramètre ci-dessus) -- valeurs à 0,00 tant
            // que l'overlay du mesh de tracking (Affichage & confort) n'est pas activé, seul
            // moment où les landmarks sont extraits aujourd'hui.
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.diagnostics_ear_debug_label),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(
                    R.string.diagnostics_ear_debug_value,
                    "%.2f".format(eyeAspectRatioGroupA),
                    "%.2f".format(eyeAspectRatioGroupB),
                ),
                color = Color(0xFF7CE0FF),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.diagnostics_ear_debug_hint),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
            )
            // Source caméra actuelle -- utile pour vérifier sur device si le repli silencieux
            // CameraX (ArCoreHeadPoseTracker.onUnavailable) s'est déclenché malgré le palier
            // OPTIMAL choisi. Sans objet pour les autres paliers (toujours CameraX).
            if (uiState.tier == TrackingTier.OPTIMAL) {
                Text(
                    stringResource(
                        R.string.diagnostics_camera_source,
                        if (uiState.usingArCoreCameraSource) {
                            stringResource(R.string.diagnostics_camera_source_arcore)
                        } else {
                            stringResource(R.string.diagnostics_camera_source_camerax)
                        },
                    ),
                    color = Color.White,
                )
            }
            Text(
                if (uiState.isCalibrated) {
                    stringResource(R.string.diagnostics_calibrated)
                } else {
                    stringResource(R.string.diagnostics_not_calibrated)
                },
                color = if (uiState.isCalibrated) Color(0xFF9FE7B0) else Color.White,
            )
            // Débit réduit en cas de chauffe (voir MainViewModel.startThermalPolling) -- même
            // signal que l'icône du HUD principal, disponible ici aussi pour qui préfère consulter
            // les diagnostics plutôt que l'aperçu caméra.
            Text(
                if (uiState.isThermalThrottling) {
                    stringResource(R.string.diagnostics_thermal_throttling_active)
                } else {
                    stringResource(R.string.diagnostics_thermal_throttling_inactive)
                },
                color = if (uiState.isThermalThrottling) Color(0xFFFFB74D) else Color.White,
            )
            // Signal purement informatif après une chauffe persistante -- jamais appliqué tout
            // seul (voir ThermalThrottleState.downgradeSuggested) : renvoie au sélecteur de palier
            // manuel ci-dessus, où la décision reste entièrement celle de l'utilisateur.
            if (uiState.thermalDowngradeSuggested) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.diagnostics_thermal_downgrade_suggestion),
                        color = Color(0xFFFFB74D),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            // Cible du déverrouillage du panneau de mocks de debug -- voir kdoc de la fonction.
            Text(
                stringResource(R.string.diagnostics_app_version, BuildConfig.VERSION_NAME),
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable {
                    debugUnlock = debugUnlock.registerTap(System.currentTimeMillis())
                },
            )

            if (debugUnlock.unlocked) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.diagnostics_debug_section_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.diagnostics_debug_section_hint),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )

                // Mock ARCore indisponible -- persisté, s'applique au prochain lancement.
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.diagnostics_debug_arcore_unavailable_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        selected = !uiState.debugForceArCoreUnavailable,
                        onClick = { onSetDebugForceArCoreUnavailable(false) },
                        label = { Text(stringResource(R.string.diagnostics_debug_override_off)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = uiState.debugForceArCoreUnavailable,
                        onClick = { onSetDebugForceArCoreUnavailable(true) },
                        label = { Text(stringResource(R.string.diagnostics_debug_override_on)) },
                    )
                }
                Text(
                    stringResource(R.string.diagnostics_debug_relaunch_hint),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Mock délégué GPU indisponible -- même forme, même contrainte de relance.
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.diagnostics_debug_gpu_unavailable_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        selected = !uiState.debugForceGpuUnavailable,
                        onClick = { onSetDebugForceGpuUnavailable(false) },
                        label = { Text(stringResource(R.string.diagnostics_debug_override_off)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = uiState.debugForceGpuUnavailable,
                        onClick = { onSetDebugForceGpuUnavailable(true) },
                        label = { Text(stringResource(R.string.diagnostics_debug_override_on)) },
                    )
                }
                Text(
                    stringResource(R.string.diagnostics_debug_relaunch_hint),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Mock thermique -- session-only, effet immédiat, pas de redémarrage requis.
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.diagnostics_debug_thermal_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        selected = uiState.debugThermalOverride == null,
                        onClick = { onSetDebugThermalOverride(null) },
                        label = { Text(stringResource(R.string.diagnostics_tier_override_auto)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = uiState.debugThermalOverride == true,
                        onClick = { onSetDebugThermalOverride(true) },
                        label = { Text(stringResource(R.string.diagnostics_debug_thermal_forced_active)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = uiState.debugThermalOverride == false,
                        onClick = { onSetDebugThermalOverride(false) },
                        label = { Text(stringResource(R.string.diagnostics_debug_thermal_forced_inactive)) },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = onResetDebugOverrides) {
                    Text(stringResource(R.string.diagnostics_debug_reset_all))
                }
            } else if (debugUnlock.tapCount > 0) {
                // Retour identique à la convention Android "numéro de build" -- confirme que les
                // appuis sont bien comptés, sans révéler le panneau avant le dernier appui.
                Text(
                    stringResource(R.string.diagnostics_debug_taps_remaining, debugUnlock.remainingTaps),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
