package com.guyiome.androidmocap.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Type de connexion actif pour le bouton de l'écran principal -- choisi et persisté depuis les réglages. */
enum class ConnectionType {
    VMC,
    IFACIALMOCAP,
    VTUBE_STUDIO,
}

private val Context.connectionSettingsDataStore by preferencesDataStore(name = "connection_settings")

/**
 * Persiste le choix du type de connexion et la dernière IP VMC utilisée, pour que l'icône de
 * connexion de l'écran principal sache quoi faire sans redemander de configuration à chaque
 * lancement de l'app.
 */
class ConnectionSettingsStore(private val context: Context) {

    private object Keys {
        val CONNECTION_TYPE = stringPreferencesKey("connection_type")
        val VMC_HOST = stringPreferencesKey("vmc_host")
        val VTS_HOST = stringPreferencesKey("vts_host")

        // Jeton d'authentification VTube Studio -- délivré une fois par popup
        // d'autorisation utilisateur, réutilisable tant qu'il n'est pas révoqué côté VTube Studio.
        val VTS_AUTH_TOKEN = stringPreferencesKey("vts_auth_token")

        // Traduction VBridger -> paramètres par défaut VTS -- voir VBridgerFormulas.kt.
        // Défaut false : comportement actuel inchangé (seuls les 52 blendshapes ARKit bruts) tant
        // que l'utilisateur n'active pas explicitement l'option.
        val VTS_USE_VBRIDGER_TRANSLATION = booleanPreferencesKey("vts_use_vbridger_translation")
    }

    val connectionType: Flow<ConnectionType?> = context.connectionSettingsDataStore.data.map { prefs ->
        prefs[Keys.CONNECTION_TYPE]?.let { raw -> runCatching { ConnectionType.valueOf(raw) }.getOrNull() }
    }

    val vmcHost: Flow<String?> = context.connectionSettingsDataStore.data.map { prefs -> prefs[Keys.VMC_HOST] }

    val vtsHost: Flow<String?> = context.connectionSettingsDataStore.data.map { prefs -> prefs[Keys.VTS_HOST] }

    val vtsAuthToken: Flow<String?> =
        context.connectionSettingsDataStore.data.map { prefs -> prefs[Keys.VTS_AUTH_TOKEN] }

    val vtsUseVBridgerTranslation: Flow<Boolean> = context.connectionSettingsDataStore.data
        .map { prefs -> prefs[Keys.VTS_USE_VBRIDGER_TRANSLATION] ?: false }

    suspend fun setConnectionType(type: ConnectionType) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.CONNECTION_TYPE] = type.name }
    }

    suspend fun setVmcHost(host: String) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.VMC_HOST] = host }
    }

    suspend fun setVtsHost(host: String) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.VTS_HOST] = host }
    }

    suspend fun setVtsAuthToken(token: String) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.VTS_AUTH_TOKEN] = token }
    }

    suspend fun setVtsUseVBridgerTranslation(enabled: Boolean) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.VTS_USE_VBRIDGER_TRANSLATION] = enabled }
    }

    /**
     * Oublie le jeton stocké -- la prochaine connexion redemande une autorisation complète (nouveau
     * popup côté VTube Studio) plutôt qu'une ré-authentification directe. Utile si le jeton a été
     * révoqué manuellement depuis le panneau "Plugin config/permissions" de VTube Studio (l'app
     * retente déjà automatiquement une fois toute seule dans ce cas, voir `VTubeStudioSender` --
     * ce réglage reste utile pour un nouveau départ explicite).
     */
    suspend fun clearVtsAuthToken() {
        context.connectionSettingsDataStore.edit { prefs -> prefs.remove(Keys.VTS_AUTH_TOKEN) }
    }
}
