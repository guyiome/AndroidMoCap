package com.guyiome.androidmocap.ui

import android.app.Activity
import android.opengl.GLSurfaceView
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guyiome.androidmocap.R
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
    // Hébergement de la texture caméra ARCore (palier OPTIMAL, voir ArCoreHeadPoseTracker) --
    // requis par ARCore même si rien n'y est dessiné visuellement pour l'instant (voir
    // computeMeshOverlayVisible : le mesh de tracking sert de seul retour visuel dans ce cas).
    // Toujours créé (même coût que previewView ci-dessus, symétrique) mais seulement attaché à
    // ArCoreHeadPoseTracker quand uiState.usingArCoreCameraSource est actif, voir plus bas.
    val glSurfaceView = remember { GLSurfaceView(context) }
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
            // Aperçu caméra : PreviewView (CameraX) normalement, ou GLSurfaceView (ARCore) au
            // palier OPTIMAL avec ARCore actif -- les deux ne coexistent jamais (voir revue
            // technique, point 13). Rien n'est visuellement dessiné dans le GLSurfaceView pour
            // l'instant (pas de rendu live caméra ARCore dans cette passe, voir MeshOverlayVisibility) --
            // il n'existe que parce qu'ARCore exige une texture GL pour piloter la caméra.
            if (uiState.usingArCoreCameraSource) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        viewModel.attachArCoreSurface(glSurfaceView)
                        glSurfaceView
                    },
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { previewView },
                )
            }

            // Overlay du mesh de tracking -- affiché soit sur choix explicite de l'utilisateur
            // (réglage Affichage & confort), soit forcé quand ARCore est la source caméra active
            // (aucun aperçu live dans ce cas, le mesh devient le seul retour visuel). Masqué par
            // défaut en mode éco comme le reste de l'aperçu, sauf si l'utilisateur a choisi de le
            // garder visible même en éco -- voir computeMeshOverlayVisible.
            val meshOverlayVisible = computeMeshOverlayVisible(
                usingArCoreCameraSource = uiState.usingArCoreCameraSource,
                faceMeshOverlayEnabled = uiState.faceMeshOverlayEnabled,
                isPowerSaveActive = uiState.isPowerSaveActive,
                keepMeshOverlayInPowerSave = uiState.keepMeshOverlayInPowerSave,
            )

            // Mode économie d'énergie : masque l'aperçu (déjà coupé côté CameraX, voir
            // setPreviewEnabled ; sans objet côté ARCore, qui n'a pas d'aperçu live) et le panneau
            // blendshapes derrière un fond noir -- les 4 contrôles du HUD restent accessibles
            // (dont l'icône "Visage détecté"). Le mesh overlay, lui, se dessine PAR-DESSUS ce fond
            // noir plutôt que d'être simplement masqué comme avant, pour permettre
            // keepMeshOverlayInPowerSave -- FaceMeshOverlay a un fond transparent (seuls les
            // points sont dessinés), il se superpose donc proprement.
            if (uiState.isPowerSaveActive) {
                PowerSaveOverlay()
                if (meshOverlayVisible) {
                    FaceMeshOverlay(
                        landmarks = trackingFrame.faceLandmarks,
                        imageWidthPx = trackingFrame.imageWidthPx,
                        imageHeightPx = trackingFrame.imageHeightPx,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                if (meshOverlayVisible) {
                    FaceMeshOverlay(
                        landmarks = trackingFrame.faceLandmarks,
                        imageWidthPx = trackingFrame.imageWidthPx,
                        imageHeightPx = trackingFrame.imageHeightPx,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Construit la liste à PARTIR de la sélection, pas en filtrant allBlendshapes par
                // la sélection -- sinon un blendshape coché mais jamais produit par MediaPipe
                // (ex. tongueOut, voir BlendshapeCatalog.unreliable) disparaît purement et
                // simplement du panneau au lieu d'y rester affiché figé à 0 (retour utilisateur du
                // 7 août 2026 : "elle devrait quand même apparaître à l'écran de debug, sans
                // bouger si on la coche").
                val blendshapeScoresByName = trackingFrame.allBlendshapes.associate { it.name to it.score }
                val selectedBlendshapeValues = uiState.selectedBlendshapeNames
                    .sorted()
                    .map { name -> name to (blendshapeScoresByName[name] ?: 0f) }
                BlendshapePanel(
                    values = selectedBlendshapeValues,
                    panelRotationDegrees = panelRotationDegrees,
                    // windowInsetsPadding AVANT le padding(16.dp) fixe : marge de sécurité
                    // supplémentaire pour le résidu de débordement du pivot de rotation (voir kdoc
                    // de BlendshapePanel) -- l'app n'a par ailleurs aucune gestion des insets
                    // système nulle part (edge-to-edge de facto vu compileSdk/targetSdk 37).
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
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
                    onSetTierOverride = { tier -> viewModel.setTierOverride(tier) },
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
                    onSetKeepMeshOverlayInPowerSave = { enabled -> viewModel.setKeepMeshOverlayInPowerSave(enabled) },
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
                Text(stringResource(R.string.camera_permission_rationale), color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.action_grant_camera_permission)) }
            }
        }
    }
}
