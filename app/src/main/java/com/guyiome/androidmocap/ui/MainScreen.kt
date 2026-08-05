package com.guyiome.androidmocap.ui

import android.app.Activity
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guyiome.androidmocap.sensors.BatteryMonitor
import com.guyiome.androidmocap.sensors.IconOrientationTracker

/**
 * Écran unique de l'app : preview caméra pleine page + bandeau d'icônes minimal ([MainHud]) +
 * panneau de réglages/diagnostics ([SettingsScreen]) affiché par-dessus à la demande.
 */
@Composable
fun MainScreen(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // État "chaud" (mis à jour à chaque frame traitée) volontairement séparé de uiState -- voir
    // MainViewModel.TrackingFrame -- pour que les recompositions à ce rythme restent limitées aux
    // composants qui en ont réellement besoin (icône visage détecté, panneau blendshapes,
    // diagnostics des réglages), pas tout l'écran.
    val trackingFrame by viewModel.trackingFrame.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    var showSettings by remember { mutableStateOf(false) }
    var showBlendshapeSelection by remember { mutableStateOf(false) }
    var iconRotationDegrees by remember { mutableStateOf(0f) }
    var batteryPercent by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(true) }

    // Panneau blendshapes : pas de rotation continue (illisible pendant la transition), juste un
    // pas de 90° selon que le téléphone est tenu à la verticale ou à l'horizontale.
    val panelRotationDegrees = when {
        iconRotationDegrees in 45f..135f -> -90f
        iconRotationDegrees in 225f..315f -> 90f
        else -> 0f
    }

    // initializeTracking() PUIS startCamera() dans le même effet : garantit que le
    // CameraController existe déjà quand on lui demande de démarrer (sinon startCamera()
    // ne fait rien silencieusement -- c'était le bug de la caméra qui ne s'allumait pas).
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            viewModel.initializeTracking(lifecycleOwner)
            viewModel.startCamera(previewView)
        }
    }

    // Fait pivoter les icônes du HUD pour qu'elles restent lisibles quel que soit l'angle
    // auquel le téléphone est tenu/posé (indépendant de la compensation de pose de tête).
    DisposableEffect(context) {
        val tracker = IconOrientationTracker(context) { degrees -> iconRotationDegrees = degrees.toFloat() }
        tracker.enable()
        onDispose { tracker.disable() }
    }

    // Surveille la batterie en continu -- utile en session longue, le téléphone étant souvent
    // posé loin de l'utilisateur sur un support.
    DisposableEffect(context) {
        val monitor = BatteryMonitor(context) { percent, charging ->
            batteryPercent = percent
            isCharging = charging
        }
        monitor.start()
        onDispose { monitor.stop() }
    }

    // Mode économie d'énergie : on ne peut pas vraiment éteindre l'écran (ça mettrait l'Activity
    // en arrière-plan et couperait la caméra, cf. FLAG_KEEP_SCREEN_ON) -- on assombrit au minimum
    // à la place, en plus de couper l'aperçu caméra (voir CameraController.setPreviewEnabled).
    val view = LocalView.current
    LaunchedEffect(uiState.isPowerSaveActive) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = if (uiState.isPowerSaveActive) {
                0.01f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Détecteur de toucher global, non consommant (Initial + pas de consume()) : ne gêne
            // aucun bouton/contrôle, sert uniquement à reporter/annuler la bascule automatique en
            // mode éco (voir MainViewModel.onUserInteraction).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        viewModel.onUserInteraction()
                    }
                }
            },
    ) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView },
            )

            // Mode économie d'énergie : masque l'aperçu (déjà coupé côté CameraX, voir
            // setPreviewEnabled) et le panneau blendshapes derrière un fond noir -- les 4
            // contrôles du HUD restent accessibles (dont l'icône "Visage détecté").
            if (uiState.isPowerSaveActive) {
                PowerSaveOverlay()
            } else {
                val selectedBlendshapeValues = trackingFrame.allBlendshapes
                    .filter { it.name in uiState.selectedBlendshapeNames }
                    .sortedBy { it.name }
                    .map { it.name to it.score }
                BlendshapePanel(
                    values = selectedBlendshapeValues,
                    panelRotationDegrees = panelRotationDegrees,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
            }

            MainHud(
                uiState = uiState,
                faceDetected = trackingFrame.faceDetected,
                iconRotationDegrees = iconRotationDegrees,
                onCalibrate = { viewModel.startCalibrationCountdown() },
                onToggleConnection = { viewModel.toggleActiveConnection() },
                onOpenSettings = { showSettings = true },
            )

            if (showSettings) {
                SettingsScreen(
                    uiState = uiState,
                    faceDetected = trackingFrame.faceDetected,
                    inferenceTimeMs = trackingFrame.inferenceTimeMs,
                    onClose = { showSettings = false },
                    onSelectConnectionType = { type -> viewModel.setConnectionType(type) },
                    onConnectVmc = { host -> viewModel.connectVmcTarget(host) },
                    onDisconnectVmc = { viewModel.disconnectVmcTarget() },
                    onStartIFacialMocap = { viewModel.startIFacialMocapListening() },
                    onStopIFacialMocap = { viewModel.stopIFacialMocapListening() },
                    onSetLowBatteryThreshold = { percent -> viewModel.setLowBatteryThresholdPercent(percent) },
                    onOpenBlendshapeSelection = { showBlendshapeSelection = true },
                    onSetPowerSaveMode = { enabled -> viewModel.setPowerSaveModeEnabled(enabled) },
                    onSetPowerSaveDelay = { seconds -> viewModel.setPowerSaveDelaySeconds(seconds) },
                )
            }

            if (showBlendshapeSelection) {
                BlendshapeSelectionScreen(
                    selectedNames = uiState.selectedBlendshapeNames,
                    onToggle = { name -> viewModel.toggleBlendshapeSelection(name) },
                    onClose = { showBlendshapeSelection = false },
                )
            }

            if (batteryPercent <= uiState.lowBatteryThresholdPercent && !isCharging) {
                LowBatteryAlert()
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("La caméra frontale est nécessaire pour la capture faciale.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestPermission) { Text("Autoriser la caméra") }
            }
        }
    }
}
