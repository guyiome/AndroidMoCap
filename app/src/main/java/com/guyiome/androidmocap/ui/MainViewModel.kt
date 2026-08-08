package com.guyiome.androidmocap.ui

import android.app.Application
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.camera.CameraController
import com.guyiome.androidmocap.capabilities.DeviceCapabilities
import com.guyiome.androidmocap.capabilities.DeviceCapabilityDetector
import com.guyiome.androidmocap.network.IFacialMocapSender
import com.guyiome.androidmocap.network.VTubeStudioConnectionState
import com.guyiome.androidmocap.network.VTubeStudioSender
import com.guyiome.androidmocap.network.VmcOscSender
import com.guyiome.androidmocap.network.getLocalIpAddress
import com.guyiome.androidmocap.sensors.DeviceOrientationTracker
import com.guyiome.androidmocap.settings.AppSettingsStore
import com.guyiome.androidmocap.settings.ConnectionSettingsStore
import com.guyiome.androidmocap.settings.ConnectionType
import com.guyiome.androidmocap.settings.DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT
import com.guyiome.androidmocap.settings.DEFAULT_POWER_SAVE_DELAY_SECONDS
import com.guyiome.androidmocap.tracking.ArCoreHeadPoseTracker
import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.CalibrationAnomalyState
import com.guyiome.androidmocap.tracking.FaceLandmarkerHelper
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.guyiome.androidmocap.tracking.REST_VARIANCE_THRESHOLD
import com.guyiome.androidmocap.tracking.RotationMath
import com.guyiome.androidmocap.tracking.ThermalThrottleState
import com.guyiome.androidmocap.tracking.maxAbsEulerDegrees
import com.guyiome.androidmocap.tracking.meanAbsoluteBlendshapeDelta
import com.guyiome.androidmocap.tracking.TierConfig
import com.guyiome.androidmocap.tracking.next
import com.guyiome.androidmocap.tracking.TrackingTier
import com.guyiome.androidmocap.tracking.TrackingTierSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    // Force un palier au lieu de la sélection automatique -- null = automatique (comportement par
    // défaut). Outil de diagnostic (voir DiagnosticsScreen), persisté mais appliqué seulement au
    // prochain lancement de l'app (voir initializeTracking) : changer ce réglage ne reconstruit
    // pas le pipeline caméra/MediaPipe à chaud.
    val tierOverride: TrackingTier? = null,
    val capabilities: DeviceCapabilities? = null,
    val activeDelegateIsGpu: Boolean = false,
    // Choix fait dans l'écran de sélection dédié -- volontairement NON persisté (remis à zéro à
    // chaque lancement de l'app, comme demandé).
    val selectedBlendshapeNames: Set<String> = emptySet(),
    // VMC (connexion directe : on saisit l'IP du PC cible -- Blender / Unity, pas VTube Studio,
    // voir revue technique point 39)
    val vmcEnabled: Boolean = false,
    val vmcTargetLabel: String = "",
    // Vrai pendant la résolution DNS/construction du socket (voir connectVmcTarget) -- généralement
    // très bref (IP déjà résolue, pas de vrai lookup réseau) mais affiché quand même pour rester
    // cohérent avec les deux autres types de connexion (revue technique, point 40).
    val vmcConnecting: Boolean = false,
    // iFacialMocap / MeowFace (le PC initie -- pratique pour VBridger, pas besoin de saisir d'IP ici)
    val localIpAddress: String = "",
    val iFacialMocapListening: Boolean = false,
    val iFacialMocapConnectedTo: String? = null,
    // Type de connexion choisi dans les réglages -- pilote le bouton de connexion unique de
    // l'écran principal (et l'IP VMC mémorisée, utilisée quand ce type est VMC).
    val connectionType: ConnectionType? = null,
    val savedVmcHost: String = "",
    // VTube Studio (API Plugin, point 39) : intégration directe alternative à VMC, que VTube
    // Studio ne reçoit vraisemblablement pas nativement en entrée (voir revue technique).
    // Contrairement à VMC (UDP, immédiat), la connexion passe par plusieurs étapes asynchrones
    // (socket, popup d'autorisation utilisateur, création des paramètres) avant d'être utilisable
    // -- voir VTubeStudioConnectionState pour le détail de ces étapes.
    val vtsTargetLabel: String = "",
    val vtsConnectionState: VTubeStudioConnectionState = VTubeStudioConnectionState.Disconnected,
    val savedVtsHost: String = "",
    // Calibration : pose de tête courante prise comme "zéro" pour compenser un téléphone
    // légèrement décalé/tourné par rapport au visage (contrainte physique de positionnement).
    val isCalibrated: Boolean = false,
    // Compte à rebours en cours avant capture de la calibration (secondes restantes), null si aucun.
    val calibrationCountdownSeconds: Int? = null,
    // Anomalie de calibrage détectée (voir tracking/CalibrationAnomaly.kt, revue technique point
    // 19) : sticky, remis à faux uniquement par performCalibration(). Purement informatif --
    // pilote seulement la teinte du bouton de calibrage dans MainHud, aucune action automatique.
    val calibrationAnomalyFlagged: Boolean = false,
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
    // Superpose les points du mesh facial (478) sur l'aperçu caméra -- persisté, réglable dans les
    // réglages. Option activable indépendamment du futur aperçu 3D d'avatar (piste séparée, pas
    // encore implémentée).
    val faceMeshOverlayEnabled: Boolean = false,
    // Garde l'overlay du mesh visible même en mode économie d'énergie (tous paliers confondus) --
    // désactivé par défaut, comportement historique inchangé (l'overlay disparaît en mode éco)
    // tant que l'utilisateur ne l'active pas lui-même. Voir ui/MeshOverlayVisibility.kt.
    val keepMeshOverlayInPowerSave: Boolean = false,
    // true si le palier OPTIMAL pilote la caméra via ARCore (voir ArCoreHeadPoseTracker) plutôt
    // que CameraX -- dans ce cas MainScreen affiche l'overlay du mesh sur fond neutre à la place
    // de l'aperçu caméra live (voir revue technique, point 13 : ARCore et CameraX ne peuvent pas
    // se partager la caméra). Décidé dans initializeTracking() selon TierConfig.useArCorePose,
    // peut repasser à false en cours de route si ARCore s'avère indisponible sur l'appareil malgré
    // le palier choisi (voir ArCoreHeadPoseTracker.onUnavailable) -- l'app replie alors
    // silencieusement sur CameraX. Non persisté, dérivé à l'exécution.
    val usingArCoreCameraSource: Boolean = false,
    // Mémorise (ou non) la sélection de blendshapes affichés d'une session à l'autre -- désactivé
    // par défaut, comportement historique inchangé tant que l'utilisateur ne l'active pas
    // lui-même. Voir rapport technique, point 18.
    val persistBlendshapeSelectionEnabled: Boolean = false,
    // Débit réduit en réponse à une chauffe détectée (voir ThermalThrottleState, startThermalPolling)
    // -- non persisté, dérivé à l'exécution, remonte tout seul dès que la chauffe retombe.
    val isThermalThrottling: Boolean = false,
    // Devient vrai (et le reste pour le reste de la session) après une chauffe persistante -- signal
    // purement informatif affiché en diagnostic, à côté du sélecteur de palier manuel : jamais
    // appliqué automatiquement, rétrograder le palier reste un choix explicite de l'utilisateur.
    val thermalDowngradeSuggested: Boolean = false,
    // Mocks de debug (panneau caché de DiagnosticsScreen, voir revue technique point 35) --
    // permettent d'exercer des chemins de code qu'un appareil de test donné (ex. haut de gamme,
    // thermiquement irréprochable) ne peut pas naturellement déclencher.
    //
    // Persistés (comme tierOverride), lus une seule fois au lancement (initializeTracking) : leur
    // effet ne s'applique qu'au prochain redémarrage complet de l'app, le pipeline caméra/MediaPipe
    // n'étant jamais reconstruit à chaud.
    val debugForceArCoreUnavailable: Boolean = false,
    val debugForceGpuUnavailable: Boolean = false,
    // Mock thermique : tri-état (null = capteur réel/automatique, true = forcer throttling, false =
    // forcer non-throttling) -- lu à chaque sondage (startThermalPolling), effet immédiat.
    // Volontairement NON persisté (absent d'AppSettingsStore) : bascule de session active, pas un
    // réglage de lancement -- repart toujours à null (automatique) au prochain démarrage de l'app.
    val debugThermalOverride: Boolean? = null,
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
    // Liste des blendshapes réellement produits par MediaPipe (peut exclure tongueOut, jamais
    // restitué en pratique -- voir BlendshapeCatalog). MainScreen part de
    // [MainUiState.selectedBlendshapeNames], pas de cette liste, pour construire l'affichage --
    // un blendshape coché mais absent d'ici reste affiché figé à 0 plutôt que de disparaître.
    val allBlendshapes: List<BlendshapeScore> = emptyList(),
    val inferenceTimeMs: Long = 0,
    // Points du mesh facial (478, coordonnées normalisées) -- toujours peuplé (coût négligeable),
    // affiché uniquement si [MainUiState.faceMeshOverlayEnabled] est actif (voir MainScreen).
    val faceLandmarks: List<Pair<Float, Float>> = emptyList(),
    // Dimensions de l'image analysée (espace de normalisation de [faceLandmarks]) -- nécessaires à
    // FaceMeshOverlay/LandmarkProjection pour un placement correct quel que soit le ratio écran vs
    // caméra. 0 tant qu'aucune frame n'a été traitée. Voir point 27 de la revue technique.
    val imageWidthPx: Int = 0,
    val imageHeightPx: Int = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "MainViewModel"

        // Cadence du sondage de throttling thermique (voir startThermalPolling) -- doit rester
        // cohérente avec ThermalThrottleState.SUSTAINED_THROTTLE_TICKS (12 sondages consécutifs,
        // soit environ une minute à ce rythme) : les deux constantes sont documentées séparément
        // plutôt que couplées techniquement, pour garder ThermalThrottleState pure et testable en
        // JVM sans dépendre d'un vrai délai d'exécution.
        private const val THERMAL_POLL_INTERVAL_MS = 5000L
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _trackingFrame = MutableStateFlow(TrackingFrame())
    val trackingFrame: StateFlow<TrackingFrame> = _trackingFrame.asStateFlow()

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var cameraController: CameraController? = null
    // Mutuellement exclusif avec cameraController -- l'un des deux est construit dans
    // initializeTracking() selon TierConfig.useArCorePose, jamais les deux à la fois. Les
    // callbacks qui doivent router vers "celui qui est actif" (releaseFrame, teardown) appellent
    // les deux via ?. plutôt que de tester usingArCoreCameraSource : au plus un des deux fait
    // réellement quelque chose, l'autre est un no-op gratuit sur null.
    private var arCoreHeadPoseTracker: ArCoreHeadPoseTracker? = null
    private var vmcSender: VmcOscSender? = null
    private var iFacialMocapSender: IFacialMocapSender? = null
    private var vtubeStudioSender: VTubeStudioSender? = null
    // Jeton d'authentification VTube Studio courant (voir ConnectionSettingsStore.vtsAuthToken) --
    // gardé en mémoire ici plutôt que dans MainUiState (jamais affiché à l'utilisateur), pour être
    // transmis au prochain VTubeStudioSender construit sans redemander le popup d'autorisation.
    @Volatile private var savedVtsAuthToken: String? = null
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
    // Dernière pose de tête reçue d'ArCoreHeadPoseTracker (callback appelé depuis le thread GL de
    // son GLSurfaceView, donc différent du thread de callback MediaPipe qui lit ce champ dans
    // handleTrackingResult -- @Volatile suffit ici, on ne fait qu'échanger la référence du tableau
    // en entier, jamais le muter en place). null tant qu'ARCore n'a pas encore émis de pose, ou si
    // ARCore n'est pas la source active.
    @Volatile private var latestArCoreHeadRotationMatrix: FloatArray? = null
    // Rotation du téléphone au moment de la calibration -- sert à calculer, à chaque frame, de
    // combien le téléphone a tourné TOUT SEUL depuis (mouvement du support/de la main, indépendant
    // du mouvement de la tête), pour l'annuler de la pose de tête envoyée.
    private var deviceRotationReference: FloatArray = RotationMath.IDENTITY_3X3.copyOf()
    // Détection d'anomalie de calibrage (voir tracking/CalibrationAnomaly.kt, revue technique
    // point 19) -- calculée à chaque frame dans handleTrackingResult, réinitialisée à chaque
    // calibrage (performCalibration). previousBlendshapesForAnomaly sert uniquement à mesurer la
    // variance frame-à-frame (voir meanAbsoluteBlendshapeDelta), distinct de tout autre usage des
    // blendshapes dans cette classe.
    private var calibrationAnomalyState = CalibrationAnomalyState.INITIAL
    private var previousBlendshapesForAnomaly: List<BlendshapeScore> = emptyList()
    private var calibrationCountdownJob: Job? = null
    // Minuteur d'inactivité avant bascule automatique en mode éco -- annulé/reprogrammé à chaque
    // toucher de l'écran (voir onUserInteraction) et à chaque changement de réglage.
    private var powerSaveTimerJob: Job? = null
    // Sondage périodique du throttling thermique (voir startThermalPolling), démarré/arrêté avec le
    // cycle de vie comme deviceOrientationTracker/arCoreHeadPoseTracker ci-dessus.
    private var thermalPollingJob: Job? = null
    // Débit nominal du palier choisi (TierConfig.targetFps) -- mémorisé pour servir de référence à
    // chaque sondage thermique (voir ThermalThrottleState.next), constant pour toute la session.
    private var nominalTargetFps: Int = 0
    private var thermalThrottleState = ThermalThrottleState.initial(nominalFps = 0)
    // Garde contre un double appel à initializeTracking() (ex. permission caméra révoquée puis
    // ré-accordée sans destruction de l'Activity) -- sans cette garde, un second appel recréait un
    // FaceLandmarkerHelper et un CameraController complets par-dessus les précédents, sans jamais
    // fermer/arrêter ceux-là (fuite du FaceLandmarker natif et du thread caméra dédié).
    private var trackingInitialized = false

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
            connectionSettingsStore.vtsHost.collect { host ->
                _uiState.update { it.copy(savedVtsHost = host ?: "") }
            }
        }
        viewModelScope.launch {
            connectionSettingsStore.vtsAuthToken.collect { token -> savedVtsAuthToken = token }
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
        viewModelScope.launch {
            appSettingsStore.faceMeshOverlayEnabled.collect { enabled ->
                _uiState.update { it.copy(faceMeshOverlayEnabled = enabled) }
                // faceLandmarkerHelper peut ne pas encore exister à ce stade (permission caméra
                // pas encore accordée) -- initializeTracking() réapplique l'état courant une fois
                // prêt, même logique que pour setPreviewEnabled côté caméra.
                faceLandmarkerHelper?.setLandmarksNeeded(enabled)
            }
        }
        viewModelScope.launch {
            appSettingsStore.persistBlendshapeSelectionEnabled.collect { enabled ->
                _uiState.update { it.copy(persistBlendshapeSelectionEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.keepMeshOverlayInPowerSave.collect { enabled ->
                _uiState.update { it.copy(keepMeshOverlayInPowerSave = enabled) }
            }
        }
        viewModelScope.launch {
            // Collecté en continu pour que l'écran Diagnostics reflète la valeur persistée --
            // n'affecte le pipeline caméra/MediaPipe qu'au prochain lancement (voir
            // initializeTracking, qui lit ce même Flow une seule fois via first()).
            appSettingsStore.tierOverride.collect { override ->
                _uiState.update { it.copy(tierOverride = override) }
            }
        }
        viewModelScope.launch {
            // Même logique que tierOverride ci-dessus -- collecté en continu pour l'affichage
            // Diagnostics, appliqué au pipeline seulement au prochain lancement (voir
            // initializeTracking, first()).
            appSettingsStore.debugForceArCoreUnavailable.collect { forced ->
                _uiState.update { it.copy(debugForceArCoreUnavailable = forced) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.debugForceGpuUnavailable.collect { forced ->
                _uiState.update { it.copy(debugForceGpuUnavailable = forced) }
            }
        }
        viewModelScope.launch {
            // Chargée une seule fois au lancement (first(), pas collect en continu comme les
            // collecteurs ci-dessus) : contrairement à un simple réglage on/off, réappliquer cette
            // valeur à chaque émission du Flow écraserait la sélection en cours dès que
            // l'utilisateur la modifie lui-même (toggleBlendshapeSelection écrit dans ce même Flow
            // quand la persistance est active, ce qui redéclencherait ce Flow en boucle).
            if (appSettingsStore.persistBlendshapeSelectionEnabled.first()) {
                val savedNames = appSettingsStore.persistedBlendshapeSelectionNames.first()
                _uiState.update { it.copy(selectedBlendshapeNames = savedNames) }
            }
        }
    }

    /**
     * À appeler une fois la permission caméra accordée. Idempotent (voir [trackingInitialized]) :
     * un second appel ne fait rien plutôt que de recréer caméra/MediaPipe par-dessus les instances
     * existantes. `suspend` uniquement pour lire [AppSettingsStore.tierOverride] une seule fois
     * via `first()` avant de choisir le palier (même patron que la lecture ponctuelle de
     * `persistBlendshapeSelectionEnabled` dans [init]) -- appelée depuis un `LaunchedEffect` côté
     * `MainScreen`, déjà une coroutine.
     */
    suspend fun initializeTracking(lifecycleOwner: LifecycleOwner) {
        if (trackingInitialized) return
        trackingInitialized = true

        val context = getApplication<Application>()
        val capabilities = DeviceCapabilityDetector.detect(context)
        val tierOverride = appSettingsStore.tierOverride.first()
        // Mocks de debug (voir MainUiState.debugForceArCoreUnavailable/debugForceGpuUnavailable,
        // AppSettingsStore, DiagnosticsScreen) -- lus une seule fois ici, même contrainte que
        // tierOverride : pas de reconstruction à chaud du pipeline caméra/MediaPipe.
        val debugForceArCoreUnavailable = appSettingsStore.debugForceArCoreUnavailable.first()
        val debugForceGpuUnavailable = appSettingsStore.debugForceGpuUnavailable.first()
        val tierConfig = TrackingTierSelector.select(capabilities, tierOverride)
        nominalTargetFps = tierConfig.targetFps
        thermalThrottleState = ThermalThrottleState.initial(nominalFps = tierConfig.targetFps)

        _uiState.update {
            it.copy(
                tier = tierConfig.tier,
                capabilities = capabilities,
                localIpAddress = getLocalIpAddress() ?: context.getString(R.string.local_ip_unavailable),
            )
        }

        val helper = FaceLandmarkerHelper(
            context = context,
            tierConfig = tierConfig,
            forceGpuUnavailable = debugForceGpuUnavailable,
            onResult = ::handleTrackingResult,
            // Signal de "frame terminé" (par timestamp, pas par objet MPImage -- voir le
            // commentaire sur FaceLandmarkerHelper.onFrameProcessed) -- permet à la source caméra
            // active (CameraController OU ArCoreHeadPoseTracker, jamais les deux) de savoir quand
            // une image de son pool de réutilisation redevient libre, sans jamais écraser une
            // image encore en cours d'analyse côté MediaPipe. Les deux appels sûrs (?.) ci-dessous
            // : un seul des deux fait quelque chose, l'autre est un no-op sur null.
            onFrameProcessed = { frameTimeMs ->
                cameraController?.releaseFrame(frameTimeMs)
                arCoreHeadPoseTracker?.releaseFrame(frameTimeMs)
            },
            onError = ::handleError,
        )
        helper.setup()
        // Réapplique l'état courant du réglage (chargé depuis les préférences, potentiellement
        // avant que le helper n'existe -- voir le collecteur dans init).
        helper.setLandmarksNeeded(_uiState.value.faceMeshOverlayEnabled)
        faceLandmarkerHelper = helper

        _uiState.update { it.copy(activeDelegateIsGpu = helper.activeDelegateIsGpu) }

        // Palier OPTIMAL : ARCore Augmented Faces pilote sa propre caméra (frontale) et sert de
        // source de pose de tête, à la place de CameraX -- les deux ne peuvent pas se partager la
        // caméra (voir revue technique, point 13). Sinon (STANDARD/COMPATIBLE, ou repli), CameraX
        // comme avant. ArCoreHeadPoseTracker continue de nourrir MediaPipe (blendshapes) via le
        // même detectAsync que CameraController -- rien ne change côté FaceLandmarkerHelper.
        if (tierConfig.useArCorePose && !debugForceArCoreUnavailable) {
            val arCoreTracker = ArCoreHeadPoseTracker(
                context = context,
                onFrame = { image, timestampMs -> faceLandmarkerHelper?.detectAsync(image, timestampMs) },
                onHeadPoseRotationMatrix = { matrix -> latestArCoreHeadRotationMatrix = matrix },
                onError = ::handleError,
                // Repli automatique et silencieux sur CameraX si Augmented Faces s'avère
                // indisponible à l'usage malgré le palier OPTIMAL choisi (ARCore non installé,
                // appareil incompatible, config caméra frontale refusée...) -- pas d'erreur
                // affichée à l'utilisateur pour ce cas précis, cohérent avec le comportement
                // documenté au point 13 : c'est un repli attendu, pas une panne.
                // Le fallback ne démarre pas cameraController lui-même (pas de PreviewView
                // disponible ici, elle vit côté MainScreen) : le construire suffit -- l'appel
                // startCamera(previewView) déjà fait par MainScreen juste après
                // initializeTracking() (même LaunchedEffect) le démarrera normalement, puisque
                // ce repli est synchrone (tryCreateSession() ne fait aucun appel asynchrone) et
                // se termine donc avant que MainScreen n'appelle startCamera().
                onUnavailable = { message ->
                    Log.w(TAG, "ARCore indisponible, repli sur CameraX : $message")
                    // close() avant de nuller la référence -- sans ça, imageProcessingExecutor
                    // (thread dédié créé dès la construction, avant même start()) fuyait pour le
                    // reste de la session (relecture du 7 août 2026, point 1).
                    arCoreHeadPoseTracker?.close()
                    arCoreHeadPoseTracker = null
                    _uiState.update { it.copy(usingArCoreCameraSource = false) }
                    cameraController = createCameraController(context, lifecycleOwner, tierConfig)
                },
                initialTargetFps = tierConfig.targetFps,
            )
            arCoreHeadPoseTracker = arCoreTracker
            _uiState.update { it.copy(usingArCoreCameraSource = true) }
            // Tenté tout de suite (pas seulement sur ON_START ci-dessous) : appelé une seconde
            // fois sans risque quand l'observer ON_START se déclenche (Session.resume() sur une
            // session déjà reprise est un no-op documenté côté ARCore) -- le but ici est de
            // détecter tout de suite une indisponibilité (onUnavailable ci-dessus) pendant qu'on
            // est encore dans initializeTracking(), plutôt que de découvrir le repli plus tard.
            arCoreTracker.start()
        } else {
            if (tierConfig.useArCorePose && debugForceArCoreUnavailable) {
                Log.w(TAG, "Mock de debug : ARCore forcé indisponible, repli CameraX simulé.")
            }
            cameraController = createCameraController(context, lifecycleOwner, tierConfig)
        }

        // Le capteur gyroscope tourne en continu tant qu'il est enregistré -- sans rattachement au
        // cycle de vie, il continuait à écouter même l'app mise en arrière-plan (contrairement à
        // CameraX, qui se coupe déjà tout seul via bindToLifecycle). On ne le garde actif qu'entre
        // ON_START et ON_STOP, comme le fait CameraX pour la caméra -- même principe étendu ici à
        // ArCoreHeadPoseTracker, qui doit lui aussi être explicitement pausé/repris (contrairement
        // à CameraController, dont le cycle de vie est déjà géré par CameraX via bindToLifecycle).
        val tracker = DeviceOrientationTracker(context)
        deviceOrientationTracker = tracker
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    tracker.start()
                    arCoreHeadPoseTracker?.start()
                    startThermalPolling(context)
                }
                Lifecycle.Event.ON_STOP -> {
                    tracker.stop()
                    arCoreHeadPoseTracker?.stop()
                    thermalPollingJob?.cancel()
                }
                else -> Unit
            }
        })
    }

    /**
     * Sondage périodique du throttling thermique (voir [DeviceCapabilityDetector.isThermalThrottling])
     * pendant que la capture est active -- réduit le débit cible en cas de chauffe
     * ([ThermalThrottleState.next], remonte automatiquement dès qu'elle retombe), via les mêmes
     * points d'entrée déjà prévus pour un ajustement à chaud (`CameraController`/
     * `ArCoreHeadPoseTracker.setTargetFps`). Ne touche ni au délégué GPU/CPU ni à la source caméra
     * -- décision explicite de ne pas reconstruire le pipeline caméra/MediaPipe à chaud, même
     * principe que le sélecteur de palier manuel (voir TrackingTierSelector). Démarré/arrêté avec
     * le cycle de vie (ON_START/ON_STOP), comme deviceOrientationTracker/arCoreHeadPoseTracker.
     */
    private fun startThermalPolling(context: Context) {
        thermalPollingJob?.cancel()
        thermalPollingJob = viewModelScope.launch {
            while (true) {
                delay(THERMAL_POLL_INTERVAL_MS)
                // Mock de debug (voir MainUiState.debugThermalOverride, DiagnosticsScreen) : lu en
                // direct à chaque sondage, prioritaire sur le capteur réel tant qu'il est non-null.
                val throttling = _uiState.value.debugThermalOverride
                    ?: DeviceCapabilityDetector.isThermalThrottling(context)
                thermalThrottleState = thermalThrottleState.next(nominalTargetFps, throttling)
                cameraController?.setTargetFps(thermalThrottleState.currentFps)
                arCoreHeadPoseTracker?.setTargetFps(thermalThrottleState.currentFps)
                _uiState.update {
                    it.copy(
                        isThermalThrottling = thermalThrottleState.isThrottling,
                        thermalDowngradeSuggested = thermalThrottleState.downgradeSuggested,
                    )
                }
            }
        }
    }

    private fun createCameraController(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        tierConfig: TierConfig,
    ): CameraController = CameraController(
        context = context,
        lifecycleOwner = lifecycleOwner,
        onFrame = { image, timestampMs -> faceLandmarkerHelper?.detectAsync(image, timestampMs) },
        // Débit cible du palier choisi (voir TrackingTierSelector) -- jusqu'ici jamais
        // appliqué : les frames en trop sont désormais ignorées avant toute allocation.
        initialTargetFps = tierConfig.targetFps,
        onError = ::handleError,
    )

    /**
     * À appeler avec le [GLSurfaceView] hébergé côté Compose ([MainScreen]) quand
     * [MainUiState.usingArCoreCameraSource] est actif -- requis par ARCore, qui ne peut piloter la
     * caméra qu'à travers une texture GL (voir [ArCoreHeadPoseTracker.attachTo]). No-op si ARCore
     * n'est pas la source active (ex. repli CameraX déjà effectué).
     */
    fun attachArCoreSurface(glSurfaceView: GLSurfaceView) {
        arCoreHeadPoseTracker?.attachTo(glSurfaceView)
    }

    fun startCamera(previewView: PreviewView) {
        cameraController?.start(previewView)
        // Réapplique l'état courant du mode économie d'énergie (chargé depuis les réglages,
        // potentiellement avant que cameraController n'existe -- voir le collecteur dans init).
        cameraController?.setPreviewEnabled(!_uiState.value.isPowerSaveActive)
    }

    private fun handleTrackingResult(result: FaceTrackingResult) {
        // En palier OPTIMAL avec ARCore actif, la pose de tête vient d'ArCoreHeadPoseTracker
        // (callback onHeadPoseRotationMatrix, thread GL séparé -- voir latestArCoreHeadRotationMatrix)
        // plutôt que de MediaPipe (facialTransformationMatrixes(), toujours calculée par
        // FaceLandmarkerHelper mais ignorée ici dans ce cas) -- voir revue technique, point 13.
        // Repli sur la matrice MediaPipe tant qu'ARCore n'a pas encore émis de pose (ex. tout
        // premier frame après le démarrage de la session).
        lastRawHeadRotationMatrix = if (_uiState.value.usingArCoreCameraSource) {
            latestArCoreHeadRotationMatrix ?: result.headRotationMatrix
        } else {
            result.headRotationMatrix
        }

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

        // Détection d'anomalie de calibrage (voir tracking/CalibrationAnomaly.kt, revue technique
        // point 19) -- gardée derrière isCalibrated : avant le tout premier calibrage, "proche de
        // zéro" n'a pas de sens et ferait remonter de faux positifs. previousBlendshapesForAnomaly
        // est mis à jour inconditionnellement plus bas, y compris avant le premier calibrage.
        if (_uiState.value.isCalibrated) {
            val variance = meanAbsoluteBlendshapeDelta(previousBlendshapesForAnomaly, result.blendshapes)
            val nextAnomalyState = calibrationAnomalyState.next(
                isAtRest = variance <= REST_VARIANCE_THRESHOLD,
                poseMagnitudeDegrees = maxAbsEulerDegrees(calibratedEuler),
                faceDetected = result.faceDetected,
            )
            // Ne pousse dans _uiState que si flagged change réellement -- pas de mise à jour de
            // l'état froid à 20-60 Hz, même discipline hot/cold que le reste de ce fichier.
            if (nextAnomalyState.flagged != calibrationAnomalyState.flagged) {
                _uiState.update { it.copy(calibrationAnomalyFlagged = nextAnomalyState.flagged) }
            }
            calibrationAnomalyState = nextAnomalyState
        }
        previousBlendshapesForAnomaly = result.blendshapes

        _trackingFrame.update {
            it.copy(
                faceDetected = calibrated.faceDetected,
                allBlendshapes = calibrated.blendshapes,
                inferenceTimeMs = calibrated.inferenceTimeMs,
                faceLandmarks = calibrated.faceLandmarks,
                imageWidthPx = calibrated.imageWidthPx,
                imageHeightPx = calibrated.imageHeightPx,
            )
        }
        vmcSender?.send(calibrated)
        iFacialMocapSender?.send(calibrated)
        vtubeStudioSender?.send(calibrated)
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
        // Un recalibrage résout toujours l'anomalie signalée (voir tracking/CalibrationAnomaly.kt)
        // -- c'est la seule façon de la réinitialiser, jamais automatique (voir revue technique,
        // point 19). previousBlendshapesForAnomaly vidé pour ne jamais comparer une frame à
        // travers la frontière du calibrage.
        calibrationAnomalyState = CalibrationAnomalyState.INITIAL
        previousBlendshapesForAnomaly = emptyList()
        _uiState.update { it.copy(isCalibrated = true, calibrationAnomalyFlagged = false) }
    }

    private fun handleError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    // --- Sélection des blendshapes affichées sur l'écran principal (persistée seulement si
    // l'utilisateur l'a activé, voir persistBlendshapeSelectionEnabled) ---

    fun toggleBlendshapeSelection(name: String) {
        _uiState.update { state ->
            val selection = state.selectedBlendshapeNames
            state.copy(selectedBlendshapeNames = if (name in selection) selection - name else selection + name)
        }
        if (_uiState.value.persistBlendshapeSelectionEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                appSettingsStore.setPersistedBlendshapeSelectionNames(_uiState.value.selectedBlendshapeNames)
            }
        }
    }

    /**
     * Réglage : mémorise (ou non) la sélection de blendshapes affichés d'une session à l'autre --
     * désactivé par défaut. Activer le réglage sauvegarde immédiatement la sélection courante
     * (plutôt que d'attendre la prochaine modification), cohérent avec l'attente "j'active, ça
     * mémorise dès maintenant l'état actuel".
     */
    fun setPersistBlendshapeSelectionEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            appSettingsStore.setPersistBlendshapeSelectionEnabled(enabled)
            if (enabled) {
                appSettingsStore.setPersistedBlendshapeSelectionNames(_uiState.value.selectedBlendshapeNames)
            }
        }
    }

    // --- Réglages généraux (persistés) ---

    fun setLowBatteryThresholdPercent(percent: Int) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setLowBatteryThresholdPercent(percent) }
    }

    /** Superposition du mesh de tracking sur l'aperçu caméra -- persisté, réglable dans les réglages. */
    fun setFaceMeshOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setFaceMeshOverlayEnabled(enabled) }
    }

    /**
     * Garde l'overlay du mesh visible même en mode économie d'énergie (tous paliers confondus) --
     * persisté, réglable dans les réglages. Voir ui/MeshOverlayVisibility.kt.
     */
    fun setKeepMeshOverlayInPowerSave(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setKeepMeshOverlayInPowerSave(enabled) }
    }

    /**
     * Force un palier de tracking (ou `null` pour revenir à la sélection automatique) -- persisté,
     * réglable depuis Diagnostics. Ne s'applique qu'au prochain lancement de l'app, voir
     * [MainUiState.tierOverride] et [initializeTracking].
     */
    fun setTierOverride(tier: TrackingTier?) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setTierOverride(tier) }
    }

    // --- Mocks de debug (panneau caché de DiagnosticsScreen, voir revue technique point 35) ---

    /**
     * Mock de debug : force le repli CameraX au palier OPTIMAL même si ARCore est supporté --
     * persisté, ne s'applique qu'au prochain lancement (voir MainUiState.debugForceArCoreUnavailable).
     */
    fun setDebugForceArCoreUnavailable(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setDebugForceArCoreUnavailable(enabled) }
    }

    /** Mock de debug : force le repli CPU même si le délégué GPU est disponible -- persisté, même contrainte. */
    fun setDebugForceGpuUnavailable(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setDebugForceGpuUnavailable(enabled) }
    }

    /**
     * Mock de debug : force le throttling thermique en direct (true = forcer actif, false = forcer
     * inactif, null = automatique/capteur réel) -- lu à chaque sondage (startThermalPolling), effet
     * immédiat, jamais persisté (voir MainUiState.debugThermalOverride).
     */
    fun setDebugThermalOverride(override: Boolean?) {
        _uiState.update { it.copy(debugThermalOverride = override) }
    }

    /**
     * Réinitialise en un seul geste les trois mocks de debug -- accessible depuis le panneau
     * déverrouillé ET depuis l'indicateur toujours visible (voir DiagnosticsScreen), sans avoir à
     * refaire le geste de déverrouillage juste pour "réparer" un mock resté actif par erreur.
     */
    fun resetDebugOverrides() {
        setDebugThermalOverride(null)
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.resetDebugOverrides() }
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
                        val message = getApplication<Application>().getString(R.string.error_vmc_ip_missing)
                        _uiState.update { it.copy(errorMessage = message) }
                    } else {
                        connectVmcTarget(host)
                    }
                }
            }
            ConnectionType.IFACIALMOCAP -> {
                if (_uiState.value.iFacialMocapListening) stopIFacialMocapListening() else startIFacialMocapListening()
            }
            ConnectionType.VTUBE_STUDIO -> {
                val state = _uiState.value.vtsConnectionState
                if (state != VTubeStudioConnectionState.Disconnected && state !is VTubeStudioConnectionState.Failed) {
                    disconnectVtsTarget()
                } else {
                    val host = _uiState.value.savedVtsHost
                    if (host.isBlank()) {
                        val message = getApplication<Application>().getString(R.string.error_vts_ip_missing)
                        _uiState.update { it.copy(errorMessage = message) }
                    } else {
                        connectVtsTarget(host)
                    }
                }
            }
            null -> {
                val message = getApplication<Application>().getString(R.string.error_no_connection_type)
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    // --- VMC : connexion directe vers Blender / Unity (pas VTube Studio, voir revue technique point 39) ---

    /** Configure la cible VMC (IP du PC qui fait tourner Blender / Unity) et l'active. */
    fun connectVmcTarget(hostText: String, port: Int = VmcOscSender.DEFAULT_PORT) {
        _uiState.update { it.copy(vmcConnecting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(hostText)
                vmcSender?.close()
                val sender = VmcOscSender(address, port)
                // Vérifié explicitement (relecture globale du 7 août 2026, point 2) : le
                // constructeur ne propage jamais d'exception même si l'ouverture du port UDP
                // local échoue (voir VmcOscSender.connect()) -- sans cette vérification, l'UI
                // affichait "connecté" même quand rien ne pouvait être envoyé.
                if (sender.isConnected) {
                    vmcSender = sender
                    _uiState.update {
                        it.copy(vmcEnabled = true, vmcConnecting = false, vmcTargetLabel = "$hostText:$port", errorMessage = null)
                    }
                    // Mémorisée pour que le bouton de connexion de l'écran principal puisse s'y
                    // reconnecter directement, sans ressaisir l'IP.
                    connectionSettingsStore.setVmcHost(hostText)
                } else {
                    vmcSender = null
                    val message = getApplication<Application>().getString(R.string.error_vmc_connection_failed, hostText)
                    _uiState.update { it.copy(vmcEnabled = false, vmcConnecting = false, errorMessage = message) }
                }
            } catch (e: Exception) {
                val message = getApplication<Application>().getString(R.string.error_vmc_invalid_address, hostText)
                _uiState.update { it.copy(vmcConnecting = false, errorMessage = message) }
            }
        }
    }

    fun disconnectVmcTarget() {
        vmcSender?.close()
        vmcSender = null
        _uiState.update { it.copy(vmcEnabled = false, vmcConnecting = false, vmcTargetLabel = "") }
    }

    // --- VTube Studio : intégration directe via l'API Plugin (point 39) ---

    /**
     * Configure la cible VTube Studio (IP du PC + port de son API Plugin, 8001 par défaut) et lance
     * la connexion. Asynchrone en plusieurs étapes (voir [MainUiState.vtsConnectionState], mis à
     * jour au fil de la connexion via le callback [VTubeStudioSender] plutôt que d'un coup comme
     * [connectVmcTarget] -- VMC n'a pas de poignée de main, VTube Studio si (socket, popup
     * d'autorisation utilisateur, création des paramètres).
     */
    fun connectVtsTarget(hostText: String, port: Int = VTubeStudioSender.DEFAULT_PORT) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(hostText)
                vtubeStudioSender?.close()
                vtubeStudioSender = VTubeStudioSender(
                    host = address,
                    port = port,
                    storedAuthToken = savedVtsAuthToken,
                    onStateChanged = { state -> _uiState.update { it.copy(vtsConnectionState = state) } },
                    onNewAuthToken = { token ->
                        // Callback appelé depuis un thread interne à nv-websocket-client -- écrire
                        // dans ConnectionSettingsStore (DataStore) est déjà sûr depuis n'importe
                        // quel thread, pas besoin de revenir sur Dispatchers.IO explicitement ici,
                        // mais on le fait quand même pour rester cohérent avec le reste de ce fichier.
                        savedVtsAuthToken = token
                        viewModelScope.launch(Dispatchers.IO) { connectionSettingsStore.setVtsAuthToken(token) }
                    },
                )
                _uiState.update { it.copy(vtsTargetLabel = "$hostText:$port", errorMessage = null) }
                connectionSettingsStore.setVtsHost(hostText)
            } catch (e: Exception) {
                val message = getApplication<Application>().getString(R.string.error_vts_invalid_address, hostText)
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    fun disconnectVtsTarget() {
        vtubeStudioSender?.close()
        vtubeStudioSender = null
        _uiState.update { it.copy(vtsTargetLabel = "", vtsConnectionState = VTubeStudioConnectionState.Disconnected) }
    }

    /**
     * Oublie le jeton d'authentification VTube Studio stocké -- la prochaine connexion redemande
     * une autorisation complète (nouveau popup) plutôt qu'une ré-authentification directe.
     * `VTubeStudioSender` retente déjà automatiquement une fois tout seul si le jeton stocké est
     * refusé (voir revue technique, point 39) ; ce bouton reste utile pour un nouveau départ
     * explicite (ex. l'utilisateur a lui-même révoqué le plugin depuis VTube Studio et le sait).
     */
    fun forgetVtsAuthToken() {
        savedVtsAuthToken = null
        viewModelScope.launch(Dispatchers.IO) { connectionSettingsStore.clearVtsAuthToken() }
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
        arCoreHeadPoseTracker?.stop()
        arCoreHeadPoseTracker?.close()
        faceLandmarkerHelper?.close()
        vmcSender?.close()
        iFacialMocapSender?.stopListening()
        vtubeStudioSender?.close()
        deviceOrientationTracker?.stop()
        calibrationCountdownJob?.cancel()
        powerSaveTimerJob?.cancel()
        thermalPollingJob?.cancel()
    }
}
