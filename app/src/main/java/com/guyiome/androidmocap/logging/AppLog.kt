package com.guyiome.androidmocap.logging

import android.util.Log
import com.guyiome.androidmocap.BuildConfig
import java.io.File

/**
 * Point d'entrée unique de journalisation de l'app (revue technique, point 50) -- remplace les
 * appels directs à `android.util.Log` dans le reste du code, pour deux raisons : un format
 * cohérent d'un appel à l'autre, et un chemin secondaire vers un fichier exportable par
 * l'utilisateur (voir `ui/LoggingSettingsScreen.kt`), en plus de `logcat`.
 *
 * - [v]/[d] (verbose/debug) sont des no-op hors build debug ([BuildConfig.DEBUG]) -- ni `logcat`,
 *   ni fichier -- convention Android officielle (voir kdoc de [LogLevel]), et garantie explicite en
 *   code plutôt que déléguée à une règle R8 (`-assumenosideeffects`) dont l'effet serait incertain
 *   ici vu `-dontoptimize` (revue technique, point 8).
 * - [i]/[w]/[e] partent toujours vers `logcat`, et vers le fichier persistant si [minimumPersistedLevel]
 *   l'autorise ([shouldPersist]) -- ERROR par défaut, réglable jusqu'à INFO (`AppSettingsStore.logLevel`).
 * - Les adresses IP du message sont masquées ([maskIpAddresses]) avant écriture fichier, sauf en
 *   build debug -- IP en clair uniquement sur la machine de dev, jamais dans un fichier qu'un
 *   utilisateur pourrait exporter et partager (revue technique, point 50, risque RGPD discuté).
 *
 * Le fichier est réécrit en entier à chaque appel persisté (lecture + [appendWithRotation] +
 * écriture) plutôt qu'un simple append -- plus simple à raisonner/tester, et largement suffisant vu
 * la fréquence réelle des événements INFO/WARN/ERROR de cette app (connexions/erreurs ponctuelles,
 * pas le flux 20-60Hz du tracking qui reste, lui, en dessous du seuil DEBUG). Une erreur d'écriture
 * (disque plein, etc.) est avalée silencieusement -- la journalisation ne doit jamais faire planter
 * l'app ni elle-même déclencher un nouveau log en cascade.
 */
internal object AppLog {

    /** ~1,5 Mo en caractères (approximation -- contenu très majoritairement ASCII, voir kdoc du fichier). */
    private const val MAX_LOG_FILE_CHARS = 1_500_000

    const val LOG_FILE_NAME = "app_logs.txt"

    @Volatile private var logFile: File? = null

    @Volatile private var minimumPersistedLevel: LogLevel = LogLevel.ERROR

    /**
     * À appeler une seule fois, au plus tôt (voir `MoCapApplication.onCreate()`). Le fichier vit
     * dans un sous-dossier dédié (`filesDir/logs/`) plutôt qu'à la racine de `filesDir` -- portée la
     * plus étroite possible pour la déclaration `FileProvider` du partage (`res/xml/file_paths.xml`),
     * qui n'expose ainsi que ce sous-dossier et rien d'autre du stockage privé de l'app.
     */
    fun init(filesDir: File) {
        val logsDir = File(filesDir, "logs").apply { mkdirs() }
        logFile = File(logsDir, LOG_FILE_NAME)
    }

    /** Reflète `AppSettingsStore.logLevel` -- appelé à chaque changement, pas seulement au lancement. */
    fun setMinimumPersistedLevel(level: LogLevel) {
        minimumPersistedLevel = level
    }

    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.v(tag, message)
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        persist(LogLevel.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        persist(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        persist(LogLevel.ERROR, tag, message, throwable)
    }

    private fun persist(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!shouldPersist(level, minimumPersistedLevel)) return
        val file = logFile ?: return
        val safeMessage = if (BuildConfig.DEBUG) message else maskIpAddresses(message)
        val line = formatLogLine(level, tag, safeMessage, System.currentTimeMillis(), throwable)
        runCatching {
            val existing = if (file.exists()) file.readText() else ""
            file.writeText(appendWithRotation(existing, line, MAX_LOG_FILE_CHARS))
        }
    }
}
