package com.guyiome.androidmocap.ui

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.guyiome.androidmocap.camera.CameraController
import com.guyiome.androidmocap.capabilities.DeviceCapabilities
import com.guyiome.androidmocap.capabilities.DeviceCapabilityDetector
import com.guyiome.androidmocap.network.IFacialMocapSender
import com.guyiome.androidmocap.network.VmcOscSender
import com.guyiome.androidmocap.network.getLocalIpAddress
import com.guyiome.androidmocap.sensors.DeviceOrientationTracker
import com.guyiome.androidmocap.settings.AppSettingsStore
import com.guyiome.androidmocap.settings.ConnectionSettingsStore
import com.guyiome.androidmocap.settings.ConnectionType
import com.guyiome.androidmocap.settings.DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT
import com.guyiome.androidmocap.settings.DEFAULT_POWER_SAVE_DELAY_SECONDS
import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.FaceLandmarkerHelper
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.guyiome.androidmocap.tracking.RotationMath
import com.guyiome.androidmocap.tracking.TrackingTier
import com.guyiome.androidmocap.tracking.TrackingTierSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * État "froid" : ne change qu'à des rythmes humains (interaction dans les réglages, connexion,
 * calibration...), jamais à chaque frame caméra -- volontairement séparé de [TrackingFrame] (qui
 * lui change à 20-60 Hz) pour que Compose puisse sauter la recomposition des écrans/composants qui
 * n'ont besoin que de cet état-ci quand seul le tracking progresse. Marqué [Immutable] : bien
 * qu'il contienne des types de collection (Set) que Compose ne considère pas stables par défaut,
 * on ne mute jamais ces collections en place (toujours remplacées via `.copy()`), donc c'est un
 * état réellement immuable d'un point de vue Compose.
 */
@Immutable
data class MainUiState(
    val tier: TrackingTier? = null,
    val capabilities: DeviceCapabilities? = null,
    val activeDelegateIsGpu: Boolean = false,
    // Choix fait dans l'écran de sélection dédié -- volontairement NON persisté (remis à zéro à
    // chaque lancement de l'app, comme demandé).
    val selectedBlendshapeNames: Set<String> = emptySet(),
    // VMC (connexion directe : on saisit l'IP du PC cible -- VTube Studio / Blender / Unity)
    val vmcEnabled: Boolean = false,
    val vmcTargetLabel: String = "",
    // iFacialMocap / MeowFace (le PC initie -- pratique pour VBridger, pas besoin de saisir d'IP ici)
    val localIpAddress: String = "",
    val iFacialMocapListening: Boolean = false,
    val iFacialMocapConnectedTo: String? = null,
    // Type de connexion choisi dans les réglages -- pilote le bouton de connexion unique de
    // l'écran principal (et l'IP VMC mémorisée, utilisée quand ce type est VMC).
    val connectionType: ConnectionType? = null,
    val savedVmcHost: String = "",
    // Calibration : pose de tête courante prise comme "zéro" pour compenser un téléphone
    // légèrement décalé/tourné par rapport au visage (contrainte physique de positionnement).
    val isCalibrated: Boolean = false,
    // Compte à rebours en cours avant capture de la calibration (secondes restantes), null si aucun.
    val calibrationCountdownSeconds: Int? = null,
    // Seuil (%) en dessous duquel l'alerte batterie faible s'affiche -- persisté, réglable dans les réglages.
    val lowBatteryThresholdPercent: Int = DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT,
    // Mode économie d'énergie : AUTORISE (persisté, réglable dans les réglages) la bascule
    // automatique après [powerSaveDelaySeconds] d'inactivité -- ne coupe rien tout de suite, voir
    // [isPowerSaveActive] pour l'état effectif.
    val powerSaveModeEnabled: Boolean = false,
    // Délai d'inactivité (secondes, aucun toucher de l'écran) avant bascule automatique --
    // persisté, réglable dans les réglages.
    val powerSaveDelaySeconds: Int = DEFAULT_POWER_SAVE_DELAY_SECONDS,
    // État effectif du mode éco à l'instant présent : coupe l'aperçu caméra affiché (l'analyse
    // continue), assombrit l'écran. Bascule seule après le délai ci-dessus, en ressort au moindre
    // toucher (voir [MainViewModel.onUserInteraction]) -- volontairement NON persisté, on redémarre
    // toujours en mode normal.
    val isPowerSaveActive: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * État "chaud" : mis à jour à chaque frame traitée par MediaPipe (jusqu'à 60 Hz sur le palier
 * OPTIMAL). Volontairement séparé de [MainUiState] -- seuls les composants qui en ont réellement
 * besoin (icône "visage détecté", panneau de blendshapes, diagnostics des réglages) le collectent,
 * plutôt que de forcer tout l'écran à se recomposer à ce rythme.
 */
data class TrackingFrame(
    val faceDetected: Boolean = false,
    // Liste complète des 52 blendshapes -- filtrée par [MainUiState.selectedBlendshapeNames] pour
    // l'affichage sur l'écran principal.
    val allBlendshapes: List<BlendshapeScore> = emptyList(),
    val inferenceTimeMs: Long = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _trackingFrame = MutableStateFlow(TrackingFrame())
    val trackingFrame: StateFlow<TrackingFrame> = _trackingFrame.asStateFlow()

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var cameraController: CameraController? = null
    private var vmcSender: VmcOscSender? = null
    private var iFacialMocapSender: IFacialMocapSender? = null
    private var deviceOrientationTracker: DeviceOrientationTracker? = null
    private val connectionSettingsStore = ConnectionSettingsStore(application)
    private val appSettingsStore = AppSettingsStore(application)

    // Calibration de la pose de tête neutre. Tout se compose en espace matriciel (voir
    // RotationMath) plutôt qu'en soustrayant des angles d'Euler déjà décomposés -- une simple
    // soustraction n'est valable que pour de petites rotations et donnait des axes inversés une
    // fois calibré téléphone à l'horizontale plutôt qu'à la verticale.
    // Pose de tête brute au moment de la calibration : devient le nouveau "zéro" de l'avatar.
    private var headRotationReference: FloatArray = RotationMath.IDENTITY_3X3.copyOf()
    private var lastRawHeadRotationMatrix: FloatArray = RotationMath.IDENTITY_3X3.copyOf()
    // Rotation du téléphone au moment de la calibration -- sert à calculer, à chaque frame, de
    // combien le téléphone a tourné TOUT SEUL depuis (mouvement du support/de la main, indépendant
    // du mouvement de la tête), pour l'annuler de la pose de tête envoyée.
    private var deviceRotationReference: FloatArray = RotationMath.IDENTITY_3X3.copyOf()
    private var calibrationCountdownJob: Job? = null
    // Minuteur d'inactivité avant bascule automatique en mode éco -- annulé/reprogrammé à chaque
    // toucher de l'écran (voir onUserInteraction) et à chaque changement de réglage.
    private var powerSaveTimerJob: Job? = null

    init {
        // Lu dès le lancement (pas seulement une fois la permission caméra accordée) : le choix
        // de type de connexion et la dernière IP VMC doivent être disponibles pour le bouton de
        // connexion de l'écran principal dès que possible.
        viewModelScope.launch {
            connectionSettingsStore.connectionType.collect { type ->
                _uiState.update { it.copy(connectionType = type) }
            }
        }
        viewModelScope.launch {
            connectionSettingsStore.vmcHost.collect { host ->
                _uiState.update { it.copy(savedVmcHost = host ?: "") }
            }
        }
        viewModelScope.launch {
            appSettingsStore.lowBatteryThresholdPercent.collect { percent ->
                _uiState.update { it.copy(lowBatteryThresholdPercent = percent) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.powerSaveModeEnabled.collect { enabled ->
                _uiState.update { it.copy(powerSaveModeEnabled = enabled) }
                if (enabled) {
                    schedulePowerSaveTimer()
                } else {
                    powerSaveTimerJob?.cancel()
                    if (_uiState.value.isPowerSaveActive) exitPowerSave()
                }
            }
        }
        viewModelScope.launch {
            appSettingsStore.powerSaveDelaySeconds.collect { seconds ->
                _uiState.update { it.copy(powerSaveDelaySeconds = seconds) }
                // Reprogramme avec le nouveau délai -- sans effet si le mode n'est pas autorisé
                // ou déjà actif (voir schedulePowerSaveTimer).
                schedulePowerSaveTimer()
            }
        }
    }

    /** À appeler une fois la permission caméra accordée. */
    fun initializeTracking(lifecycleOwner: LifecycleOwner) {
        val context = getApplication<Application>()
        val capabilities = DeviceCapabilityDetector.detect(context)
        val tierConfig = TrackingTierSelector.select(capabilities)

        _uiState.update {
            it.copy(
                tier = tierConfig.tier,
                capabilities = capabilities,
                localIpAddress = getLocalIpAddress() ?: "indisponible",
            )
        }

        val helper = FaceLandmarkerHelper(
            context = context,
            tierConfig = tierConfig,
            onResult = ::handleTrackingResult,
            // Signal de "frame terminé" (par timestamp, pas par objet MPImage -- voir le
            // commentaire sur FaceLandmarkerHelper.onFrameProcessed) -- permet à CameraController
            // de savoir quand un bitmap de son pool de réutilisation redevient libre, sans jamais
            // écraser une image encore en cours d'analyse côté MediaPipe.
            onFrameProcessed = { frameTimeMs -> cameraController?.releaseFrame(frameTimeMs) },
            onError = ::handleError,
        )
        helper.setup()
        faceLandmarkerHelper = helper

        _uiState.update { it.copy(activeDelegateIsGpu = helper.activeDelegateIsGpu) }

        cameraController = CameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onFrame = { image, timestampMs -> faceLandmarkerHelper?.detectAsync(image, timestampMs) },
            // Débit cible du palier choisi (voir TrackingTierSelector) -- jusqu'ici jamais
            // appliqué : les frames en trop sont désormais ignorées avant toute allocation.
            initialTargetFps = tierConfig.targetFps,
            onError = ::handleError,
        )

        deviceOrientationTracker = DeviceOrientationTracker(context).also { it.start() }
    }

    fun startCamera(previewView: PreviewView) {
        cameraController?.start(previewView)
        // Réapplique l'état courant du mode économie d'énergie (chargé depuis les réglages,
        // potentiellement avant que cameraController n'existe -- voir le collecteur dans init).
        cameraController?.setPreviewEnabled(!_uiState.value.isPowerSaveActive)
    }

    private fun handleTrackingResult(result: FaceTrackingResult) {
        lastRawHeadRotationMatrix = result.headRotationMatrix

        // Pas de calibration manuelle encore faite : on prend la toute première pose détectée
        // comme "zéro" par défaut, plutôt que d'envoyer une pose brute (relative à l'angle brut
        // de la caméra) visiblement incohérente à l'avatar tant que l'utilisateur n'a pas cliqué
        // sur "Mise à zéro".
        if (!_uiState.value.isCalibrated && result.faceDetected) {
            performCalibration()
        }

        // Rotation du téléphone lui-même depuis la calibration, dans son propre repère.
        val deviceDelta = deviceOrientationTracker?.rotationDeltaMatrix(deviceRotationReference)
            ?: RotationMath.IDENTITY_3X3

        // Annule le mouvement du téléphone puis exprime le résultat relatif à la pose de
        // référence capturée à la calibration (= le nouveau "zéro") -- composition extraite dans
        // RotationMath pour être testable indépendamment du ViewModel, voir
        // RotationMath.composeCalibratedEuler.
        val calibratedEuler = RotationMath.composeCalibratedEuler(
            headRotationMatrix = result.headRotationMatrix,
            deviceDeltaMatrix = deviceDelta,
            headRotationReference = headRotationReference,
        )

        val calibrated = result.copy(headEulerDegrees = calibratedEuler)

        _trackingFrame.update {
            it.copy(
                faceDetected = calibrated.faceDetected,
                allBlendshapes = calibrated.blendshapes,
                inferenceTimeMs = calibrated.inferenceTimeMs,
            )
        }
        vmcSender?.send(calibrated)
        iFacialMocapSender?.send(calibrated)
    }

    /**
     * Bouton "Mise à zéro" : déclenche un compte à rebours (le temps de tendre le bras pour
     * cliquer et reprendre position bien en face de la caméra) avant de capturer la pose de tête
     * courante comme nouveau point neutre de l'avatar.
     */
    fun startCalibrationCountdown(seconds: Int = 5) {
        calibrationCountdownJob?.cancel()
        calibrationCountdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(calibrationCountdownSeconds = remaining) }
                delay(1000)
            }
            _uiState.update { it.copy(calibrationCountdownSeconds = null) }
            performCalibration()
        }
    }

    /**
     * Capture la pose de tête courante comme nouveau point neutre de l'avatar. Utile quand le
     * téléphone est posé sur un support un peu décalé/tourné par rapport au visage -- sans ça,
     * l'avatar part "en sucette" dès qu'on tourne la tête, même légèrement. Aussi appelée
     * automatiquement sur la toute première pose détectée (voir [handleTrackingResult]).
     */
    private fun performCalibration() {
        headRotationReference = lastRawHeadRotationMatrix.copyOf()
        deviceRotationReference = deviceOrientationTracker?.snapshotRotationMatrix() ?: deviceRotationReference
        _uiState.update { it.copy(isCalibrated = true) }
    }

    private fun handleError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    // --- Sélection des blendshapes affichées sur l'écran principal (non persistée) ---

    fun toggleBlendshapeSelection(name: String) {
        _uiState.update { state ->
            val selection = state.selectedBlendshapeNames
            state.copy(selectedBlendshapeNames = if (name in selection) selection - name else selection + name)
        }
    }

    // --- Réglages généraux (persistés) ---

    fun setLowBatteryThresholdPercent(percent: Int) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setLowBatteryThresholdPercent(percent) }
    }

    /**
     * Toggle réglages : autorise ou non la bascule automatique en mode éco après un délai
     * d'inactivité (voir [MainUiState.powerSaveDelaySeconds]) -- l'effet réel (programmer le
     * minuteur, ou en sortir immédiatement si actif) est géré par le collecteur dans [init].
     */
    fun setPowerSaveModeEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setPowerSaveModeEnabled(enabled) }
    }

    /** Délai d'inactivité (secondes) avant bascule automatique -- persisté, réglable dans les réglages. */
    fun setPowerSaveDelaySeconds(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setPowerSaveDelaySeconds(seconds) }
    }

    /**
     * (Re)programme le minuteur d'inactivité avant bascule automatique en mode éco -- annule tout
     * minuteur en cours. Ne fait rien si le mode n'est pas autorisé dans les réglages, ou si le
     * mode éco est déjà actif (on attend alors un toucher pour en ressortir, voir [onUserInteraction]).
     */
    private fun schedulePowerSaveTimer() {
        powerSaveTimerJob?.cancel()
        if (!_uiState.value.powerSaveModeEnabled || _uiState.value.isPowerSaveActive) return
        val delaySeconds = _uiState.value.powerSaveDelaySeconds
        powerSaveTimerJob = viewModelScope.launch {
            delay(delaySeconds * 1000L)
            enterPowerSave()
        }
    }

    private fun enterPowerSave() {
        _uiState.update { it.copy(isPowerSaveActive = true) }
        cameraController?.setPreviewEnabled(false)
    }

    private fun exitPowerSave() {
        _uiState.update { it.copy(isPowerSaveActive = false) }
        cameraController?.setPreviewEnabled(true)
    }

    /**
     * À appeler à chaque toucher de l'écran (voir le détecteur global dans [MainScreen]) : sort
     * du mode éco si actif, et dans tous les cas relance le compte du délai d'inactivité -- le
     * moindre toucher reporte la prochaine bascule automatique.
     */
    fun onUserInteraction() {
        if (_uiState.value.isPowerSaveActive) exitPowerSave()
        schedulePowerSaveTimer()
    }

    // --- Type de connexion (réglages) : pilote le bouton unique de l'écran principal ---

    /** Choix persisté depuis l'écran réglages (VMC ou iFacialMocap) -- lu au prochain lancement aussi. */
    fun setConnectionType(type: ConnectionType) {
        viewModelScope.launch(Dispatchers.IO) { connectionSettingsStore.setConnectionType(type) }
    }

    /**
     * Bouton de connexion de l'écran principal : connecte ou déconnecte selon le type choisi
     * dans les réglages. Pour VMC, réutilise la dernière IP enregistrée -- si aucune n'a encore
     * été saisie une fois dans les réglages, rien à faire.
     */
    fun toggleActiveConnection() {
        when (_uiState.value.connectionType) {
            ConnectionType.VMC -> {
                if (_uiState.value.vmcEnabled) {
                    disconnectVmcTarget()
                } else {
                    val host = _uiState.value.savedVmcHost
                    if (host.isBlank()) {
                        _uiState.update { it.copy(errorMessage = "Renseigne d'abord une IP VMC dans les réglages.") }
                    } else {
                        connectVmcTarget(host)
                    }
                }
            }
            ConnectionType.IFACIALMOCAP -> {
                if (_uiState.value.iFacialMocapListening) stopIFacialMocapListening() else startIFacialMocapListening()
            }
            null -> {
                _uiState.update { it.copy(errorMessage = "Choisis un type de connexion dans les réglages.") }
            }
        }
    }

    // --- VMC : connexion directe vers VTube Studio / Blender / Unity ---

    /** Configure la cible VMC (IP du PC qui fait tourner VTube Studio / Blender / Unity) et l'active. */
    fun connectVmcTarget(hostText: String, port: Int = VmcOscSender.DEFAULT_PORT) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(hostText)
                vmcSender?.close()
                vmcSender = VmcOscSender(address, port)
                _uiState.update { it.copy(vmcEnabled = true, vmcTargetLabel = "$hostText:$port", errorMessage = null) }
                // Mémorisée pour que le bouton de connexion de l'écran principal puisse s'y
                // reconnecter directement, sans ressaisir l'IP.
                connectionSettingsStore.setVmcHost(hostText)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Adresse IP invalide ou injoignable : $hostText") }
            }
        }
    }

    fun disconnectVmcTarget() {
        vmcSender?.close()
        vmcSender = null
        _uiState.update { it.copy(vmcEnabled = false, vmcTargetLabel = "") }
    }

    // --- iFacialMocap / MeowFace : lecture native par VBridger (et VTube Studio, VSeeFace...) ---

    /** Se met à l'écoute du handshake iFacialMocap sur le port 49983 -- c'est le PC qui initie. */
    fun startIFacialMocapListening() {
        val sender = iFacialMocapSender ?: IFacialMocapSender { connectedTo ->
            _uiState.update { it.copy(iFacialMocapConnectedTo = connectedTo) }
        }.also { iFacialMocapSender = it }
        sender.startListening()
        _uiState.update { it.copy(iFacialMocapListening = true) }
    }

    fun stopIFacialMocapListening() {
        iFacialMocapSender?.stopListening()
        _uiState.update { it.copy(iFacialMocapListening = false, iFacialMocapConnectedTo = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraController?.stop()
        faceLandmarkerHelper?.close()
        vmcSender?.close()
        iFacialMocapSender?.stopListening()
        deviceOrientationTracker?.stop()
        calibrationCountdownJob?.cancel()
        powerSaveTimerJob?.cancel()
    }
}
