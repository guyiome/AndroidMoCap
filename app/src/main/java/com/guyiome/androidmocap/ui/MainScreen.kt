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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guyiome.androidmocap.sensors.BatteryMonitor
import com.guyiome.androidmocap.sensors.IconOrientationTracker

/**
 * Écran unique de l'app : preview caméra pleine page + bandeau d'icônes minimal ([MainHud]) +
 * menu de réglages ([SettingsScreen]) affiché par-dessus à la demande, qui ouvre à son tour l'une
 * de ses quatre catégories ([DiagnosticsScreen], [ConnectionSettingsScreen],
 * [DisplaySettingsScreen], [ExperimentalFeaturesScreen] -- voir rapport technique, point 21).
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
    // Menu des réglages + ses quatre sous-écrans (voir rapport technique, point 21) -- même
    // principe "un booléen par écran conditionnel" que showBlendshapeSelection ci-dessous,
    // désormais ouvert depuis DisplaySettingsScreen plutôt que directement depuis ce menu.
    var showSettings by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showConnectionSettings by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showExperimentalFeatures by remember { mutableStateOf(false) }
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

    // Fait pivoter les icônes du HUD (IconOrientationTracker) et surveille la batterie
    // (BatteryMonitor, utile en session longue, le téléphone étant souvent posé loin de
    // l'utilisateur sur un support). Rattachés au cycle de vie de l'Activity (ON_START/ON_STOP)
    // plutôt que directement démarrés ici : un DisposableEffect(context) ne se redéclenche que si
    // le composable quitte la composition, pas quand l'app passe juste en arrière-plan (l'Activity
    // reste en mémoire) -- sans ça, ces deux-là continuaient à écouter capteur/broadcast batterie
    // inutilement pendant que l'app n'était plus au premier plan.
    DisposableEffect(lifecycleOwner, context) {
        val iconTracker = IconOrientationTracker(context) { degrees -> iconRotationDegrees = degrees.toFloat() }
        val batteryMonitor = BatteryMonitor(context) { percent, charging ->
            batteryPercent = percent
            isCharging = charging
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    iconTracker.enable()
                    batteryMonitor.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    iconTracker.disable()
                    batteryMonitor.stop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            iconTracker.disable()
            batteryMonitor.stop()
        }
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

            // Overlay optionnel du mesh de tracking -- masqué en mode éco comme le reste de
            // l'aperçu (PowerSaveOverlay le recouvrira de toute façon, mais autant ne pas dessiner
            // 478 points par frame pour rien pendant que l'écran est délibérément assombri).
            if (uiState.faceMeshOverlayEnabled && !uiState.isPowerSaveActive) {
                FaceMeshOverlay(
                    landmarks = trackingFrame.faceLandmarks,
                    modifier = Modifier.fillMaxSize(),
                )
            }

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
                    onClose = { showSettings = false },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenConnection = { showConnectionSettings = true },
                    onOpenDisplay = { showDisplaySettings = true },
                    onOpenExperimental = { showExperimentalFeatures = true },
                )
            }

            if (showDiagnostics) {
                DiagnosticsScreen(
                    uiState = uiState,
                    faceDetected = trackingFrame.faceDetected,
                    inferenceTimeMs = trackingFrame.inferenceTimeMs,
                    onClose = { showDiagnostics = false },
                )
            }

            if (showConnectionSettings) {
                ConnectionSettingsScreen(
                    uiState = uiState,
                    onClose = { showConnectionSettings = false },
                    onSelectConnectionType = { type -> viewModel.setConnectionType(type) },
                    onConnectVmc = { host -> viewModel.connectVmcTarget(host) },
                    onDisconnectVmc = { viewModel.disconnectVmcTarget() },
                    onStartIFacialMocap = { viewModel.startIFacialMocapListening() },
                    onStopIFacialMocap = { viewModel.stopIFacialMocapListening() },
                )
            }

            if (showDisplaySettings) {
                DisplaySettingsScreen(
                    uiState = uiState,
                    onClose = { showDisplaySettings = false },
                    onOpenBlendshapeSelection = { showBlendshapeSelection = true },
                    onSetPersistBlendshapeSelection = { enabled -> viewModel.setPersistBlendshapeSelectionEnabled(enabled) },
                    onSetLowBatteryThreshold = { percent -> viewModel.setLowBatteryThresholdPercent(percent) },
                    onSetPowerSaveMode = { enabled -> viewModel.setPowerSaveModeEnabled(enabled) },
                    onSetPowerSaveDelay = { seconds -> viewModel.setPowerSaveDelaySeconds(seconds) },
                    onSetFaceMeshOverlay = { enabled -> viewModel.setFaceMeshOverlayEnabled(enabled) },
                )
            }

            if (showExperimentalFeatures) {
                ExperimentalFeaturesScreen(onClose = { showExperimentalFeatures = false })
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
