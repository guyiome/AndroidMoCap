package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeScore
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre l'encodage/décodage JSON pur de [VTubeStudioProtocol] -- entièrement en JVM, sans socket
 * ni device, même esprit que `VmcOscSenderTest`/`IFacialMocapSenderTest`. Ne couvre pas
 * [VTubeStudioSender] (socket WebSocket réel, non testable ici) ni le vrai comportement du serveur
 * VTube Studio (ex. gestion réelle d'un paramètre déjà créé par un autre plugin).
 */
class VTubeStudioProtocolTest {

    @Test
    fun `buildAuthTokenRequestJson encode le nom du plugin et le type de message`() {
        val envelope = parseEnvelope(buildAuthTokenRequestJson("req-1"))

        assertEquals("AuthenticationTokenRequest", envelope.messageType)
        assertEquals("req-1", envelope.requestID)
        assertEquals("VTubeStudioPublicAPI", envelope.apiName)

        val data = decodeDataOrNullForTest<AuthenticationTokenRequestData>(envelope)
        assertEquals(PLUGIN_NAME, data?.pluginName)
        assertEquals(PLUGIN_DEVELOPER, data?.pluginDeveloper)
    }

    @Test
    fun `buildAuthTokenRequestJson serialise vraiment les champs, pas juste au decodage`() {
        // Régression du 8 août 2026 (device) : sans encodeDefaults=true, kotlinx.serialization
        // omet du JSON tout champ dont la valeur égale son défaut Kotlin -- ici TOUS les champs
        // (apiName/apiVersion/pluginName/pluginDeveloper valent tous leur défaut en pratique), le
        // JSON envoyé à VTube Studio devenait `{"requestID":"...","messageType":"...","data":{}}`.
        // Un simple round-trip (voir le test précédent) ne détecte PAS ce bug : le décodage
        // réapplique les mêmes valeurs par défaut pour les champs absents, masquant le problème.
        // Seule une assertion sur le texte JSON brut, comme ici, le révèle.
        val json = buildAuthTokenRequestJson("req-1")

        assertTrue("apiName manquant du JSON brut : $json", json.contains("\"apiName\":\"VTubeStudioPublicAPI\""))
        assertTrue("apiVersion manquant du JSON brut : $json", json.contains("\"apiVersion\":\"1.0\""))
        assertTrue("pluginName manquant du JSON brut : $json", json.contains("\"pluginName\":\"$PLUGIN_NAME\""))
        assertTrue("pluginDeveloper manquant du JSON brut : $json", json.contains("\"pluginDeveloper\":\"$PLUGIN_DEVELOPER\""))
    }

    @Test
    fun `buildAuthRequestJson embarque le token fourni`() {
        val envelope = parseEnvelope(buildAuthRequestJson("req-2", "mon-token"))

        assertEquals("AuthenticationRequest", envelope.messageType)
        val data = decodeDataOrNullForTest<AuthenticationRequestData>(envelope)
        assertEquals("mon-token", data?.authenticationToken)
    }

    @Test
    fun `buildParameterCreationRequestJson encode le nom du parametre avec les bornes par defaut`() {
        val envelope = parseEnvelope(buildParameterCreationRequestJson("req-3", "jawOpen"))

        assertEquals("ParameterCreationRequest", envelope.messageType)
        val data = decodeDataOrNullForTest<ParameterCreationRequestData>(envelope)
        assertEquals("jawOpen", data?.parameterName)
        assertEquals(-1f, data?.min)
        assertEquals(1f, data?.max)
        assertEquals(0f, data?.defaultValue)
    }

    @Test
    fun `buildParameterCreationRequestsJson produit un message par nom, avec des requestID uniques`() {
        val messages = buildParameterCreationRequestsJson("batch", listOf("jawOpen", "eyeBlinkLeft"))

        assertEquals(2, messages.size)
        val envelopes = messages.map { parseEnvelope(it) }
        assertEquals(setOf("batch-0", "batch-1"), envelopes.map { it.requestID }.toSet())
        assertEquals(
            listOf("jawOpen", "eyeBlinkLeft"),
            envelopes.map { decodeDataOrNullForTest<ParameterCreationRequestData>(it)?.parameterName },
        )
    }

    @Test
    fun `buildInjectParameterDataRequestJson envoie toutes les valeurs avec faceFound vrai`() {
        val blendshapes = listOf(BlendshapeScore("jawOpen", 0.5f), BlendshapeScore("eyeBlinkLeft", 0.25f))
        val envelope = parseEnvelope(buildInjectParameterDataRequestJson("req-4", blendshapes))

        assertEquals("InjectParameterDataRequest", envelope.messageType)
        val data = decodeDataOrNullForTest<InjectParameterDataRequestData>(envelope)
        assertNotNull(data)
        assertTrue(data!!.faceFound)
        assertEquals("set", data.mode)
        assertEquals(
            listOf(ParameterValue("jawOpen", 0.5f), ParameterValue("eyeBlinkLeft", 0.25f)),
            data.parameterValues,
        )
    }

    @Test
    fun `parseAuthTokenResponse decode un vrai payload de reponse serveur`() {
        val json = """
            {
              "apiName": "VTubeStudioPublicAPI",
              "apiVersion": "1.0",
              "timestamp": 1625405710728,
              "requestID": "req-1",
              "messageType": "AuthenticationTokenResponse",
              "data": { "authenticationToken": "abcd-1234" }
            }
        """.trimIndent()

        val response = parseAuthTokenResponse(parseEnvelope(json))
        assertEquals("abcd-1234", response?.authenticationToken)
    }

    @Test
    fun `parseAuthResponse decode authenticated vrai ou faux`() {
        val approved = parseEnvelope(
            """{"apiName":"VTubeStudioPublicAPI","apiVersion":"1.0","requestID":"r","messageType":"AuthenticationResponse","data":{"authenticated":true,"reason":"ok"}}""",
        )
        assertEquals(true, parseAuthResponse(approved)?.authenticated)

        val denied = parseEnvelope(
            """{"apiName":"VTubeStudioPublicAPI","apiVersion":"1.0","requestID":"r","messageType":"AuthenticationResponse","data":{"authenticated":false,"reason":"nope"}}""",
        )
        assertEquals(false, parseAuthResponse(denied)?.authenticated)
    }

    @Test
    fun `parseApiError decode errorID et message`() {
        val json = """
            {
              "apiName": "VTubeStudioPublicAPI",
              "apiVersion": "1.0",
              "requestID": "req-5",
              "messageType": "APIError",
              "data": { "errorID": 50, "message": "User has denied API access for your plugin." }
            }
        """.trimIndent()

        val error = parseApiError(parseEnvelope(json))
        assertEquals(50, error?.errorID)
        assertEquals("User has denied API access for your plugin.", error?.message)
    }

    @Test
    fun `parseXxx renvoie null si le messageType ne correspond pas`() {
        val authResponseEnvelope = parseEnvelope(
            """{"apiName":"VTubeStudioPublicAPI","apiVersion":"1.0","requestID":"r","messageType":"AuthenticationResponse","data":{"authenticated":true}}""",
        )
        // Mauvais parseur pour ce messageType -- ne doit pas planter, juste renvoyer null.
        assertNull(parseApiError(authResponseEnvelope))
        assertNull(parseAuthTokenResponse(authResponseEnvelope))
    }

    @Test
    fun `ignoreUnknownKeys tolere des champs supplementaires inconnus du client`() {
        val json = """
            {
              "apiName": "VTubeStudioPublicAPI",
              "apiVersion": "1.0",
              "requestID": "r",
              "messageType": "AuthenticationResponse",
              "unknownField": "should be ignored",
              "data": { "authenticated": true, "reason": "ok", "unknownDataField": 42 }
            }
        """.trimIndent()

        assertEquals(true, parseAuthResponse(parseEnvelope(json))?.authenticated)
    }

    private inline fun <reified T> decodeDataOrNullForTest(envelope: VtsEnvelope): T? =
        envelope.data?.let { vtsJson.decodeFromJsonElement(it) }
}
