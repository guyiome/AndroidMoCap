package com.guyiome.androidmocap.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guyiome.androidmocap.logging.LogLevel
import com.guyiome.androidmocap.tracking.BlendshapeCatalog
import com.guyiome.androidmocap.tracking.DEFAULT_CALIBRATION_RECORDING_DURATION_MS
import com.guyiome.androidmocap.tracking.DEFAULT_CLASSIFICATION_MARGIN
import com.guyiome.androidmocap.tracking.TrackingTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/** Seuil par défaut (%) en dessous duquel l'alerte batterie faible s'affiche. */
const val DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT = 20

/** Délai par défaut (secondes) d'inactivité avant de basculer en mode économie d'énergie. */
const val DEFAULT_POWER_SAVE_DELAY_SECONDS = 30

/**
 * Réglages généraux de l'app, persistés entre les lancements. La sélection de blendshapes
 * affichées reste non persistée par défaut (remise à zéro à chaque lancement, comme demandé
 * initialement) -- [persistBlendshapeSelectionEnabled] permet de changer ce comportement au cas
 * par cas, voir sa doc.
 */
class AppSettingsStore(private val context: Context) {

    private object Keys {
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold_percent")
        val POWER_SAVE_MODE = booleanPreferencesKey("power_save_mode_enabled")
        val POWER_SAVE_DELAY = intPreferencesKey("power_save_delay_seconds")
        val FACE_MESH_OVERLAY = booleanPreferencesKey("face_mesh_overlay_enabled")
        val KEEP_MESH_OVERLAY_IN_POWER_SAVE = booleanPreferencesKey("keep_mesh_overlay_in_power_save")
        val PERSIST_BLENDSHAPE_SELECTION = booleanPreferencesKey("persist_blendshape_selection_enabled")
        val PERSISTED_BLENDSHAPE_SELECTION = stringSetPreferencesKey("persisted_blendshape_selection")
        val TIER_OVERRIDE = stringPreferencesKey("tier_override")
        val DEBUG_FORCE_ARCORE_UNAVAILABLE = booleanPreferencesKey("debug_force_arcore_unavailable")
        val DEBUG_FORCE_GPU_UNAVAILABLE = booleanPreferencesKey("debug_force_gpu_unavailable")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val MIRROR_MODE = booleanPreferencesKey("mirror_mode_enabled")
        val TONGUE_OUT_DETECTION = booleanPreferencesKey("tongue_out_detection_enabled")
        val TONGUE_REFERENCES_CALIBRATED = booleanPreferencesKey("tongue_references_calibrated")
        val TONGUE_CALIBRATION_RECORDING_DURATION_MS = longPreferencesKey("tongue_calibration_recording_duration_ms")
        val TONGUE_CLASSIFICATION_MARGIN = floatPreferencesKey("tongue_classification_margin")
        val LAST_UPDATE_CHECK_TIMESTAMP_MS = longPreferencesKey("last_update_check_timestamp_ms")
    }

    val lowBatteryThresholdPercent: Flow<Int> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.LOW_BATTERY_THRESHOLD] ?: DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT
    }

    suspend fun setLowBatteryThresholdPercent(percent: Int) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LOW_BATTERY_THRESHOLD] = percent }
    }

    /**
     * Autorise le passage automatique en mode économie d'énergie après [powerSaveDelaySeconds]
     * d'inactivité (aucun toucher à l'écran) -- coupe alors l'aperçu caméra affiché (l'analyse
     * MediaPipe continue normalement, elle ne dépend pas de l'aperçu) et assombrit l'écran au
     * minimum. On en ressort au moindre toucher.
     */
    val powerSaveModeEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.POWER_SAVE_MODE] ?: false
    }

    suspend fun setPowerSaveModeEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.POWER_SAVE_MODE] = enabled }
    }

    /** Délai d'inactivité (secondes) avant bascule automatique en mode économie d'énergie. */
    val powerSaveDelaySeconds: Flow<Int> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.POWER_SAVE_DELAY] ?: DEFAULT_POWER_SAVE_DELAY_SECONDS
    }

    suspend fun setPowerSaveDelaySeconds(seconds: Int) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.POWER_SAVE_DELAY] = seconds }
    }

    /** Superpose les points du mesh facial MediaPipe sur l'aperçu caméra -- désactivé par défaut. */
    val faceMeshOverlayEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.FACE_MESH_OVERLAY] ?: false
    }

    suspend fun setFaceMeshOverlayEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.FACE_MESH_OVERLAY] = enabled }
    }

    /**
     * Garde l'overlay du mesh visible même en mode économie d'énergie (tous paliers confondus) --
     * désactivé par défaut, l'overlay disparaît alors en mode éco comme le reste de l'aperçu. Voir
     * `ui/MeshOverlayVisibility.kt`.
     */
    val keepMeshOverlayInPowerSave: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.KEEP_MESH_OVERLAY_IN_POWER_SAVE] ?: false
    }

    suspend fun setKeepMeshOverlayInPowerSave(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.KEEP_MESH_OVERLAY_IN_POWER_SAVE] = enabled }
    }

    /**
     * Désactivé par défaut (voir rapport technique, point 18) : la sélection de blendshapes
     * affichés ([persistedBlendshapeSelectionNames]) n'est mémorisée que si ce réglage est activé
     * -- ne change rien au comportement historique tant que l'utilisateur ne l'active pas lui-même.
     */
    val persistBlendshapeSelectionEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.PERSIST_BLENDSHAPE_SELECTION] ?: false
    }

    suspend fun setPersistBlendshapeSelectionEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.PERSIST_BLENDSHAPE_SELECTION] = enabled }
    }

    /** Dernière sélection de blendshapes sauvegardée, utilisée uniquement si [persistBlendshapeSelectionEnabled]. */
    val persistedBlendshapeSelectionNames: Flow<Set<String>> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.PERSISTED_BLENDSHAPE_SELECTION] ?: emptySet()
    }

    suspend fun setPersistedBlendshapeSelectionNames(names: Set<String>) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.PERSISTED_BLENDSHAPE_SELECTION] = names }
    }

    /**
     * Poids par blendshape -- une [floatPreferencesKey] par entrée du catalogue plutôt qu'un blob
     * unique (JSON ou autre) : cohérent avec le reste de ce fichier (aucune dépendance de
     * sérialisation ici), et une réinitialisation devient une simple suppression de clés plutôt
     * qu'une réécriture de structure. Défaut 1.0 (poids neutre) pour toute entrée absente -- couvre
     * aussi bien "jamais réglé" que "juste réinitialisé". Non branché sur le pipeline de tracking
     * pour l'instant (pas d'UI de réglage fine) -- voir `MainViewModel.handleTrackingResult`.
     */
    val blendshapeWeights: Flow<Map<String, Float>> = context.appSettingsDataStore.data.map { prefs ->
        BlendshapeCatalog.all.associate { (_, name) -> name to (prefs[weightKey(name)] ?: 1f) }
    }

    suspend fun setBlendshapeWeight(name: String, weight: Float) {
        context.appSettingsDataStore.edit { prefs -> prefs[weightKey(name)] = weight }
    }

    /** Remet tous les poids au défaut neutre (1.0) en supprimant leurs clés plutôt qu'en les réécrivant. */
    suspend fun resetBlendshapeWeights() {
        context.appSettingsDataStore.edit { prefs ->
            BlendshapeCatalog.all.forEach { (_, name) -> prefs.remove(weightKey(name)) }
        }
    }

    private fun weightKey(name: String) = floatPreferencesKey("blendshape_weight_$name")

    /**
     * Force un palier de tracking spécifique au lieu de la sélection automatique -- `null` =
     * automatique (comportement par défaut, voir `TrackingTierSelector.select`). Pensé comme
     * outil de diagnostic (ex. forcer `STANDARD` sur un appareil qui qualifierait pour `OPTIMAL`,
     * pour tester le chemin CameraX sans dépendre d'un second appareil), pas une fonctionnalité
     * utilisateur normale -- voir `AdvancedSettingsScreen`. Lu une seule fois au lancement
     * (`initializeTracking`, comme `persistBlendshapeSelectionEnabled`) : un changement en cours
     * de session ne s'applique qu'au prochain redémarrage de l'app, le pipeline caméra/MediaPipe
     * n'étant pas reconstruit à chaud.
     */
    val tierOverride: Flow<TrackingTier?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.TIER_OVERRIDE]?.let { name -> runCatching { TrackingTier.valueOf(name) }.getOrNull() }
    }

    suspend fun setTierOverride(tier: TrackingTier?) {
        context.appSettingsDataStore.edit { prefs ->
            if (tier == null) prefs.remove(Keys.TIER_OVERRIDE) else prefs[Keys.TIER_OVERRIDE] = tier.name
        }
    }

    /**
     * Mock de debug (panneau caché de `AdvancedSettingsScreen`, voir revue technique point 35) : force
     * le repli CameraX au palier `OPTIMAL` même sur un appareil qui supporte réellement ARCore --
     * permet de vérifier `ArCoreHeadPoseTracker.onUnavailable` sans dépendre d'un appareil
     * incompatible. Désactivé par défaut. Persisté mais lu une seule fois au lancement
     * (`initializeTracking`, comme [tierOverride]) : pas de reconstruction à chaud du pipeline
     * caméra/MediaPipe, un changement ne s'applique qu'au prochain redémarrage complet de l'app.
     */
    val debugForceArCoreUnavailable: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.DEBUG_FORCE_ARCORE_UNAVAILABLE] ?: false
    }

    suspend fun setDebugForceArCoreUnavailable(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.DEBUG_FORCE_ARCORE_UNAVAILABLE] = enabled }
    }

    /**
     * Mock de debug : force le repli CPU au lieu du délégué GPU dans `FaceLandmarkerHelper`, même
     * sur un appareil qui le supporte -- même principe et mêmes contraintes que
     * [debugForceArCoreUnavailable] ci-dessus.
     */
    val debugForceGpuUnavailable: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.DEBUG_FORCE_GPU_UNAVAILABLE] ?: false
    }

    suspend fun setDebugForceGpuUnavailable(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.DEBUG_FORCE_GPU_UNAVAILABLE] = enabled }
    }

    /**
     * Réinitialise en un seul appel les mocks de debug persistés ci-dessus -- accessible depuis
     * l'indicateur toujours visible de `AdvancedSettingsScreen` sans avoir à refaire le geste de
     * déverrouillage juste pour corriger un mock resté actif par erreur. Ne touche pas à
     * [tierOverride] (réglage diagnostic préexistant et distinct) ni au mock thermique
     * (`MainUiState.debugThermalOverride`, volontairement non persisté -- voir `MainViewModel`).
     */
    suspend fun resetDebugOverrides() {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DEBUG_FORCE_ARCORE_UNAVAILABLE] = false
            prefs[Keys.DEBUG_FORCE_GPU_UNAVAILABLE] = false
        }
    }

    /**
     * Niveau minimal conservé dans le fichier de logs exportable (voir `logging/AppLog.kt`, revue
     * technique point 50) -- `ERROR` par défaut, réglable jusqu'à `WARN`/`INFO` depuis
     * `AdvancedSettingsScreen`. `VERBOSE`/`DEBUG` ne sont volontairement pas des valeurs valides ici
     * (convention Android officielle -- jamais persistés, voir kdoc de `LogLevel`) ; une valeur
     * stockée invalide ou absente retombe sur `ERROR` plutôt que de planter.
     */
    val logLevel: Flow<LogLevel> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.LOG_LEVEL]?.let { name -> runCatching { LogLevel.valueOf(name) }.getOrNull() } ?: LogLevel.ERROR
    }

    suspend fun setLogLevel(level: LogLevel) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LOG_LEVEL] = level.name }
    }

    /**
     * Mode miroir (revue technique, point 51) : mirrore ensemble la tête et tous les blendshapes
     * gauche/droite avant envoi ([com.guyiome.androidmocap.tracking.mirrorFaceTrackingResult]) --
     * **activé par défaut** : l'aperçu caméra/mesh à l'écran est déjà mirroré en permanence
     * (indépendant de ce réglage, voir `FaceMeshOverlay`), donc avoir la donnée réseau mirorée par
     * défaut évite un décalage day-one entre ce que l'utilisateur voit et ce que fait l'avatar --
     * cohérent aussi avec la convention la plus répandue côté VTubing (VBridger et consorts).
     * Toujours réglable : désactiver ce switch envoie la sortie native/anatomique à la place.
     */
    val mirrorModeEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.MIRROR_MODE] ?: true
    }

    suspend fun setMirrorModeEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.MIRROR_MODE] = enabled }
    }

    /**
     * Détection expérimentale de la langue tirée (revue technique, point 15) -- **désactivée par
     * défaut**, rangée dans "Fonctionnalités expérimentales". Active la cascade à 3 étages (porte
     * `jawOpen`+`mouthOpennessRatio`, analyse couleur, classification par embedding calibrée) et le
     * logging de diagnostic (`TONGUE_DIAGNOSTIC_LOGGING`, `MainViewModel`).
     *
     * `tongueOut` est réellement injecté dans les données envoyées aux protocoles réseau depuis le
     * 13 août 2026 (même toggle, pas de réglage séparé) -- après deux sessions de test device
     * confirmant une nette amélioration de la fiabilité (calibration élargie au mouvement de
     * mâchoire, `TongueCalibrationRecordingState.kt`) et un anti-rebond dédié
     * (`TongueOutInjectionGate.kt`) filtrant le résidu d'erreurs isolées encore observé. Risque
     * résiduel faible mais non nul, assumé -- fonctionnalité rangée dans "Expérimental" précisément
     * pour cette raison, voir revue technique point 15 pour le détail des deux sessions.
     */
    val tongueOutDetectionEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.TONGUE_OUT_DETECTION] ?: false
    }

    suspend fun setTongueOutDetectionEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.TONGUE_OUT_DETECTION] = enabled }
    }

    /**
     * Miroir UI-only de l'état réel de la calibration de l'étage 3 (revue technique, point 15) --
     * la source de vérité reste `TongueCalibrationStore.load() != null` (fichier `filesDir`, pas
     * DataStore : deux vecteurs de floats n'ont pas leur place dans des préférences scalaires).
     * Ce flag évite juste une lecture disque réactive à chaque recomposition de l'écran de
     * calibration -- mis à jour juste après un `TongueCalibrationStore.save()` réussi.
     */
    val tongueReferencesCalibrated: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.TONGUE_REFERENCES_CALIBRATED] ?: false
    }

    suspend fun setTongueReferencesCalibrated(calibrated: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.TONGUE_REFERENCES_CALIBRATED] = calibrated }
    }

    /**
     * Unité de base pour la durée d'enregistrement de calibration -- réglable depuis un panneau
     * debug plutôt que codée en dur (voir
     * `TongueCalibrationRecordingState.DEFAULT_CALIBRATION_RECORDING_DURATION_MS`) : les étages 1/2
     * ont demandé sept sessions de réglage sur device, éviter le même cycle rebuild+reinstall pour
     * chaque essai ici. Les deux phases ("langue dehors"/"langue rentrée") en dérivent chacune par
     * leur propre multiplicateur depuis le 13 août 2026 (point 56) -- voir
     * `TONGUE_OUT_RECORDING_MULTIPLIER`/`TONGUE_IN_RECORDING_MULTIPLIER`, pas la valeur brute
     * directement.
     */
    val tongueCalibrationRecordingDurationMs: Flow<Long> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.TONGUE_CALIBRATION_RECORDING_DURATION_MS] ?: DEFAULT_CALIBRATION_RECORDING_DURATION_MS
    }

    suspend fun setTongueCalibrationRecordingDurationMs(durationMs: Long) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.TONGUE_CALIBRATION_RECORDING_DURATION_MS] = durationMs }
    }

    /** Marge de classification de l'étage 3 -- même rationale que la durée ci-dessus, voir
     *  `TongueEmbeddingClassifier.DEFAULT_CLASSIFICATION_MARGIN`. */
    val tongueClassificationMargin: Flow<Float> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.TONGUE_CLASSIFICATION_MARGIN] ?: DEFAULT_CLASSIFICATION_MARGIN
    }

    suspend fun setTongueClassificationMargin(margin: Float) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.TONGUE_CLASSIFICATION_MARGIN] = margin }
    }

    /**
     * Horodatage (epoch ms) de la dernière vérification de mise à jour réussie (point 14) -- `0L`
     * par défaut (jamais vérifié). Sert uniquement de seuil de rafraîchissement
     * (`MainViewModel.checkForUpdate`, throttle 24h) pour éviter un appel réseau à chaque
     * lancement ; mis à jour seulement après un appel réussi, pas après un échec réseau, pour
     * qu'un échec temporaire se retente au prochain lancement plutôt que d'attendre 24h.
     */
    val lastUpdateCheckTimestampMs: Flow<Long> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK_TIMESTAMP_MS] ?: 0L
    }

    suspend fun setLastUpdateCheckTimestampMs(timestampMs: Long) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LAST_UPDATE_CHECK_TIMESTAMP_MS] = timestampMs }
    }
}
