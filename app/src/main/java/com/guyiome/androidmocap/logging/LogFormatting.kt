package com.guyiome.androidmocap.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fonctions pures utilisées par [AppLog] (voir revue technique, point 50) -- formatage d'une ligne
 * de log auto-suffisante (le fichier exporté n'a pas le formatage de `logcat` pour se rattraper),
 * masquage des adresses IP et politique de rotation par taille. Testées en JVM, sans dépendance
 * Android (pas de `android.util.Log` ici -- `Log.getStackTraceString()` est réimplémenté avec
 * `java.io.PrintWriter`/`StringWriter`, standard JVM, pour rester testable sans stub Android).
 */

/**
 * Une ligne par entrée, format `AAAA-MM-JJ HH:mm:ss.SSS NIVEAU/TAG: message`, suivie de la pile
 * d'appel sur les lignes suivantes si [throwable] est fourni. [timestampMs] est un paramètre
 * explicite (pas d'appel interne à `System.currentTimeMillis()`) pour rester pur et testable,
 * cohérent avec le reste du projet (`OneEuroFilter.next(elapsedMs)`, etc.).
 */
internal fun formatLogLine(
    level: LogLevel,
    tag: String,
    message: String,
    timestampMs: Long,
    throwable: Throwable? = null,
): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
    val base = "$timestamp ${level.shortCode}/$tag: $message"
    return if (throwable == null) base else "$base\n${stackTraceString(throwable)}"
}

private fun stackTraceString(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    return writer.toString().trimEnd('\n')
}

/**
 * Remplace les deux derniers octets de toute adresse IPv4 détectée dans [message] par `x`
 * (ex. `192.168.1.42` -> `192.168.x.x`) -- appliqué une seule fois au niveau du sink
 * ([AppLog]), pas à chaque site d'appel, donc peu importe la formulation exacte utilisée par
 * `VmcOscSender`/`VTubeStudioSender`/etc. Sur-masquage volontairement accepté comme filet de
 * sécurité (une chaîne à 4 groupes de chiffres qui ne serait pas une IP serait aussi masquée) plutôt
 * que l'inverse -- ne pas laisser fuiter une vraie IP est plus important que la précision du motif.
 */
internal fun maskIpAddresses(message: String): String =
    IPV4_PATTERN.replace(message) { match -> "${match.groupValues[1]}.${match.groupValues[2]}.x.x" }

private val IPV4_PATTERN = Regex("""\b(\d{1,3})\.(\d{1,3})\.\d{1,3}\.\d{1,3}\b""")

/**
 * Ajoute [newLine] à [existingContent] (séparées par un saut de ligne), puis tronque par le début
 * si le résultat dépasse [maxSizeChars] -- garde les entrées les plus récentes, jamais les plus
 * anciennes. Coupe proprement sur la première frontière de ligne trouvée après troncature plutôt que
 * de laisser une ligne partielle illisible en tête de fichier. Taille en caractères, pas en octets
 * réels (approximation volontaire -- le contenu loggé est très majoritairement ASCII, voir kdoc
 * d'[AppLog] pour la valeur retenue).
 */
internal fun appendWithRotation(existingContent: String, newLine: String, maxSizeChars: Int): String {
    val combined = if (existingContent.isEmpty()) newLine else "$existingContent\n$newLine"
    if (combined.length <= maxSizeChars) return combined
    val truncated = combined.takeLast(maxSizeChars)
    val firstNewline = truncated.indexOf('\n')
    return if (firstNewline >= 0) truncated.substring(firstNewline + 1) else truncated
}

/** [entryLevel] doit-il être conservé dans le fichier exportable vu le seuil [configuredMinimum] ? */
internal fun shouldPersist(entryLevel: LogLevel, configuredMinimum: LogLevel): Boolean =
    entryLevel.ordinal >= configuredMinimum.ordinal
