package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Partie impure de la vérification de mise à jour semi-automatique -- un unique appel
 * réseau, pas testable en JVM pur (même catégorie que [VmcOscSender.send]/[IFacialMocapSender]),
 * voir `GitHubReleaseCheck.kt` pour le parsing/la comparaison de version, tous deux purs et testés.
 *
 * `HttpURLConnection` plutôt qu'une dépendance HTTP dédiée (OkHttp...) -- un seul GET simple,
 * cohérent avec le reste de ce module qui évite les dépendances réseau au-delà du strict
 * nécessaire (`DatagramSocket` brut pour VMC/iFacialMocap, nv-websocket-client seulement parce que
 * WebSocket n'a pas d'équivalent JDK).
 *
 * Best-effort : toute erreur (réseau, JSON, timeout, limite de taux GitHub...) est journalisée et
 * renvoie `null` plutôt que de propager une exception -- cette vérification est un confort, jamais
 * un prérequis au fonctionnement de l'app.
 */
class UpdateChecker {
    companion object {
        private const val TAG = "UpdateChecker"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/guyiome/AndroidMoCap/releases/latest"
        private const val TIMEOUT_MS = 5_000
    }

    internal suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                // Sans en-tête User-Agent, l'API GitHub répond 403 -- exigence documentée côté
                // GitHub, pas propre à ce projet.
                setRequestProperty("User-Agent", "AndroidMoCap")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                AppLog.w(TAG, "Vérification de mise à jour : réponse HTTP ${connection.responseCode}")
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseLatestReleaseJson(body)
        } catch (e: Exception) {
            AppLog.w(TAG, "Vérification de mise à jour échouée (réseau ou parsing)", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
