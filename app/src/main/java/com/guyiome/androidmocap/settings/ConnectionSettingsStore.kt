package com.guyiome.androidmocap.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Type de connexion actif pour le bouton de l'écran principal -- choisi et persisté depuis les réglages. */
enum class ConnectionType {
    VMC,
    IFACIALMOCAP,
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
    }

    val connectionType: Flow<ConnectionType?> = context.connectionSettingsDataStore.data.map { prefs ->
        prefs[Keys.CONNECTION_TYPE]?.let { raw -> runCatching { ConnectionType.valueOf(raw) }.getOrNull() }
    }

    val vmcHost: Flow<String?> = context.connectionSettingsDataStore.data.map { prefs -> prefs[Keys.VMC_HOST] }

    suspend fun setConnectionType(type: ConnectionType) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.CONNECTION_TYPE] = type.name }
    }

    suspend fun setVmcHost(host: String) {
        context.connectionSettingsDataStore.edit { prefs -> prefs[Keys.VMC_HOST] = host }
    }
}
