package com.guyiome.androidmocap.ui

import android.app.Activity
import android.content.Intent
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.sensors.BatteryMonitor
import com.guyiome.androidmocap.sensors.IconOrientationTracker
import com.guyiome.androidmocap.tracking.EyeLandmarkIndices
import com.guyiome.androidmocap.tracking.eyeAspectRatioFromLandmarks

/**
 * Écran unique de l'app : preview caméra pleine page + bandeau d'icônes minimal ([MainHud]) +
 * menu de réglages ([SettingsScreen]) affiché par-dessus à la demande, qui ouvre à son tour l'une
 * de ses six catégories ([DisplaySettingsScreen], [BlendshapesScreen], [ConnectionSettingsScreen],
 * [ComfortSettingsScreen], [ExperimentalFeaturesScreen], [AdvancedSettingsScreen] -- regroupées par
 * intention utilisateur plutôt que par couche technique, refonte des menus).
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
    // le fond caméra live y est dessiné en interne (drawCameraBackground), ce Compose AndroidView
    // n'est qu'un hôte. Toujours créé (même coût que previewView ci-dessus, symétrique) mais
    // seulement attaché à ArCoreHeadPoseTracker quand uiState.usingArCoreCameraSource est actif,
    // voir plus bas.
    val glSurfaceView = remember { GLSurfaceView(context) }
    // Menu des réglages + ses six catégories (refonte des menus, regroupement par intention
    // utilisateur) -- un booléen par écran conditionnel, chacun ouvert directement depuis
    // SettingsScreen.
    var showSettings by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showBlendshapes by remember { mutableStateOf(false) }
    var showConnectionSettings by remember { mutableStateOf(false) }
    var showComfortSettings by remember { mutableStateOf(false) }
    var showExperimentalFeatures by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showTongueCalibration by remember { mutableStateOf(false) }
    var showVtsFormulas by remember { mutableStateOf(false) }
    var iconRotationDegrees by remember { mutableFloatStateOf(0f) }
    var batteryPercent by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(true) }

    // Panneau blendshapes : pas de rotation continue (illisible pendant la transition), juste un
    // pas de 90° selon que le téléphone est tenu à la verticale ou à l'horizontale. Délègue à
    // snapToRotationBucket() (RotationBucket.kt, même package) plutôt qu'à une logique ad hoc
    // locale -- l'ancienne version avait un trou (135°-225°, tête en bas, tombait à tort sur 0f au
    // lieu de 180f), corrigé en même temps que le correctif de rotation caméra (revue technique,
    // points 1/15/20). Reste en convention brute OrientationEventListener (horaire) -- rotation
    // cosmétique Compose, sans rapport avec la convention Surface.ROTATION_* utilisée côté caméra.
    val panelRotationDegrees = when (snapToRotationBucket(iconRotationDegrees.toInt())) {
        90 -> -90f
        270 -> 90f
        180 -> 180f
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
            // technique, point 13). Le fond caméra live du GLSurfaceView est dessiné en interne par
            // ArCoreHeadPoseTracker.onDrawFrame (drawCameraBackground) -- le GLSurfaceView n'est
            // qu'un hôte AndroidView ici, comme previewView pour CameraX.
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

            // Overlay du mesh de tracking -- affiché sur choix explicite de l'utilisateur (réglage
            // Affichage & confort), même règle sur les deux paliers depuis l'ajout du fond caméra
            // live ARCore (avant : forcé sur ARCore, faute d'aperçu live sur ce palier). Masqué par
            // défaut en mode éco comme le reste de l'aperçu, sauf si l'utilisateur a choisi de le
            // garder visible même en éco -- voir computeMeshOverlayVisible.
            val meshOverlayVisible = computeMeshOverlayVisible(
                faceMeshOverlayEnabled = uiState.faceMeshOverlayEnabled,
                isPowerSaveActive = uiState.isPowerSaveActive,
                keepMeshOverlayInPowerSave = uiState.keepMeshOverlayInPowerSave,
            )

            // Mode économie d'énergie : masque l'aperçu (CameraX : setPreviewEnabled ; ARCore :
            // ArCoreHeadPoseTracker.setBackgroundRenderingEnabled) et le panneau blendshapes
            // derrière un fond noir -- les 4 contrôles du HUD restent accessibles
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
                // Trié une fois par changement de sélection (vitesse humaine), pas à chaque frame
                // (relecture globale du 7 août 2026, point 22) -- uiState.selectedBlendshapeNames
                // ne change qu'à l'interaction utilisateur, contrairement à trackingFrame ci-dessus.
                val sortedSelectedNames = remember(uiState.selectedBlendshapeNames) {
                    uiState.selectedBlendshapeNames.sorted()
                }
                val selectedBlendshapeValues = sortedSelectedNames
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

            // Masqué pendant la calibration langue tirée : sinon son bandeau d'icônes (ancré
            // bottom-center comme le bouton d'action de TongueCalibrationScreen) entre en collision
            // visuelle avec lui, surtout à l'horizontale -- constaté sur device.
            if (!showTongueCalibration) {
                MainHud(
                    uiState = uiState,
                    faceDetected = trackingFrame.faceDetected,
                    iconRotationDegrees = iconRotationDegrees,
                    onCalibrate = { viewModel.startCalibrationCountdown() },
                    onToggleConnection = { viewModel.toggleActiveConnection() },
                    onOpenSettings = { showSettings = true },
                    onOpenExperimental = { showExperimentalFeatures = true },
                )
            }

            // Masqué pendant la calibration langue tirée, même raison que showExperimentalFeatures
            // plus bas -- sinon ce menu (encore plus "sous" dans la pile de navigation) bleedait lui
            // aussi à travers l'écran de calibration désormais transparent.
            if (showSettings && !showTongueCalibration) {
                SettingsScreen(
                    uiState = uiState,
                    faceDetected = trackingFrame.faceDetected,
                    onClose = { showSettings = false },
                    onOpenDisplay = { showDisplaySettings = true },
                    onOpenBlendshapes = { showBlendshapes = true },
                    onOpenConnection = { showConnectionSettings = true },
                    onOpenComfort = { showComfortSettings = true },
                    onOpenExperimental = { showExperimentalFeatures = true },
                    onOpenAdvanced = { showAdvancedSettings = true },
                )
            }

            if (showAdvancedSettings) {
                var hasLogsToShare by remember { mutableStateOf(false) }
                LaunchedEffect(showAdvancedSettings) { hasLogsToShare = viewModel.hasLogsToShare() }
                AdvancedSettingsScreen(
                    uiState = uiState,
                    faceDetected = trackingFrame.faceDetected,
                    inferenceTimeMs = trackingFrame.inferenceTimeMs,
                    // Diagnostic EAR (revue technique, point 28) -- voir kdoc dans
                    // AdvancedSettingsScreen. Recalculé seulement quand la liste de landmarks change
                    // réellement (elle est déjà stable/vide hors overlay du mesh activé), pas de
                    // remember nécessaire ici : trackingFrame change de toute façon à 20-60 Hz.
                    eyeAspectRatioGroupA = eyeAspectRatioFromLandmarks(trackingFrame.faceLandmarks, EyeLandmarkIndices.RIGHT_EYE),
                    eyeAspectRatioGroupB = eyeAspectRatioFromLandmarks(trackingFrame.faceLandmarks, EyeLandmarkIndices.LEFT_EYE),
                    logLevel = uiState.logLevel,
                    hasLogsToShare = hasLogsToShare,
                    onClose = { showAdvancedSettings = false },
                    onSetTierOverride = { tier -> viewModel.setTierOverride(tier) },
                    onSetDebugForceArCoreUnavailable = { enabled -> viewModel.setDebugForceArCoreUnavailable(enabled) },
                    onSetDebugForceGpuUnavailable = { enabled -> viewModel.setDebugForceGpuUnavailable(enabled) },
                    onSetDebugThermalOverride = { override -> viewModel.setDebugThermalOverride(override) },
                    onResetDebugOverrides = { viewModel.resetDebugOverrides() },
                    onSetLogLevel = { level -> viewModel.setLogLevel(level) },
                    onShareLogs = {
                        viewModel.buildShareLogsIntent()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    },
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
                    onConnectVts = { host, port -> viewModel.connectVtsTarget(host, port) },
                    onDisconnectVts = { viewModel.disconnectVtsTarget() },
                    onForgetVtsToken = { viewModel.forgetVtsAuthToken() },
                    onSetUseVBridgerTranslation = { enabled -> viewModel.setVtsUseVBridgerTranslation(enabled) },
                    onOpenVtsFormulas = { showVtsFormulas = true },
                )
            }

            if (showVtsFormulas) {
                VtsFormulasScreen(
                    useVBridgerTranslation = uiState.vtsUseVBridgerTranslation,
                    onClose = { showVtsFormulas = false },
                )
            }

            if (showDisplaySettings) {
                DisplaySettingsScreen(
                    uiState = uiState,
                    onClose = { showDisplaySettings = false },
                    onSetFaceMeshOverlay = { enabled -> viewModel.setFaceMeshOverlayEnabled(enabled) },
                    onSetKeepMeshOverlayInPowerSave = { enabled -> viewModel.setKeepMeshOverlayInPowerSave(enabled) },
                    onSetMirrorMode = { enabled -> viewModel.setMirrorModeEnabled(enabled) },
                )
            }

            if (showComfortSettings) {
                ComfortSettingsScreen(
                    uiState = uiState,
                    onClose = { showComfortSettings = false },
                    onSetLowBatteryThreshold = { percent -> viewModel.setLowBatteryThresholdPercent(percent) },
                    onSetPowerSaveMode = { enabled -> viewModel.setPowerSaveModeEnabled(enabled) },
                    onSetPowerSaveDelay = { seconds -> viewModel.setPowerSaveDelaySeconds(seconds) },
                    onSetAppLanguage = { language -> viewModel.setAppLanguage(language) },
                )
            }

            // Masqué pendant la calibration langue tirée (!showTongueCalibration) -- cet écran
            // n'a plus de fond opaque (refonte cosmétique, caméra visible), donc ExperimentalFeaturesScreen
            // resterait visible en transparence dessous sinon (bug réel constaté sur device, menu et
            // calibration superposés de façon illisible). showExperimentalFeatures reste vrai tel
            // quel pendant ce temps -- réapparaît automatiquement une fois la calibration fermée,
            // c'est le mécanisme de "retour au menu" voulu.
            if (showExperimentalFeatures && !showTongueCalibration) {
                ExperimentalFeaturesScreen(
                    uiState = uiState,
                    onClose = { showExperimentalFeatures = false },
                    onSetTongueOutDetectionEnabled = { enabled -> viewModel.setTongueOutDetectionEnabled(enabled) },
                    onOpenTongueCalibration = { showTongueCalibration = true },
                )
            }

            if (showTongueCalibration) {
                TongueCalibrationScreen(
                    phase = uiState.tongueCalibrationPhase,
                    secondsRemaining = uiState.tongueCalibrationSecondsRemaining,
                    isCalibrated = uiState.tongueReferencesCalibrated,
                    recordingDurationMs = uiState.tongueCalibrationRecordingDurationMs,
                    classificationMargin = uiState.tongueClassificationMargin,
                    iconRotationDegrees = iconRotationDegrees,
                    onStartCalibration = { viewModel.startTongueCalibration() },
                    onCancel = { viewModel.cancelTongueCalibration() },
                    onSetRecordingDurationMs = { durationMs -> viewModel.setTongueCalibrationRecordingDurationMs(durationMs) },
                    onSetClassificationMargin = { margin -> viewModel.setTongueClassificationMargin(margin) },
                    onClose = { showTongueCalibration = false },
                )
            }

            if (showBlendshapes) {
                BlendshapesScreen(
                    selectedNames = uiState.selectedBlendshapeNames,
                    onToggle = { name -> viewModel.toggleBlendshapeSelection(name) },
                    persistSelectionEnabled = uiState.persistBlendshapeSelectionEnabled,
                    onSetPersistSelection = { enabled -> viewModel.setPersistBlendshapeSelectionEnabled(enabled) },
                    onDeselectAll = { viewModel.deselectAllBlendshapes() },
                    onResetWeights = { viewModel.resetBlendshapeWeights() },
                    onClose = { showBlendshapes = false },
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
