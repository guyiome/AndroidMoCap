package com.guyiome.androidmocap.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Partie pure de la vérification de mise à jour semi-automatique (point 14) -- parsing de la
 * réponse `GET /repos/guyiome/AndroidMoCap/releases/latest` et comparaison de version, sans aucun
 * appel réseau (voir [UpdateChecker] pour la partie impure). Ce point d'entrée de l'API GitHub
 * exclut déjà lui-même les prereleases/drafts -- un tag `-beta` (voir revue technique, point 49)
 * n'est donc jamais vu ici, cohérent avec le but recherché dès la mise en place du canal beta :
 * ne jamais notifier un utilisateur "stable" d'un build de test.
 */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)

private val releaseJson = Json { ignoreUnknownKeys = true }

/** `null` si la réponse n'est pas un JSON de Release valide -- jamais d'exception propagée. */
internal fun parseLatestReleaseJson(raw: String): GitHubRelease? =
    try {
        releaseJson.decodeFromString<GitHubRelease>(raw)
    } catch (e: Exception) {
        null
    }

/**
 * `"v0.2.0"`/`"0.2.0"` -> `[0, 2, 0]`. `null` si un segment n'est pas un entier -- inclut le cas
 * d'un tag inattendu comme `"v0.3.0-beta.1"` (ne devrait normalement jamais atteindre cette
 * fonction via `/releases/latest`, voir kdoc de tête, mais reste géré sans crash si l'API change
 * de comportement).
 */
internal fun parseVersionParts(version: String): List<Int>? {
    val parts = version.removePrefix("v").split(".")
    val ints = parts.map { it.toIntOrNull() }
    return if (ints.any { it == null }) null else ints.filterNotNull()
}

/**
 * Compare deux noms de version segment par segment (`major.minor.patch...`), sans limite au
 * nombre de segments -- `"0.2"` et `"0.2.0"` sont égales (segments manquants traités comme `0`).
 * `false` si l'un des deux ne parse pas (défaut sûr : pas de notification plutôt qu'un crash ou un
 * faux positif sur une donnée illisible).
 */
internal fun isNewerVersion(current: String, latest: String): Boolean {
    val currentParts = parseVersionParts(current) ?: return false
    val latestParts = parseVersionParts(latest) ?: return false
    val length = maxOf(currentParts.size, latestParts.size)
    for (i in 0 until length) {
        val c = currentParts.getOrElse(i) { 0 }
        val l = latestParts.getOrElse(i) { 0 }
        if (l != c) return l > c
    }
    return false
}
