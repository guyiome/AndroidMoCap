package com.guyiome.androidmocap.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.guyiome.androidmocap.BuildConfig
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.camera.CameraController
import com.guyiome.androidmocap.capabilities.DeviceCapabilities
import com.guyiome.androidmocap.capabilities.DeviceCapabilityDetector
import com.guyiome.androidmocap.logging.AppLog
import com.guyiome.androidmocap.logging.LogLevel
import com.guyiome.androidmocap.network.IFacialMocapSender
import com.guyiome.androidmocap.network.VTubeStudioConnectionState
import com.guyiome.androidmocap.network.VTubeStudioSender
import com.guyiome.androidmocap.network.VmcOscSender
import com.guyiome.androidmocap.network.getLocalIpAddress
import com.guyiome.androidmocap.sensors.DeviceOrientationTracker
import com.guyiome.androidmocap.settings.AppLanguage
import com.guyiome.androidmocap.settings.AppSettingsStore
import com.guyiome.androidmocap.settings.ConnectionSettingsStore
import com.guyiome.androidmocap.settings.ConnectionType
import com.guyiome.androidmocap.settings.appLanguageFromTag
import com.guyiome.androidmocap.settings.DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT
import com.guyiome.androidmocap.settings.DEFAULT_POWER_SAVE_DELAY_SECONDS
import com.guyiome.androidmocap.settings.TongueCalibrationStore
import com.guyiome.androidmocap.tracking.ArCoreHeadPoseTracker
import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.BrowLandmarkIndices
import com.guyiome.androidmocap.tracking.CalibrationAnomalyState
import com.guyiome.androidmocap.tracking.EyeBlinkCorrectionState
import com.guyiome.androidmocap.tracking.FaceLandmarkerHelper
import com.guyiome.androidmocap.tracking.browRaiseRatioFromLandmarks
import com.guyiome.androidmocap.tracking.colorGateOpen
import com.guyiome.androidmocap.tracking.correctEyeBlinkScores
import com.guyiome.androidmocap.tracking.InferenceLoadState
import com.guyiome.androidmocap.tracking.isRunningHigh
import com.guyiome.androidmocap.tracking.isElevated
import com.guyiome.androidmocap.tracking.classifyTongueState
import com.guyiome.androidmocap.tracking.cosineSimilarity
import com.guyiome.androidmocap.tracking.DEFAULT_CALIBRATION_PREPARE_DURATION_MS
import com.guyiome.androidmocap.tracking.DEFAULT_CALIBRATION_RECORDING_DURATION_MS
import com.guyiome.androidmocap.tracking.DEFAULT_CLASSIFICATION_MARGIN
import com.guyiome.androidmocap.tracking.TONGUE_IN_RECORDING_MULTIPLIER
import com.guyiome.androidmocap.tracking.TONGUE_OUT_RECORDING_MULTIPLIER
import com.guyiome.androidmocap.tracking.TongueEmbeddingClassification
import com.guyiome.androidmocap.tracking.TongueOutDisplayState
import com.guyiome.androidmocap.tracking.TongueOutInjectionState
import com.guyiome.androidmocap.tracking.confirmed
import com.guyiome.androidmocap.tracking.tongueOutInjectionShouldUpdate
import com.guyiome.androidmocap.tracking.jawOpenGateOpen
import com.guyiome.androidmocap.tracking.mouthGeometricGateOpen
import com.guyiome.androidmocap.tracking.mirrorFaceTrackingResult
import com.guyiome.androidmocap.tracking.mouthCropRegion
import com.guyiome.androidmocap.tracking.mouthOpennessRatio
import com.guyiome.androidmocap.tracking.newTongueCalibrationRecording
import com.guyiome.androidmocap.tracking.result
import com.guyiome.androidmocap.tracking.tick
import com.guyiome.androidmocap.tracking.TongueCalibrationPhase
import com.guyiome.androidmocap.tracking.TongueCalibrationRecordingState
import com.guyiome.androidmocap.tracking.TongueCalibrationResult
import com.guyiome.androidmocap.tracking.TongueColorBaseline
import com.guyiome.androidmocap.tracking.TongueEmbeddingHelper
import com.guyiome.androidmocap.tracking.tonguePixelRatio
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
import java.io.File
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
    // défaut). Outil de diagnostic (voir AdvancedSettingsScreen), persisté mais appliqué seulement au
    // prochain lancement de l'app (voir initializeTracking) : changer ce réglage ne reconstruit
    // pas le pipeline caméra/MediaPipe à chaud.
    val tierOverride: TrackingTier? = null,
    val capabilities: DeviceCapabilities? = null,
    val activeDelegateIsGpu: Boolean = false,
    // Choix fait dans l'écran de sélection dédié -- volontairement NON persisté (remis à zéro à
    // chaque lancement de l'app, comme demandé).
    val selectedBlendshapeNames: Set<String> = emptySet(),
    // Poids par blendshape (défaut implicite 1.0 pour toute entrée absente, voir
    // AppSettingsStore.blendshapeWeights) -- pas encore branché sur le pipeline de tracking, voir
    // handleTrackingResult().
    val blendshapeWeights: Map<String, Float> = emptyMap(),
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
    // Charge d'inférence live (voir InferenceLoadMonitor.kt, point 15) -- moyenne mobile de
    // inferenceTimeMs mesurablement au-dessus du budget frame du débit courant. Signal live (pas
    // sticky comme thermalDowngradeSuggested) : redevient faux dès que la charge redescend, pensé
    // pour un avertissement à l'écran qui doit disparaître quand la situation redevient normale.
    val inferenceRunningHigh: Boolean = false,
    // Mocks de debug (panneau caché de AdvancedSettingsScreen, voir revue technique point 35) --
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
    // Langue forcée de l'app (point 30) -- reflète AppCompatDelegate.getApplicationLocales(), pas
    // persisté par ce projet (AppCompat s'en charge lui-même, voir AppLocalesMetadataHolderService
    // dans AndroidManifest.xml) : lu une fois au lancement (voir init), mis à jour immédiatement à
    // chaque changement via setAppLanguage(), sans attendre un redémarrage de l'app.
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    // Niveau minimal conservé dans le fichier de logs exportable (voir logging/AppLog.kt, revue
    // technique point 50) -- collecté en continu (pas juste au lancement) : un changement
    // s'applique immédiatement, contrairement à tierOverride/debugForce* ci-dessus.
    val logLevel: LogLevel = LogLevel.ERROR,
    // Mode miroir (point 51) -- collecté en continu, effet immédiat (même patron que logLevel
    // ci-dessus) : mirrore ensemble tête + blendshapes gauche/droite avant envoi. Activé par
    // défaut (voir AppSettingsStore.mirrorModeEnabled pour le pourquoi), désactivable.
    val mirrorModeEnabled: Boolean = true,
    // Détection expérimentale de la langue tirée (point 15) -- collecté en continu, effet immédiat.
    // Désactivée par défaut, rangée dans "Fonctionnalités expérimentales" (voir
    // AppSettingsStore.tongueOutDetectionEnabled). Affichée dans le panneau de blendshapes local
    // (si tongueOut est coché) ET, depuis le 13 août 2026 (revue technique, point 15/56), réellement
    // injectée dans "finalToSend" et envoyée aux trois protocoles réseau une fois calibré -- ce
    // toggle gouverne donc désormais détection ET injection ensemble, plus de distinction entre les
    // deux comme avant l'activation.
    val tongueOutDetectionEnabled: Boolean = false,
    // Étage 3 (point 15) : calibration personnelle par embedding. Phase de la machine à état
    // (IDLE hors calibration), poussée dans _uiState seulement aux transitions (pas à chaque
    // frame, même discipline que calibrationAnomalyFlagged) -- voir handleTrackingResult().
    val tongueCalibrationPhase: TongueCalibrationPhase = TongueCalibrationPhase.IDLE,
    // Compte à rebours affiché pendant une phase de calibration (enregistrement ou pause de
    // préparation), null hors calibration -- voir handleTrackingResult().
    val tongueCalibrationSecondsRemaining: Int? = null,
    // Miroir UI-only (voir AppSettingsStore.tongueReferencesCalibrated) -- source de vérité réelle :
    // tongueCalibrationResult (privé, chargé depuis TongueCalibrationStore).
    val tongueReferencesCalibrated: Boolean = false,
    // Réglables depuis le panneau debug (ExperimentalFeaturesScreen), voir
    // AppSettingsStore.tongueCalibrationRecordingDurationMs/tongueClassificationMargin.
    val tongueCalibrationRecordingDurationMs: Long = DEFAULT_CALIBRATION_RECORDING_DURATION_MS,
    val tongueClassificationMargin: Float = DEFAULT_CLASSIFICATION_MARGIN,
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

        // Diagnostic du point 28 (fiabilisation eyeBlink) -- logue EAR + blendshape brut/corrigé à
        // chaque frame, lu via `adb logcat -s EarDiag`. A servi à diagnostiquer et confirmer trois
        // correctifs réels (fuite gauche/droite, effondrement en tenue longue, et à isoler qu'un
        // éclairage inégal -- pas le logiciel -- expliquait la faiblesse de l'œil droit, voir revue
        // technique). Désactivé par défaut maintenant que l'investigation est close : remettre à
        // `true` ponctuellement si un nouveau souci de clignement doit être diagnostiqué.
        private const val EAR_DIAGNOSTIC_LOGGING = false
        private const val EAR_DIAG_TAG = "EarDiag"

        // Diagnostic temporaire sourcils (9-10 août 2026, suite discussion pipeline RTX VTube
        // Studio) -- même patron que EAR_DIAGNOSTIC_LOGGING ci-dessus, voir tracking/BrowRaise.kt.
        // Indices gauche/droite toujours pas confirmés sur device : premier test (10 août) non
        // concluant -- pas de marqueurs par étape cette fois-là, et L reste au-dessus de R sur
        // quasi toute la capture y compris au repos, signe d'une asymétrie de fond (angle caméra ?
        // morphologie ?) qui pollue la mesure plutôt qu'une confirmation/infirmation du mapping.
        // Désactivé en attendant un retest propre (marqueurs start/stop par étape) -- remettre à
        // `true` à ce moment-là. Investigation mise en pause, pas close.
        private const val BROW_DIAGNOSTIC_LOGGING = false
        private const val BROW_DIAG_TAG = "BrowDiag"

        // Diagnostic temporaire cohérence rotation tête / regard gauche / regard droit (9 août
        // 2026, doute de l'utilisateur : la tête semble en "mode miroir" pas les yeux) -- les trois
        // sont calculés indépendamment (RotationMath.toEulerDegrees pour la tête, deux formules
        // séparées dans FaceLandmarkerHelper pour les yeux), jamais croisés entre eux jusqu'ici.
        // Sert à vérifier si un même mouvement anatomique (ex. "je tourne la tête vers ma droite")
        // produit bien le même signe de yaw sur les trois signaux, avant de conclure quoi que ce soit.
        private const val ROTATION_DIAGNOSTIC_LOGGING = false
        private const val ROTATION_DIAG_TAG = "RotationDiag"

        // Diagnostic ponctuel (12 août 2026, revue technique points 1/15/20) : pose de tête complète
        // + échantillon de blendshapes, pour comparer la variance entre une tenue immobile en
        // portrait et en paysage. **Conclu** : la rotation caméra fixe (SENSOR_ORIENTATION, jamais
        // recalculée selon la tenue physique) n'est PAS en cause -- le vrai souci était une
        // référence de calibrage de pose de tête restée en portrait pendant que le téléphone passait
        // en paysage (roll mesuré 98,7°±147,5° avant recalibrage en paysage, 1,05°±2,88° après,
        // contre -0,12°±1,0° en portrait -- recalibrer suffit, aucun changement de code nécessaire
        // côté rotation caméra). Repassé à `false`, gardé au cas où un diagnostic similaire (pose/
        // blendshapes en tenue immobile) redevienne utile plus tard -- même discipline que
        // ROTATION_DIAGNOSTIC_LOGGING ci-dessus.
        private const val ROTATION_QUALITY_DIAGNOSTIC_LOGGING = false
        private const val ROTATION_QUALITY_DIAG_TAG = "RotationQualityDiag"

        // Diagnostic de la cascade de détection de la langue tirée (revue technique, point 15) --
        // logue l'état des trois étages (porte jawOpen+mouthGeometric, ratio couleur brut/adaptatif,
        // classification par embedding simOut/simIn) à chaque frame quand tongueOutDetectionEnabled
        // est actif. Phase 1 (étages 1+2) close le 11 août 2026 : étage 1 confirmé fiable, étage 2
        // fonctionnel mécaniquement mais son classifieur couleur seul jugé non fiable. Étage 3
        // (embedding + calibration) implémenté le même jour, activement débogué (locale de
        // calibration, faux positif "bouche pressée" -- voir points 15ter à 15quinquies) sur le
        // moment, ce qui justifiait `true` -- **resté actif par erreur après coup** (laissé à `true`
        // dans un commit du 11 août sans lien avec ce flag, jamais repassé à `false` depuis, retrouvé
        // le 12 août en creusant une sensation de saccade sur le mesh ARCore -- ce log tourne à
        // chaque frame traitée quand le toggle langue tirée est actif, avec extraction de région +
        // formatage de chaîne + écriture logcat, un coût réel maintenant plus perceptible avec le
        // fond caméra live comme référence visuelle).
        // Investigation complète du 13 août 2026 (revue technique, point 15/56) : ANR corrigé
        // (contre-pression sur embed()), gate géométrique étage 1 retiré, gate couleur étage 2
        // retiré, seuil d'anti-rebond abaissé à 2 -- confirmé sur device (streak de 48 TONGUE_OUT
        // consécutifs sur une tenue continue, charge embed() mesurée ~9,6ms en moyenne, largement
        // dans le budget). Repassé à `false`.
        private const val TONGUE_DIAGNOSTIC_LOGGING = false
        private const val TONGUE_DIAG_TAG = "TongueDiag"

        // Intervalle minimal entre deux appels à TongueEmbeddingHelper.embed() (natif, synchrone,
        // coûteux) -- voir tongueEmbeddingLastCallTimestampMs pour le contexte complet (ANR confirmé
        // sur device sans cette limite). ~6-7 Hz max. Ce débit réduit interagit directement avec
        // l'anti-rebond côté injection (DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES = 2,
        // TongueOutInjectionGate.kt) : une classification fraîche n'arrive qu'à ce débit, pas à
        // celui de la caméra -- voir tongueOutInjectionShouldUpdate() pour la garde ajoutée en
        // conséquence (revue de code, 13 août 2026) plutôt que de compter des frames caméra brutes.
        private const val TONGUE_EMBEDDING_MIN_INTERVAL_MS = 150L

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
    private val tongueCalibrationStore = TongueCalibrationStore(application.filesDir)

    // Étage 3 (point 15) : construit seulement quand tongueOutDetectionEnabled est actif (voir le
    // collecteur dans init{}), pas de façon inconditionnelle comme faceLandmarkerHelper -- éviter
    // de charger un second modèle/réserver un délégué GPU-CPU pour qui ne touche jamais cette
    // fonctionnalité expérimentale. @Volatile : écrit depuis le thread principal (collecteur de
    // réglages, initializeTracking(), close() sur toggle-off/onCleared()) mais lu depuis le thread
    // de callback MediaPipe (handleTrackingResult -> sampleMouthTongueEmbedding) -- même situation
    // que latestArCoreHeadRotationMatrix ci-dessous, même traitement.
    @Volatile private var tongueEmbeddingHelper: TongueEmbeddingHelper? = null
    // Mémorisé lors d'initializeTracking() -- nécessaire pour construire tongueEmbeddingHelper à
    // la demande si le toggle est activé après le lancement (tierConfig lui-même n'est qu'une
    // variable locale d'initializeTracking(), jamais stocké ailleurs jusqu'ici).
    private var currentTierConfig: TierConfig? = null
    private var currentDebugForceGpuUnavailable: Boolean = false
    // Chargée une fois (paresseusement, au premier besoin) puis gardée en mémoire -- pas relue à
    // chaque frame, seulement mise à jour après un enregistrement de calibration réussi. @Volatile :
    // écrite sur Dispatchers.IO (chargement initial, sauvegarde de calibration) mais lue depuis le
    // thread de callback MediaPipe -- même raison que tongueEmbeddingHelper ci-dessus.
    @Volatile private var tongueCalibrationResult: TongueCalibrationResult? = null
    private var tongueCalibrationRecordingState = TongueCalibrationRecordingState()
    private var tongueCalibrationLastTimestampMs: Long? = null
    // Lissage/anti-rebond de tongueOut -- pilote à la fois le panneau de test LOCAL et, depuis le
    // 13 août 2026, la valeur réellement injectée dans "final" envoyé au réseau (revue technique,
    // point 15) : les deux utilisent désormais le même signal debouncé/lissé, plus de distinction
    // entre "ce qu'on voit" et "ce qui part". tongueOutInjectionState (TongueOutInjectionGate.kt)
    // exige plusieurs classements TONGUE_OUT consécutifs avant de considérer l'état confirmé --
    // filtre le résidu d'erreurs isolées observé même après l'élargissement de la calibration au
    // mouvement de mâchoire (TongueCalibrationRecordingState.kt) ; tongueOutDisplayState
    // (TongueOutDisplaySmoothing.kt) prend ensuite ce signal déjà debouncé et le maintient affiché
    // un court instant après la dernière confirmation, pour éviter tout clignotement en fin de tenue.
    private var tongueOutInjectionState = TongueOutInjectionState()
    private var tongueOutDisplayState = TongueOutDisplayState()
    private var tongueOutDisplayLastTimestampMs: Long? = null

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
    // Correction eyeBlinkLeft/Right par EAR (tracking/EyeBlinkCorrection.kt, revue technique point
    // 28) -- porte le OneEuroFilter (point 46) à coupure adaptative d'une frame à l'autre.
    // eyeBlinkCorrectionLastTimestampMs sert à calculer le temps réellement écoulé entre deux
    // frames (pas un compte de frames) : le lissage doit avoir la même durée réelle quel que soit
    // le palier/débit courant, voir kdoc d'OneEuroFilter.
    private var eyeBlinkCorrectionState = EyeBlinkCorrectionState()
    private var eyeBlinkCorrectionLastTimestampMs: Long? = null
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
    // Moyenne mobile de inferenceTimeMs (voir InferenceLoadMonitor.kt, point 15) -- alimente
    // MainUiState.inferenceRunningHigh, mise à jour à chaque frame dans handleTrackingResult().
    private var inferenceLoadState = InferenceLoadState()
    // Référence "sans langue" du ratio couleur, suivie dynamiquement (voir TongueColorBaseline.kt,
    // point 15) -- remplace un seuil absolu fixe qui s'est révélé instable d'une session à l'autre
    // sur device (11 août 2026).
    private var tongueColorBaselineState = TongueColorBaseline()
    // Contre-pression sur l'étage 3 (embed() natif, synchrone, coûteux -- MediaPipe ImageEmbedder),
    // ajoutée le 13 août 2026 suite à un ANR confirmé sur device ("Input dispatching timed out")
    // pendant une session où l'utilisateur enchaînait des mouvements de mâchoire/lèvres rapides et
    // variés pour tester la cascade : rien ne limitait jusqu'ici la fréquence d'appel de embed(),
    // qui tournait à chaque frame franchissant les étages 1+2 -- jusqu'au débit caméra complet
    // (30-60 Hz) si les portes restaient ouvertes en continu. Même famille de correctif que
    // ArCoreHeadPoseTracker.MAX_PENDING_CONVERSIONS (contre-pression explicite plutôt que
    // d'espérer que ça ne pose jamais problème). Voir son utilisation plus bas.
    private var tongueEmbeddingLastCallTimestampMs: Long? = null
    // Garde contre un double appel à initializeTracking() (ex. permission caméra révoquée puis
    // ré-accordée sans destruction de l'Activity) -- sans cette garde, un second appel recréait un
    // FaceLandmarkerHelper et un CameraController complets par-dessus les précédents, sans jamais
    // fermer/arrêter ceux-là (fuite du FaceLandmarker natif et du thread caméra dédié).
    private var trackingInitialized = false

    init {
        // Langue forcée (point 30) : lue une seule fois ici, pas de Flow à collecter -- AppCompat
        // gère lui-même la persistance (voir MainUiState.appLanguage), il n'y a rien à observer
        // d'autre que l'état déjà appliqué par le framework au moment où ce ViewModel est créé.
        // toLanguageTags() renvoie une chaîne vide (pas null) quand aucune langue n'est forcée --
        // appLanguageFromTag gère ce cas.
        _uiState.update {
            it.copy(appLanguage = appLanguageFromTag(AppCompatDelegate.getApplicationLocales().toLanguageTags()))
        }
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
                // Ne pilote plus que l'affichage visuel de l'overlay (computeMeshOverlayVisible) --
                // les landmarks sont extraits en permanence depuis le point 28 (correction eyeBlink
                // par EAR), plus seulement quand ce réglage est actif.
                _uiState.update { it.copy(faceMeshOverlayEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.persistBlendshapeSelectionEnabled.collect { enabled ->
                _uiState.update { it.copy(persistBlendshapeSelectionEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.blendshapeWeights.collect { weights ->
                _uiState.update { it.copy(blendshapeWeights = weights) }
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
            // Collecté en continu, effet immédiat (contrairement à tierOverride/debugForce*
            // ci-dessus) : AppLog.setMinimumPersistedLevel doit refléter le réglage courant à
            // chaque log, pas seulement au lancement. La toute première valeur est aussi
            // synchronisée de façon anticipée/bloquante dans initializeTracking() -- voir son kdoc
            // -- ce collecteur reste nécessaire pour les changements faits en direct depuis
            // AdvancedSettingsScreen, après le démarrage.
            appSettingsStore.logLevel.collect { level ->
                _uiState.update { it.copy(logLevel = level) }
                AppLog.setMinimumPersistedLevel(level)
            }
        }
        viewModelScope.launch {
            appSettingsStore.mirrorModeEnabled.collect { enabled ->
                _uiState.update { it.copy(mirrorModeEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.tongueOutDetectionEnabled.collect { enabled ->
                _uiState.update { it.copy(tongueOutDetectionEnabled = enabled) }
                if (enabled) {
                    ensureTongueEmbeddingHelper()
                } else {
                    tongueEmbeddingHelper?.close()
                    tongueEmbeddingHelper = null
                    // Une calibration en cours perd son helper d'embedding juste au-dessus --
                    // annule proprement plutôt que de laisser la machine à état tourner à vide
                    // (embedding toujours null jusqu'à DONE, résultat jeté silencieusement sans
                    // retour utilisateur). Trouvé en revue de code le 11 août 2026.
                    cancelTongueCalibration()
                }
            }
        }
        viewModelScope.launch {
            appSettingsStore.tongueReferencesCalibrated.collect { calibrated ->
                _uiState.update { it.copy(tongueReferencesCalibrated = calibrated) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.tongueCalibrationRecordingDurationMs.collect { durationMs ->
                _uiState.update { it.copy(tongueCalibrationRecordingDurationMs = durationMs) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.tongueClassificationMargin.collect { margin ->
                _uiState.update { it.copy(tongueClassificationMargin = margin) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Chargée une seule fois au lancement -- pas de relecture disque réactive à chaque
            // frame, seulement mise à jour en mémoire après un enregistrement réussi (voir
            // handleTrackingResult()).
            tongueCalibrationResult = tongueCalibrationStore.load()
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

        // Synchronisé ici de façon anticipée/bloquante (pas seulement via le collecteur réactif de
        // logLevel dans init{}, qui pourrait ne pas encore avoir livré sa première valeur à ce
        // stade) -- pour que tous les logs de démarrage qui suivent (palier retenu ci-dessous,
        // démarrage de session ARCore...) respectent déjà le bon niveau. Même classe de bug que
        // celui trouvé sur le tout premier log ARCore, où le niveau par défaut (ERROR) était encore
        // actif au moment du log (revue technique, point 50) -- corrigé une bonne fois ici plutôt
        // que par patch au cas par cas.
        AppLog.setMinimumPersistedLevel(appSettingsStore.logLevel.first())
        AppLog.i(TAG, "Démarrage de l'app, version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        val context = getApplication<Application>()
        val capabilities = DeviceCapabilityDetector.detect(context)
        val tierOverride = appSettingsStore.tierOverride.first()
        // Mocks de debug (voir MainUiState.debugForceArCoreUnavailable/debugForceGpuUnavailable,
        // AppSettingsStore, AdvancedSettingsScreen) -- lus une seule fois ici, même contrainte que
        // tierOverride : pas de reconstruction à chaud du pipeline caméra/MediaPipe.
        val debugForceArCoreUnavailable = appSettingsStore.debugForceArCoreUnavailable.first()
        val debugForceGpuUnavailable = appSettingsStore.debugForceGpuUnavailable.first()
        val tierConfig = TrackingTierSelector.select(capabilities, tierOverride)
        AppLog.i(
            TAG,
            "Palier retenu : ${tierConfig.tier}" +
                if (tierOverride != null) " (forcé manuellement)" else " (sélection automatique)",
        )
        nominalTargetFps = tierConfig.targetFps
        thermalThrottleState = ThermalThrottleState.initial(nominalFps = tierConfig.targetFps)
        currentTierConfig = tierConfig
        currentDebugForceGpuUnavailable = debugForceGpuUnavailable
        // Course d'initialisation corrigée le 11 août 2026, confirmée sur device : le collecteur
        // tongueOutDetectionEnabled (init{}) se déclenche dès la création du ViewModel, AVANT que
        // initializeTracking() (suspend fun, appelée depuis un LaunchedEffect côté UI donc après
        // init{}) n'ait fixé currentTierConfig -- si le toggle était déjà persisté à true,
        // ensureTongueEmbeddingHelper() abandonnait silencieusement (currentTierConfig encore null)
        // et ne réessayait plus jamais (le Flow ne réémet pas tant que la valeur ne change pas).
        // Symptôme observé : une calibration se termine ses deux phases normalement côté UI, mais
        // aucun embedding n'a jamais été calculé -- rien n'est sauvegardé, "jamais calibré" persiste.
        // Ce deuxième appel, maintenant que currentTierConfig est connu, rattrape ce cas -- mais
        // seulement si le toggle est bien actif (ensureTongueEmbeddingHelper() ne le vérifie pas
        // elle-même, c'était jusqu'ici la responsabilité de l'appelant, le collecteur).
        if (_uiState.value.tongueOutDetectionEnabled) {
            ensureTongueEmbeddingHelper()
        }

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
                    AppLog.w(TAG, "ARCore indisponible, repli sur CameraX : $message")
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
                AppLog.w(TAG, "Mock de debug : ARCore forcé indisponible, repli CameraX simulé.")
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
                // Mock de debug (voir MainUiState.debugThermalOverride, AdvancedSettingsScreen) : lu en
                // direct à chaque sondage, prioritaire sur le capteur réel tant qu'il est non-null.
                val throttling = _uiState.value.debugThermalOverride
                    ?: DeviceCapabilityDetector.isThermalThrottling(context)
                val previousFps = thermalThrottleState.currentFps
                thermalThrottleState = thermalThrottleState.next(nominalTargetFps, throttling)
                // Débit réellement changé (pas juste "suggestion" -- thermalDowngradeSuggested
                // ci-dessous reste un signal séparé, potentiellement affiché sans qu'un changement
                // de débit ait eu lieu) : log une seule fois par transition, pas à chaque sondage.
                if (thermalThrottleState.currentFps != previousFps) {
                    AppLog.i(
                        TAG,
                        "Débit ajusté par le throttling thermique : $previousFps -> ${thermalThrottleState.currentFps} fps",
                    )
                }
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

        // Correction eyeBlinkLeft/Right par EAR (revue technique, point 28) : le blendshape brut
        // fuit d'un œil vers l'autre pendant un clin d'œil isolé (mesuré sur device -- pas
        // supposé), l'EAR (géométrique, indépendant du classifieur ML) fuit beaucoup moins dans
        // les mêmes conditions -- utilisé ici pour atténuer les fuites plutôt que remplacer le
        // blendshape. Temps écoulé depuis la frame précédente (pas un compte de frames) pour que le
        // lissage du OneEuroFilter (point 46) ait la même durée réelle quel que soit le débit
        // courant ; 0 par défaut pour la toute première frame (pas de remontée à lisser, rien à corriger).
        val elapsedMs = eyeBlinkCorrectionLastTimestampMs?.let { calibrated.timestampMs - it } ?: 0L
        eyeBlinkCorrectionLastTimestampMs = calibrated.timestampMs
        val (correctedBlendshapes, nextEyeBlinkCorrectionState) = correctEyeBlinkScores(
            calibrated.blendshapes,
            calibrated.faceLandmarks,
            eyeBlinkCorrectionState,
            elapsedMs,
        )
        eyeBlinkCorrectionState = nextEyeBlinkCorrectionState
        val corrected = calibrated.copy(blendshapes = correctedBlendshapes)

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

        // Mode miroir (point 51) : mirrore ensemble tête + blendshapes gauche/droite + regard par
        // œil, appliqué en tout dernier -- après la correction EAR, jamais avant (EAR travaille sur
        // l'identité anatomique réelle des landmarks, indépendante de ce réglage). Activé par
        // défaut (voir AppSettingsStore.mirrorModeEnabled) -- désactiver ce réglage envoie
        // "corrected" tel quel, sortie native/anatomique.
        val final = if (_uiState.value.mirrorModeEnabled) mirrorFaceTrackingResult(corrected) else corrected

        // Charge d'inférence live (point 15) -- budget suit le débit courant, potentiellement déjà
        // réduit par le throttling thermique (thermalThrottleState.currentFps), pour ne pas signaler
        // une fausse alerte de charge quand c'est juste le débit qui a été volontairement abaissé.
        inferenceLoadState = inferenceLoadState.next(final.inferenceTimeMs)
        val frameBudgetMs = 1000L / thermalThrottleState.currentFps.coerceAtLeast(1)
        val runningHigh = inferenceLoadState.isRunningHigh(frameBudgetMs)
        if (runningHigh != _uiState.value.inferenceRunningHigh) {
            _uiState.update { it.copy(inferenceRunningHigh = runningHigh) }
        }

        _trackingFrame.update {
            it.copy(
                faceDetected = final.faceDetected,
                allBlendshapes = final.blendshapes,
                inferenceTimeMs = final.inferenceTimeMs,
                faceLandmarks = final.faceLandmarks,
                imageWidthPx = final.imageWidthPx,
                imageHeightPx = final.imageHeightPx,
            )
        }
        // Envoi réseau déplacé après la cascade de détection de la langue tirée plus bas (revue
        // technique, point 15, 13 août 2026) -- permet d'y injecter tongueOut une fois confirmé
        // fiable, voir tongueOutValueForInjection et son utilisation en fin de fonction.

        // Diagnostic temporaire EAR (voir la constante EAR_DIAGNOSTIC_LOGGING ci-dessus) -- brut ET
        // corrigé côte à côte pour vérifier sur device que la correction atténue bien les fuites
        // sans toucher aux vraies fermetures. Rien à loguer tant que les landmarks ne sont pas
        // extraits (aucune frame traitée pour l'instant).
        if (EAR_DIAGNOSTIC_LOGGING && calibrated.faceLandmarks.isNotEmpty()) {
            val blinkLeftRaw = calibrated.blendshapes.firstOrNull { it.name == "eyeBlinkLeft" }?.score ?: 0f
            val blinkRightRaw = calibrated.blendshapes.firstOrNull { it.name == "eyeBlinkRight" }?.score ?: 0f
            val blinkLeftCorrected = corrected.blendshapes.firstOrNull { it.name == "eyeBlinkLeft" }?.score ?: 0f
            val blinkRightCorrected = corrected.blendshapes.firstOrNull { it.name == "eyeBlinkRight" }?.score ?: 0f
            AppLog.d(
                EAR_DIAG_TAG,
                "eyeBlinkLeft brut=%.3f corrige=%.3f | eyeBlinkRight brut=%.3f corrige=%.3f".format(
                    blinkLeftRaw, blinkLeftCorrected, blinkRightRaw, blinkRightCorrected,
                ),
            )
        }

        // Diagnostic temporaire sourcils (voir BROW_DIAGNOSTIC_LOGGING ci-dessus) -- score brut de
        // chaque côté à côté du ratio géométrique calculé avec la correspondance gauche/droite
        // hypothétique (BrowLandmarkIndices) : sert à confirmer ou infirmer cette correspondance,
        // pas encore à corriger quoi que ce soit.
        if (BROW_DIAGNOSTIC_LOGGING && calibrated.faceLandmarks.isNotEmpty()) {
            val browOuterUpLeftRaw = calibrated.blendshapes.firstOrNull { it.name == "browOuterUpLeft" }?.score ?: 0f
            val browOuterUpRightRaw = calibrated.blendshapes.firstOrNull { it.name == "browOuterUpRight" }?.score ?: 0f
            val browDownLeftRaw = calibrated.blendshapes.firstOrNull { it.name == "browDownLeft" }?.score ?: 0f
            val browDownRightRaw = calibrated.blendshapes.firstOrNull { it.name == "browDownRight" }?.score ?: 0f
            val browRaiseLeft = browRaiseRatioFromLandmarks(calibrated.faceLandmarks, BrowLandmarkIndices.LEFT_EYEBROW)
            val browRaiseRight = browRaiseRatioFromLandmarks(calibrated.faceLandmarks, BrowLandmarkIndices.RIGHT_EYEBROW)
            AppLog.d(
                BROW_DIAG_TAG,
                "L outerUp=%.3f down=%.3f ratio=%.3f | R outerUp=%.3f down=%.3f ratio=%.3f".format(
                    browOuterUpLeftRaw, browDownLeftRaw, browRaiseLeft,
                    browOuterUpRightRaw, browDownRightRaw, browRaiseRight,
                ),
            )
        }

        // Diagnostic temporaire cohérence rotation (voir ROTATION_DIAGNOSTIC_LOGGING ci-dessus) --
        // sur "final" (donc après mode miroir éventuel) : ce qui est réellement envoyé, pas la
        // valeur intermédiaire pré-miroir.
        if (ROTATION_DIAGNOSTIC_LOGGING && final.faceDetected) {
            AppLog.d(
                ROTATION_DIAG_TAG,
                "tete yaw=%.1f | oeil gauche yaw=%.1f | oeil droit yaw=%.1f".format(
                    final.headEulerDegrees.getOrElse(1) { 0f },
                    final.leftEyeEulerDegrees.getOrElse(1) { 0f },
                    final.rightEyeEulerDegrees.getOrElse(1) { 0f },
                ),
            )
        }

        // Diagnostic temporaire qualité/précision du tracking selon la rotation caméra fixe (voir
        // ROTATION_QUALITY_DIAGNOSTIC_LOGGING ci-dessus). Pose de tête complète + échantillon de
        // blendshapes représentatif (mâchoire, bouche, sourcil, yeux, joue) -- comparer la variance
        // entre une tenue immobile en portrait et une tenue immobile en paysage.
        if (ROTATION_QUALITY_DIAGNOSTIC_LOGGING && final.faceDetected) {
            val jawOpen = final.blendshapes.firstOrNull { it.name == "jawOpen" }?.score ?: 0f
            val mouthClose = final.blendshapes.firstOrNull { it.name == "mouthClose" }?.score ?: 0f
            val browInnerUp = final.blendshapes.firstOrNull { it.name == "browInnerUp" }?.score ?: 0f
            val eyeBlinkLeft = final.blendshapes.firstOrNull { it.name == "eyeBlinkLeft" }?.score ?: 0f
            val eyeBlinkRight = final.blendshapes.firstOrNull { it.name == "eyeBlinkRight" }?.score ?: 0f
            val cheekPuff = final.blendshapes.firstOrNull { it.name == "cheekPuff" }?.score ?: 0f
            AppLog.d(
                ROTATION_QUALITY_DIAG_TAG,
                "pitch=%.2f yaw=%.2f roll=%.2f jawOpen=%.4f mouthClose=%.4f browInnerUp=%.4f eyeBlinkLeft=%.4f eyeBlinkRight=%.4f cheekPuff=%.4f inferenceMs=%d".format(
                    final.headEulerDegrees.getOrElse(0) { 0f },
                    final.headEulerDegrees.getOrElse(1) { 0f },
                    final.headEulerDegrees.getOrElse(2) { 0f },
                    jawOpen, mouthClose, browInnerUp, eyeBlinkLeft, eyeBlinkRight, cheekPuff,
                    final.inferenceTimeMs,
                ),
            )
        }

        // Calibration de l'étage 3 (point 15) -- tourne indépendamment des étages 1/2 : l'utilisateur
        // tient le geste sur commande (TongueCalibrationScreen), gater sur jawOpen/couleur ici serait
        // contre-productif. Temps écoulé depuis le tick précédent (pas un compte de frames), même
        // discipline que eyeBlinkCorrectionLastTimestampMs plus haut.
        // Réutilisé plus bas (cascade) pour éviter un double appel à embed() -- voir son usage.
        val tongueCalibrationRecordingActive = tongueCalibrationRecordingState.phase != TongueCalibrationPhase.IDLE &&
            tongueCalibrationRecordingState.phase != TongueCalibrationPhase.DONE
        if (tongueCalibrationRecordingActive) {
            val calibrationElapsedMs = tongueCalibrationLastTimestampMs?.let { corrected.timestampMs - it } ?: 0L
            tongueCalibrationLastTimestampMs = corrected.timestampMs
            val embedding = sampleMouthTongueEmbedding(
                corrected.timestampMs,
                corrected.faceLandmarks,
                corrected.imageWidthPx,
                corrected.imageHeightPx,
            )
            val previousPhase = tongueCalibrationRecordingState.phase
            val recordingDurationMs = _uiState.value.tongueCalibrationRecordingDurationMs
            // Les deux phases dérivent de la même unité de base réglable par un multiplicateur
            // dédié (voir kdoc TONGUE_OUT_RECORDING_MULTIPLIER/TONGUE_IN_RECORDING_MULTIPLIER,
            // TongueCalibrationRecordingState.kt) -- couvre plusieurs cycles de variation pendant
            // l'enregistrement (mouvement de mâchoire côté "in", légère variation de
            // position/profondeur de la langue côté "out"), pas juste une pose statique.
            val tongueOutDurationMs = recordingDurationMs * TONGUE_OUT_RECORDING_MULTIPLIER
            val tongueInDurationMs = recordingDurationMs * TONGUE_IN_RECORDING_MULTIPLIER
            tongueCalibrationRecordingState = tongueCalibrationRecordingState.tick(
                calibrationElapsedMs,
                embedding,
                tongueOutDurationMs,
                tongueInDurationMs = tongueInDurationMs,
            )
            if (tongueCalibrationRecordingState.phase == TongueCalibrationPhase.DONE) {
                val result = tongueCalibrationRecordingState.result()
                if (result != null) {
                    tongueCalibrationResult = result
                    tongueCalibrationStore.save(result)
                    viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setTongueReferencesCalibrated(true) }
                }
                // Repart directement à IDLE -- une nouvelle calibration remplace entièrement
                // l'ancienne (même principe que performCalibration(), pas de fusion partielle).
                tongueCalibrationRecordingState = TongueCalibrationRecordingState()
                tongueCalibrationLastTimestampMs = null
                _uiState.update {
                    it.copy(tongueCalibrationPhase = TongueCalibrationPhase.IDLE, tongueCalibrationSecondsRemaining = null)
                }
            } else {
                // Compte à rebours dérivé du même état réel qui pilote l'accumulation (pas un
                // minuteur indépendant, voir kdoc de TongueCalibrationScreen) -- durée de la phase
                // courante selon qu'on est en enregistrement (réglable, panneau debug) ou en pause
                // de préparation (constante, pas encore réglable). Poussé à _uiState seulement au
                // changement de seconde affichée ou de phase, pas à chaque frame (même discipline
                // que calibrationAnomalyFlagged) : ceil() pour un décompte 3,2,1 naturel plutôt que
                // de sauter directement à 2 dès la première frame.
                val phaseDurationMs = when (tongueCalibrationRecordingState.phase) {
                    TongueCalibrationPhase.PREPARE_TONGUE_OUT,
                    TongueCalibrationPhase.PREPARE_TONGUE_IN -> DEFAULT_CALIBRATION_PREPARE_DURATION_MS
                    TongueCalibrationPhase.RECORDING_TONGUE_IN -> tongueInDurationMs
                    else -> tongueOutDurationMs
                }
                val secondsRemaining = kotlin.math.ceil(
                    (phaseDurationMs - tongueCalibrationRecordingState.elapsedMsInPhase) / 1000f
                ).toInt().coerceAtLeast(0)
                if (tongueCalibrationRecordingState.phase != previousPhase ||
                    secondsRemaining != _uiState.value.tongueCalibrationSecondsRemaining
                ) {
                    _uiState.update {
                        it.copy(
                            tongueCalibrationPhase = tongueCalibrationRecordingState.phase,
                            tongueCalibrationSecondsRemaining = secondsRemaining,
                        )
                    }
                }
            }
        }

        // Cascade de détection de la langue tirée, phase 1+3 (revue technique, point 15) -- gatée
        // par le réglage expérimental. Injectée dans "final" envoyé au réseau depuis le 13 août 2026
        // (voir tongueOutValueForInjection ci-dessous et son utilisation en fin de fonction) --
        // seulement après deux sessions de test device avec une calibration élargie au mouvement de
        // mâchoire (TongueCalibrationRecordingState.kt) et un anti-rebond dédié
        // (TongueOutInjectionGate.kt) pour filtrer le résidu d'erreurs isolées encore observé.
        // null tant que le toggle est désactivé -- "final" part alors inchangé, comme avant.
        var tongueOutValueForInjection: Float? = null
        if (_uiState.value.tongueOutDetectionEnabled) {
            val jawOpen = corrected.blendshapes.firstOrNull { it.name == "jawOpen" }?.score ?: 0f
            // Diagnostic croisé (voir kdoc de mouthOpennessRatio, LipLandmarks.kt) : mesure
            // géométrique indépendante du classifieur ML de jawOpen -- ajouté le 11 août 2026 après
            // avoir constaté sur device que jawOpen s'effondrait de façon inattendue pendant une
            // vraie langue tirée, pour vérifier si c'est un artefact du blendshape ML (la langue
            // perturbe le suivi des landmarks mâchoire) ou un vrai relâchement de la bouche.
            val mouthGeometric = mouthOpennessRatio(corrected.faceLandmarks)
            var tongueOutClassification: TongueEmbeddingClassification? = null
            // ⚠️ mouthGeometricGateOpen retiré de la porte dure le 13 août 2026 (revue technique,
            // point 15/56) -- gardé en jawOpen seul. Ajouté le 11 août pour bloquer un faux positif
            // "bouche pressée" (jawOpen 0,3-0,5+ malgré une bouche géométriquement fermée, point
            // 15quinquies), mais confirmé sur device qu'une VRAIE tenue "langue dehors" produit
            // exactement la même signature (jawOpen 0,43-0,73 ET mouthGeo très bas, 0,005-0,199 --
            // la langue elle-même semble perturber le suivi des landmarks internes 13/14 utilisés
            // par mouthOpennessRatio) : chevauchement quasi total des deux plages, aucun seuil ne
            // peut les séparer. mouthGeometric reste calculé et loggé (diagnostic), mais ne bloque
            // plus l'étage 1 -- pari que les étages 2/3 (nettement renforcés depuis, calibration
            // élargie + anti-rebond d'injection) suffisent à rejeter le cas "bouche pressée" en
            // aval (probablement classé UNDECIDED/TONGUE_IN plutôt que confondu avec TONGUE_OUT,
            // hypothèse à confirmer sur device -- à revenir en arrière si le faux positif d'origine
            // réapparaît).
            // Calculé une seule fois, réutilisé pour le if ci-dessous, le log de diagnostic de la
            // branche else, et tongueOutInjectionShouldUpdate() plus bas (revue de code, 13 août
            // 2026 -- évite trois appels équivalents à jawOpenGateOpen(jawOpen) sur la même frame).
            val stage1Open = jawOpenGateOpen(jawOpen)
            if (stage1Open) {
                val ratio = sampleMouthTonguePixelRatio(
                    corrected.timestampMs,
                    corrected.faceLandmarks,
                    corrected.imageWidthPx,
                    corrected.imageHeightPx,
                )
                // Diagnostic seul (loggé plus bas, "etage2=") -- ne gate rien. Le seuil fixe
                // (DEFAULT_COLOR_RATIO_THRESHOLD, MouthColorAnalysis.kt) qu'il utilise s'est montré
                // pas assez stable d'une session à l'autre pour porter une vraie décision (revue
                // technique, point 15bis) ; seul adaptiveFired ci-dessous, sur la référence
                // adaptative, déclenche réellement l'étage 3. Gardé pour comparer les deux signaux
                // dans les logs tant que l'étage 2 est encore sous investigation.
                val colorFired = ratio != null && colorGateOpen(ratio)
                // Comparaison avec la référence adaptative (TongueColorBaseline.kt, point 15) --
                // comparée AVANT mise à jour (sinon le ratio courant s'auto-compare à une référence
                // qui vient de l'intégrer). Mise à jour juste après, avec ce même ratio, pour la
                // frame suivante -- uniquement quand la bouche est déjà assez ouverte (ce bloc),
                // jawOpen sous le seuil ne calcule pas de ratio (voir étage 1) donc ne participe pas
                // à la référence.
                val adaptiveFired = ratio != null && tongueColorBaselineState.isElevated(ratio)
                if (ratio != null) {
                    tongueColorBaselineState = tongueColorBaselineState.next(ratio)
                }

                // Étage 3 (embedding + calibration personnelle, revue technique point 15) -- ne
                // dépend plus d'un indice positif de l'étage 2 depuis le 13 août 2026 (adaptiveFired
                // retiré du gate, comme mouthGeometricGateOpen à l'étage 1 le même jour) : constaté
                // sur device qu'il bloquait 63% des frames où la mâchoire était pourtant ouverte
                // (276/435 sur une session de tenue continue), l'étage 3 ne recevant alors jamais sa
                // chance de trancher. adaptiveFired/colorFired restent calculés et loggés
                // (diagnostic), ne bloquent plus rien -- ne reste que jawOpenGateOpen (étage 1) ET
                // une calibration existante.
                var simOut: Float? = null
                var simIn: Float? = null
                val calibrationResult = tongueCalibrationResult
                // Contre-pression (TONGUE_EMBEDDING_MIN_INTERVAL_MS, voir kdoc de
                // tongueEmbeddingLastCallTimestampMs) -- embed() natif synchrone, ne pas le rappeler
                // plus vite que ce débit même si l'étage 1 reste ouvert en continu sur des dizaines
                // de frames d'affilée (ANR confirmé sur device sans cette garde) -- d'autant plus
                // important maintenant que l'étage 2 ne filtre plus rien en amont.
                val embeddingThrottled = tongueEmbeddingLastCallTimestampMs?.let {
                    corrected.timestampMs - it < TONGUE_EMBEDDING_MIN_INTERVAL_MS
                } ?: false
                // !tongueCalibrationRecordingActive (revue de code, 13 août 2026) : sans cette
                // garde, une recalibration ("Recalibrer", calibration précédente encore chargée
                // dans tongueCalibrationResult tant que la nouvelle n'est pas DONE) appelait embed()
                // deux fois par frame -- une fois ici (contre l'ancienne référence, gaspillé : sur
                // le point de devenir obsolète) et une fois non-throttlée pour l'accumulateur de
                // calibration (voir plus haut) -- même coût natif que celui protégé par
                // TONGUE_EMBEDDING_MIN_INTERVAL_MS contre l'ANR, sur un chemin que ce throttle ne
                // couvrait pas.
                if (calibrationResult != null && !embeddingThrottled && !tongueCalibrationRecordingActive) {
                    tongueEmbeddingLastCallTimestampMs = corrected.timestampMs
                    val embedding = sampleMouthTongueEmbedding(
                        corrected.timestampMs,
                        corrected.faceLandmarks,
                        corrected.imageWidthPx,
                        corrected.imageHeightPx,
                    )
                    if (embedding != null) {
                        simOut = cosineSimilarity(embedding, calibrationResult.tongueOutReference)
                        simIn = cosineSimilarity(embedding, calibrationResult.tongueInReference)
                        tongueOutClassification = classifyTongueState(
                            embedding,
                            calibrationResult.tongueOutReference,
                            calibrationResult.tongueInReference,
                            margin = _uiState.value.tongueClassificationMargin,
                        )
                    }
                }

                if (TONGUE_DIAGNOSTIC_LOGGING) {
                    // Région de recadrage : coordonnées seules (pas de lecture/écriture de bitmap,
                    // contrairement à l'ancien saveTongueDebugCrop() retiré pour la perf) -- ajouté
                    // le 11 août 2026 pour diagnostiquer un faux positif reproductible où mouthGeo
                    // reste proche de zéro (bouche quasi fermée) alors que ratio reste pégé à 1.0,
                    // signe possible d'un recadrage qui tombe sur la peau des lèvres plutôt que
                    // l'intérieur de la bouche quand celle-ci est peu ouverte.
                    val region = mouthCropRegion(corrected.faceLandmarks, corrected.imageWidthPx, corrected.imageHeightPx)
                    AppLog.d(
                        TONGUE_DIAG_TAG,
                        "jawOpen=%.3f mouthGeo=%.3f etage1=OK ratio=%s etage2=%s etage2Adaptatif=%s baseline=%.4f etage3=%s simOut=%s simIn=%s recadrage=%s".format(
                            jawOpen, mouthGeometric, ratio, colorFired, adaptiveFired, tongueColorBaselineState.value,
                            tongueOutClassification, simOut, simIn,
                            region?.let { "x=${it.x} y=${it.y} w=${it.width} h=${it.height}" } ?: "null",
                        ),
                    )
                }
            } else if (TONGUE_DIAGNOSTIC_LOGGING) {
                AppLog.d(
                    TONGUE_DIAG_TAG,
                    "jawOpen=%.3f mouthGeo=%.3f etage1=NON (jawOpenOk=%s mouthGeoOk=%s)".format(
                        jawOpen, mouthGeometric, stage1Open, mouthGeometricGateOpen(mouthGeometric),
                    ),
                )
            }

            // Anti-rebond avant injection (TongueOutInjectionGate.kt, 13 août 2026) : exige plusieurs
            // classements TONGUE_OUT consécutifs avant de considérer l'état confirmé -- filtre le
            // résidu d'erreurs isolées observé même après l'élargissement de la calibration au
            // mouvement de mâchoire (voir son kdoc pour le détail des deux sessions de test). Le
            // panneau LOCAL de test et la valeur réellement envoyée au réseau partagent désormais ce
            // même signal debouncé -- plus de distinction "ce qu'on voit" / "ce qui part".
            //
            // ⚠️ N'appelle next() que si tongueOutInjectionShouldUpdate() dit que cette frame apporte
            // une vraie information (revue de code du 13 août 2026, même jour) -- sans cette garde,
            // une frame où l'étage 1 reste ouvert mais où embed() a été sauté par le throttle
            // (TONGUE_EMBEDDING_MIN_INTERVAL_MS, ~150ms) remettait le compteur à zéro comme une
            // vraie classification négative, alors qu'aucune classification n'avait été tentée --
            // rendait la confirmation à 2 frames consécutives quasi impossible dès que la caméra
            // tourne plus vite que le throttle (20-60Hz typique contre ~150-230ms entre deux
            // tentatives réussies mesuré sur device), malgré une classification qui revenait
            // pourtant correctement et régulièrement TONGUE_OUT. Voir kdoc de
            // tongueOutInjectionShouldUpdate().
            if (tongueOutInjectionShouldUpdate(stage1Open, tongueOutClassification)) {
                tongueOutInjectionState = tongueOutInjectionState.next(tongueOutClassification)
            }
            val debouncedClassification =
                if (tongueOutInjectionState.confirmed()) TongueEmbeddingClassification.TONGUE_OUT else null

            // Lissé (TongueOutDisplaySmoothing.kt) plutôt qu'un booléen brut par frame -- retour
            // utilisateur (11 août 2026) : pendant une tenue réelle, l'étage 3 alterne souvent
            // TONGUE_OUT/UNDECIDED d'une frame à l'autre, ce qui clignotait 1/0 à l'écran. Le
            // lissage ne change aucune décision de la cascade elle-même (etage3/simOut/simIn dans
            // les logs restent bruts, non lissés/non debouncés).
            val displayElapsedMs = tongueOutDisplayLastTimestampMs?.let { corrected.timestampMs - it } ?: 0L
            tongueOutDisplayLastTimestampMs = corrected.timestampMs
            tongueOutDisplayState = tongueOutDisplayState.next(debouncedClassification, displayElapsedMs)
            val tongueOutDisplayValue = if (tongueOutDisplayState.displayedAsOut) 1f else 0f
            _trackingFrame.update { it.copy(allBlendshapes = it.allBlendshapes + BlendshapeScore("tongueOut", tongueOutDisplayValue)) }
            tongueOutValueForInjection = tongueOutDisplayValue
        }

        // Envoi réseau (revue technique, point 15) : injecte tongueOut si le toggle expérimental est
        // actif (tongueOutValueForInjection non-null), sinon "final" part inchangé -- déplacé après
        // la cascade ci-dessus (auparavant plus haut dans cette fonction, avant que la cascade ait pu
        // calculer quoi que ce soit) pour rendre cette injection possible.
        val finalToSend = tongueOutValueForInjection?.let { value ->
            final.copy(blendshapes = final.blendshapes + BlendshapeScore("tongueOut", value))
        } ?: final
        vmcSender?.send(finalToSend)
        iFacialMocapSender?.send(finalToSend)
        vtubeStudioSender?.send(finalToSend)
    }

    /**
     * Étage 2 de la cascade langue tirée (point 15) : recadre la région buccale à partir des
     * landmarks puis calcule la fraction de pixels "couleur langue" ([tonguePixelRatio]). `null` si
     * le recadrage est impossible (landmarks insuffisants) ou si le bitmap du frame correspondant
     * n'est plus disponible. Essaie `cameraController` (palier CameraX) puis `arCoreHeadPoseTracker`
     * (palier OPTIMAL) -- mutuellement exclusifs (voir leur déclaration), au plus un des deux
     * renvoie un bitmap. `ArCoreHeadPoseTracker.peekLastBitmap` ajouté le 11 août 2026 pour lever la
     * limite initiale de la phase 1 ("pas de pool de bitmap côté ARCore") -- voir son kdoc pour le
     * détail (pas un vrai pool, juste le dernier bitmap converti retenu un instant de plus). Glue
     * Android non testée (accès direct à un `Bitmap`), même catégorie que le reste de
     * `CameraController`/`ArCoreHeadPoseTracker` touchant `Bitmap`/`Canvas`.
     */
    private fun sampleMouthTonguePixelRatio(
        frameTimeMs: Long,
        landmarks: List<Pair<Float, Float>>,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): Float? {
        val region = mouthCropRegion(landmarks, imageWidthPx, imageHeightPx) ?: return null
        val bitmap = cameraController?.peekPooledBitmap(frameTimeMs)
            ?: arCoreHeadPoseTracker?.peekLastBitmap(frameTimeMs)
            ?: return null
        val pixels = IntArray(region.width * region.height)
        bitmap.getPixels(pixels, 0, region.width, region.x, region.y, region.width, region.height)
        return tonguePixelRatio(pixels)
    }

    /**
     * Calcule l'embedding du recadrage buccal courant pour l'étage 3 (point 15) -- réutilise le
     * même accès bitmap synchrone que [sampleMouthTonguePixelRatio]. `null`
     * si le recadrage est impossible, si aucun bitmap n'est disponible, ou si
     * [tongueEmbeddingHelper] n'est pas prêt (toggle expérimental éteint, ou pas encore de frame
     * traitée depuis son activation).
     */
    private fun sampleMouthTongueEmbedding(
        frameTimeMs: Long,
        landmarks: List<Pair<Float, Float>>,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): FloatArray? {
        val helper = tongueEmbeddingHelper ?: return null
        val region = mouthCropRegion(landmarks, imageWidthPx, imageHeightPx) ?: return null
        val bitmap = cameraController?.peekPooledBitmap(frameTimeMs)
            ?: arCoreHeadPoseTracker?.peekLastBitmap(frameTimeMs)
            ?: return null
        val cropped = Bitmap.createBitmap(bitmap, region.x, region.y, region.width, region.height)
        return helper.embed(cropped)
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

    /** Vide la sélection en un coup -- même règle de persistance que [toggleBlendshapeSelection]. */
    fun deselectAllBlendshapes() {
        _uiState.update { it.copy(selectedBlendshapeNames = emptySet()) }
        if (_uiState.value.persistBlendshapeSelectionEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                appSettingsStore.setPersistedBlendshapeSelectionNames(emptySet())
            }
        }
    }

    /**
     * Remet tous les poids par blendshape au défaut neutre (1.0) -- pas de branchement sur le
     * pipeline de tracking pour l'instant (voir `AppSettingsStore.blendshapeWeights`), mais le
     * bouton associé dans l'UI passe par une confirmation avant d'appeler cette fonction, la perte
     * de réglage étant coûteuse une fois l'UI de réglage fine construite.
     */
    fun resetBlendshapeWeights() {
        viewModelScope.launch(Dispatchers.IO) {
            appSettingsStore.resetBlendshapeWeights()
        }
        _uiState.update { it.copy(blendshapeWeights = emptyMap()) }
    }

    // --- Réglages généraux (persistés) ---

    /**
     * Sélecteur de langue en-app (point 30) : force la langue de l'app indépendamment de la langue
     * système, sur toutes les versions d'Android (contrairement au sélecteur système natif, réglages
     * système > langues de l'app, réservé à Android 13+). Effet immédiat (AppCompat recrée les
     * Activity concernées) -- pas besoin de relancer l'app. Persistance automatique gérée par
     * AppCompat (voir AppLocalesMetadataHolderService dans AndroidManifest.xml), rien à écrire
     * nous-mêmes dans ConnectionSettingsStore/AppSettingsStore.
     */
    fun setAppLanguage(language: AppLanguage) {
        val localeList = if (language.languageTag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
        _uiState.update { it.copy(appLanguage = language) }
    }

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
     * Niveau minimal conservé dans le fichier de logs exportable (voir logging/AppLog.kt, revue
     * technique point 50) -- persisté, réglable depuis AdvancedSettingsScreen, effet immédiat (voir
     * le collecteur `appSettingsStore.logLevel` dans init{}).
     */
    fun setLogLevel(level: LogLevel) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setLogLevel(level) }
    }

    /**
     * Mode miroir (point 51) -- persisté, réglable dans les réglages, effet immédiat (voir le
     * collecteur `appSettingsStore.mirrorModeEnabled` dans init{}).
     */
    fun setMirrorModeEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setMirrorModeEnabled(enabled) }
    }

    /**
     * Détection expérimentale de la langue tirée (point 15) -- persisté, réglable depuis
     * `ExperimentalFeaturesScreen`, effet immédiat (voir le collecteur
     * `appSettingsStore.tongueOutDetectionEnabled` dans init{}). Désactivé par défaut.
     */
    fun setTongueOutDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setTongueOutDetectionEnabled(enabled) }
    }

    /**
     * Crée [tongueEmbeddingHelper] à la demande (voir kdoc de son champ) -- no-op s'il existe déjà
     * ou si [currentTierConfig] n'est pas encore connu (le toggle a été activé avant la fin
     * d'`initializeTracking()`, cas limite non géré finement pour l'instant : le prochain
     * changement du toggle réessaiera).
     */
    private fun ensureTongueEmbeddingHelper() {
        if (tongueEmbeddingHelper != null) return
        val tierConfig = currentTierConfig ?: return
        tongueEmbeddingHelper = TongueEmbeddingHelper(
            context = getApplication(),
            tierConfig = tierConfig,
            forceGpuUnavailable = currentDebugForceGpuUnavailable,
            onError = { message -> AppLog.w(TAG, "Échec d'initialisation de TongueEmbeddingHelper : $message") },
        ).also { it.setup() }
        // Log toujours actif (pas gaté par TONGUE_DIAGNOSTIC_LOGGING) : confirmer que le helper
        // s'est bien construit était justement ce qui manquait pour diagnostiquer la course
        // d'initialisation corrigée juste au-dessus -- un simple silence ne dit pas "jamais tenté"
        // de "tenté et réussi".
        AppLog.i(TAG, "TongueEmbeddingHelper initialisé (délégué GPU=${tongueEmbeddingHelper?.activeDelegateIsGpu})")
    }

    /**
     * Bouton "Démarrer" de `TongueCalibrationScreen` -- lance la première phase, désormais une
     * pause de préparation avant "langue dehors" (voir `newTongueCalibrationRecording`, ajoutée le
     * 13 août 2026). `tongueCalibrationLastTimestampMs` remis à `null` : la toute première frame de
     * calibration n'a pas de delta de temps significatif, voir son usage dans
     * `handleTrackingResult()`.
     */
    fun startTongueCalibration() {
        tongueCalibrationRecordingState = newTongueCalibrationRecording()
        tongueCalibrationLastTimestampMs = null
        // Démarre en PREPARE_TONGUE_OUT (voir newTongueCalibrationRecording) -- décompte initial
        // sur la durée de pause, pas d'enregistrement, pour un premier affichage cohérent avec le
        // décompte réel plutôt qu'un saut visible au premier tick.
        val initialSecondsRemaining = kotlin.math.ceil(DEFAULT_CALIBRATION_PREPARE_DURATION_MS / 1000f).toInt()
        _uiState.update {
            it.copy(
                tongueCalibrationPhase = tongueCalibrationRecordingState.phase,
                tongueCalibrationSecondsRemaining = initialSecondsRemaining,
            )
        }
    }

    /** Annule une calibration en cours -- remet la machine à IDLE, ne touche pas à une calibration déjà sauvegardée. */
    fun cancelTongueCalibration() {
        tongueCalibrationRecordingState = TongueCalibrationRecordingState()
        tongueCalibrationLastTimestampMs = null
        _uiState.update { it.copy(tongueCalibrationPhase = TongueCalibrationPhase.IDLE, tongueCalibrationSecondsRemaining = null) }
    }

    /** Réglages debug (durée d'enregistrement / marge de classification), voir `AppSettingsStore`. */
    fun setTongueCalibrationRecordingDurationMs(durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setTongueCalibrationRecordingDurationMs(durationMs) }
    }

    fun setTongueClassificationMargin(margin: Float) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setTongueClassificationMargin(margin) }
    }

    /**
     * Construit l'intent de partage du fichier de logs (bouton "Partager les logs",
     * AdvancedSettingsScreen) -- `null` si rien n'a encore été loggé au niveau configuré (pas
     * d'erreur affichée dans ce cas, juste rien à partager). URI `content://` via [FileProvider]
     * (voir AndroidManifest.xml/res/xml/file_paths.xml) plutôt qu'une URI `file://` directe,
     * bloquée par le système sur les versions récentes d'Android (`FileUriExposedException`).
     * Non testable en JVM (accès disque + `FileProvider` réel) -- même catégorie que le reste du
     * glue code Android de ce ViewModel.
     */
    fun buildShareLogsIntent(): Intent? {
        val context = getApplication<Application>()
        if (!hasLogsToShare()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile())
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Y a-t-il quoi que ce soit à partager ? Vérification légère (existence + taille), pas de lecture. */
    fun hasLogsToShare(): Boolean = logFile().let { it.exists() && it.length() > 0L }

    private fun logFile(): File = File(File(getApplication<Application>().filesDir, "logs"), AppLog.LOG_FILE_NAME)

    /**
     * Force un palier de tracking (ou `null` pour revenir à la sélection automatique) -- persisté,
     * réglable depuis Diagnostics. Ne s'applique qu'au prochain lancement de l'app, voir
     * [MainUiState.tierOverride] et [initializeTracking].
     */
    fun setTierOverride(tier: TrackingTier?) {
        viewModelScope.launch(Dispatchers.IO) { appSettingsStore.setTierOverride(tier) }
    }

    // --- Mocks de debug (panneau caché de AdvancedSettingsScreen, voir revue technique point 35) ---

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
     * déverrouillé ET depuis l'indicateur toujours visible (voir AdvancedSettingsScreen), sans avoir à
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
        arCoreHeadPoseTracker?.setBackgroundRenderingEnabled(false)
    }

    private fun exitPowerSave() {
        _uiState.update { it.copy(isPowerSaveActive = false) }
        cameraController?.setPreviewEnabled(true)
        arCoreHeadPoseTracker?.setBackgroundRenderingEnabled(true)
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
                lateinit var sender: VmcOscSender
                sender = VmcOscSender(address, port, onConnectionLost = { onVmcConnectionLost(sender) })
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
                    AppLog.i(TAG, "VMC connecté à $hostText:$port")
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
        val wasConnected = _uiState.value.vmcEnabled
        vmcSender?.close()
        vmcSender = null
        _uiState.update { it.copy(vmcEnabled = false, vmcConnecting = false, vmcTargetLabel = "") }
        if (wasConnected) AppLog.i(TAG, "VMC déconnecté")
    }

    /**
     * Callback de [VmcOscSender.onConnectionLost] (voir son kdoc) -- déclenché depuis le thread
     * d'envoi dédié de [VmcOscSender] quand un envoi révèle que plus personne n'écoute côté cible
     * (ex. Blender/Unity fermé). Revue de code, 13 août 2026 : l'icône restait "connecté"
     * indéfiniment jusqu'ici, [vmcEnabled][MainUiState.vmcEnabled] n'étant qu'un interrupteur local
     * jamais remis à jour par une vraie perte de connexion.
     *
     * Vérifie l'identité de [sender] avant de toucher à l'état : si l'utilisateur s'est reconnecté
     * entretemps ([vmcSender] a changé), ce callback appartient à une connexion déjà abandonnée.
     */
    private fun onVmcConnectionLost(sender: VmcOscSender) {
        if (vmcSender !== sender) return
        vmcSender?.close()
        vmcSender = null
        _uiState.update { it.copy(vmcEnabled = false, vmcConnecting = false, vmcTargetLabel = "") }
        AppLog.i(TAG, "VMC déconnecté (cible injoignable)")
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
                    onStateChanged = { state ->
                        _uiState.update { it.copy(vtsConnectionState = state) }
                        // ParametersRegistered = seul état "vraiment prêt" de la machine à état
                        // (voir VTubeStudioConnectionState) -- ne se déclenche qu'une fois par cycle
                        // de connexion réussi, pas de garde anti-doublon nécessaire ici.
                        if (state == VTubeStudioConnectionState.ParametersRegistered) {
                            AppLog.i(TAG, "VTube Studio connecté et paramètres enregistrés")
                        }
                    },
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
        val wasConnected = _uiState.value.vtsConnectionState != VTubeStudioConnectionState.Disconnected
        vtubeStudioSender?.close()
        vtubeStudioSender = null
        _uiState.update { it.copy(vtsTargetLabel = "", vtsConnectionState = VTubeStudioConnectionState.Disconnected) }
        if (wasConnected) AppLog.i(TAG, "VTube Studio déconnecté")
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
            // connectedTo non-nul = handshake reçu (voir IFacialMocapSender) ; nul = déconnexion,
            // soit un arrêt propre côté PC (STOP_HANDSHAKE), soit une cible devenue injoignable
            // détectée au prochain envoi (revue de code, 13 août 2026 -- voir IFacialMocapSender.send()
            // pour le détail, déjà loggé côté sender dans ce second cas) -- distinct de
            // stopIFacialMocapListening() ci-dessous (arrêt côté app).
            if (connectedTo != null) {
                AppLog.i(TAG, "UDP/VBridger connecté depuis $connectedTo")
            } else {
                AppLog.i(TAG, "UDP/VBridger déconnecté")
            }
        }.also { iFacialMocapSender = it }
        sender.startListening()
        _uiState.update { it.copy(iFacialMocapListening = true) }
    }

    fun stopIFacialMocapListening() {
        val wasListening = _uiState.value.iFacialMocapListening
        iFacialMocapSender?.stopListening()
        _uiState.update { it.copy(iFacialMocapListening = false, iFacialMocapConnectedTo = null) }
        if (wasListening) AppLog.i(TAG, "UDP/VBridger écoute arrêtée")
    }

    override fun onCleared() {
        super.onCleared()
        cameraController?.stop()
        arCoreHeadPoseTracker?.stop()
        arCoreHeadPoseTracker?.close()
        faceLandmarkerHelper?.close()
        tongueEmbeddingHelper?.close()
        vmcSender?.close()
        iFacialMocapSender?.stopListening()
        vtubeStudioSender?.close()
        deviceOrientationTracker?.stop()
        calibrationCountdownJob?.cancel()
        powerSaveTimerJob?.cancel()
        thermalPollingJob?.cancel()
    }
}
