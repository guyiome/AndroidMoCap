package com.guyiome.androidmocap.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guyiome.androidmocap.logging.LogLevel
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
     * Force un palier de tracking spécifique au lieu de la sélection automatique -- `null` =
     * automatique (comportement par défaut, voir `TrackingTierSelector.select`). Pensé comme
     * outil de diagnostic (ex. forcer `STANDARD` sur un appareil qui qualifierait pour `OPTIMAL`,
     * pour tester le chemin CameraX sans dépendre d'un second appareil), pas une fonctionnalité
     * utilisateur normale -- voir `DiagnosticsScreen`. Lu une seule fois au lancement
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
     * Mock de debug (panneau caché de `DiagnosticsScreen`, voir revue technique point 35) : force
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
     * l'indicateur toujours visible de `DiagnosticsScreen` sans avoir à refaire le geste de
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
     * `LoggingSettingsScreen`. `VERBOSE`/`DEBUG` ne sont volontairement pas des valeurs valides ici
     * (convention Android officielle -- jamais persistés, voir kdoc de `LogLevel`) ; une valeur
     * stockée invalide ou absente retombe sur `ERROR` plutôt que de planter.
     */
    val logLevel: Flow<LogLevel> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.LOG_LEVEL]?.let { name -> runCatching { LogLevel.valueOf(name) }.getOrNull() } ?: LogLevel.ERROR
    }

    suspend fun setLogLevel(level: LogLevel) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LOG_LEVEL] = level.name }
    }
}
