package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeScore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Encodage/décodage JSON pur pour l'API Plugin de VTube Studio (WebSocket, voir [VTubeStudioSender]
 * pour la partie socket réelle) -- VTube Studio ne reçoit
 * vraisemblablement pas le protocole VMC/OSC en entrée, sa seule intégration tierce documentée est
 * cette API (`ws://<host>:8001` par défaut).
 *
 * Toutes les formes de message ci-dessous sont vérifiées sur la doc officielle
 * (`github.com/DenchiSoft/VTubeStudio`), pas supposées -- en particulier le comportement de
 * [ParameterCreationRequestData] (recréer un paramètre déjà créé par le même plugin réussit et
 * écrase min/max/défaut ; un paramètre créé par un *autre* plugin renvoie une erreur), qui permet
 * d'appeler la création à chaque connexion sans logique de détection "paramètre déjà existant".
 *
 * Toutes les fonctions de ce fichier sont pures (aucune dépendance socket/Android), testables en
 * JVM -- même esprit que [VmcOscSender.buildBundle]/[IFacialMocapSender.buildMessage]. Les
 * identifiants de requête (`requestID`) sont passés par l'appelant plutôt que générés ici, pour que
 * ces fonctions restent déterministes et testables sans dépendre d'un générateur aléatoire.
 */

private const val API_NAME = "VTubeStudioPublicAPI"
private const val API_VERSION = "1.0"

/** Affiché à l'utilisateur dans le popup d'autorisation VTube Studio. */
internal const val PLUGIN_NAME = "AndroidMoCap"
internal const val PLUGIN_DEVELOPER = "AndroidMoCap"

/**
 * `ignoreUnknownKeys` : l'API VTube Studio peut ajouter des champs (ex. `timestamp`) qu'on ignore.
 *
 * `encodeDefaults = true` -- **critique** : sans ce réglage, kotlinx.serialization omet du JSON
 * tout champ dont la valeur égale sa valeur par défaut dans la classe Kotlin. Or `apiName`,
 * `apiVersion`, `pluginName`, `pluginDeveloper` ont tous une valeur par défaut qui est justement
 * celle envoyée en pratique -- sans `encodeDefaults`, ces champs disparaissaient purement et
 * simplement du JSON envoyé (confirmé par logcat sur device, 8 août 2026 : `"data":{}` au lieu de
 * `{"pluginName":"AndroidMoCap","pluginDeveloper":"AndroidMoCap"}`), rendant VTube Studio incapable
 * d'identifier le plugin -- aucune erreur renvoyée, juste un silence total côté serveur.
 *
 * ⚠️ Piège pour la suite : le round-trip encode→décode ne détecte PAS ce genre de bug tout seul --
 * le décodage réapplique les mêmes valeurs par défaut pour les champs absents, donc les deux bugs
 * (encodage qui omet, décodage qui comble) s'annulent silencieusement à l'intérieur de ce projet.
 * Seul un vrai récepteur externe (ou une assertion sur le texte JSON brut, voir
 * `VTubeStudioProtocolTest`) peut révéler la différence.
 */
internal val vtsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class VtsEnvelope(
    val apiName: String = API_NAME,
    val apiVersion: String = API_VERSION,
    val requestID: String,
    val messageType: String,
    val data: JsonElement? = null,
)

@Serializable
internal data class AuthenticationTokenRequestData(
    val pluginName: String = PLUGIN_NAME,
    val pluginDeveloper: String = PLUGIN_DEVELOPER,
)

@Serializable
internal data class AuthenticationTokenResponseData(
    val authenticationToken: String,
)

@Serializable
internal data class AuthenticationRequestData(
    val pluginName: String = PLUGIN_NAME,
    val pluginDeveloper: String = PLUGIN_DEVELOPER,
    val authenticationToken: String,
)

@Serializable
internal data class AuthenticationResponseData(
    val authenticated: Boolean,
    val reason: String = "",
)

@Serializable
internal data class ParameterCreationRequestData(
    val parameterName: String,
    val explanation: String = "Blendshape ARKit envoyé par AndroidMoCap",
    val min: Float = -1f,
    val max: Float = 1f,
    val defaultValue: Float = 0f,
)

@Serializable
internal data class ParameterCreationResponseData(
    val parameterName: String,
)

@Serializable
internal data class ParameterValue(
    val id: String,
    val value: Float,
)

@Serializable
internal data class InjectParameterDataRequestData(
    val faceFound: Boolean,
    val mode: String = "set",
    val parameterValues: List<ParameterValue>,
)

@Serializable
internal data class ApiErrorData(
    val errorID: Int,
    val message: String,
)

private inline fun <reified T> buildMessageJson(requestId: String, messageType: String, data: T): String {
    val envelope = VtsEnvelope(
        requestID = requestId,
        messageType = messageType,
        data = vtsJson.encodeToJsonElement(data),
    )
    return vtsJson.encodeToString(envelope)
}

internal fun buildAuthTokenRequestJson(requestId: String): String =
    buildMessageJson(requestId, "AuthenticationTokenRequest", AuthenticationTokenRequestData())

internal fun buildAuthRequestJson(requestId: String, authenticationToken: String): String =
    buildMessageJson(requestId, "AuthenticationRequest", AuthenticationRequestData(authenticationToken = authenticationToken))

internal fun buildParameterCreationRequestJson(requestId: String, parameterName: String): String =
    buildMessageJson(requestId, "ParameterCreationRequest", ParameterCreationRequestData(parameterName = parameterName))

/**
 * Un `ParameterCreationRequest` par nom -- l'API n'accepte qu'un seul paramètre par requête (voir
 * doc). [requestId] sert de préfixe, suffixé par l'index pour rester unique par message tout en
 * restant déterministe (donc testable) plutôt que d'appeler un générateur aléatoire ici.
 */
internal fun buildParameterCreationRequestsJson(requestIdPrefix: String, parameterNames: List<String>): List<String> =
    parameterNames.mapIndexed { index, name -> buildParameterCreationRequestJson("$requestIdPrefix-$index", name) }

/**
 * `faceFound = true` inconditionnellement : [VTubeStudioSender.send] ne délègue jamais ici quand
 * `!result.faceDetected` (retour anticipé, même convention que `VmcOscSender.send`/
 * `IFacialMocapSender.send`) -- ce champ ne reflète donc que les frames réellement transmises.
 */
internal fun buildInjectParameterDataRequestJson(requestId: String, blendshapes: List<BlendshapeScore>): String =
    buildMessageJson(
        requestId,
        "InjectParameterDataRequest",
        InjectParameterDataRequestData(
            faceFound = true,
            parameterValues = blendshapes.map { ParameterValue(id = it.name, value = it.score) },
        ),
    )

internal fun parseEnvelope(text: String): VtsEnvelope = vtsJson.decodeFromString(text)

internal fun parseAuthTokenResponse(envelope: VtsEnvelope): AuthenticationTokenResponseData? =
    decodeDataOrNull(envelope, "AuthenticationTokenResponse")

internal fun parseAuthResponse(envelope: VtsEnvelope): AuthenticationResponseData? =
    decodeDataOrNull(envelope, "AuthenticationResponse")

internal fun parseParameterCreationResponse(envelope: VtsEnvelope): ParameterCreationResponseData? =
    decodeDataOrNull(envelope, "ParameterCreationResponse")

internal fun parseApiError(envelope: VtsEnvelope): ApiErrorData? =
    decodeDataOrNull(envelope, "APIError")

private inline fun <reified T> decodeDataOrNull(envelope: VtsEnvelope, expectedMessageType: String): T? {
    if (envelope.messageType != expectedMessageType) return null
    val data = envelope.data ?: return null
    return runCatching { vtsJson.decodeFromJsonElement<T>(data) }.getOrNull()
}
