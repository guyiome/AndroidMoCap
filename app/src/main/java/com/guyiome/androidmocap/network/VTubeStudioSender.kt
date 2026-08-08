package com.guyiome.androidmocap.network

import android.util.Log
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Émetteur pour l'API Plugin de VTube Studio (point 39) -- alternative directe à [VmcOscSender],
 * que VTube Studio ne reçoit vraisemblablement pas nativement (voir revue technique). Protocole
 * encodé/décodé par [VTubeStudioProtocol] (pur, testé), cycle de connexion piloté par
 * [nextVTubeStudioConnectionState] (pur, testé) -- cette classe est la seule pièce non testable en
 * JVM : elle enveloppe un vrai [WebSocket] OkHttp et traduit ses événements en appels à `next()`.
 *
 * ⚠️ Non vérifié sur device (voir revue technique, point 39) : en particulier, si le serveur
 * WebSocket de VTube Studio écoute au-delà de `127.0.0.1` -- indispensable puisque contrairement à
 * VMC/iFacialMocap le téléphone est ici un client WebSocket classique visant l'IP du PC, pas un
 * simple envoi UDP. Si ce n'est pas le cas, aucune correction côté app ne peut compenser.
 *
 * `WebSocket.send()` est documenté non bloquant côté OkHttp (mise en file interne, thread d'écriture
 * dédié) -- contrairement aux deux émetteurs UDP de ce projet, pas besoin ici d'un
 * `ExecutorService` dédié pour éviter de bloquer l'appelant (thread d'inférence MediaPipe).
 *
 * Les callbacks [WebSocketListener] (donc [onStateChanged]/[onNewAuthToken]) sont invoqués depuis
 * un thread interne à OkHttp, jamais le thread appelant -- même situation que
 * [IFacialMocapSender.onStatusChanged], déjà utilisé par `MainViewModel` en toute sécurité
 * (`MutableStateFlow.update` est thread-safe).
 *
 * Cycle de vie d'une instance : une connexion. `MainViewModel` en reconstruit une nouvelle à chaque
 * clic "Connecter" (même patron que [VmcOscSender]) plutôt que de réutiliser/reconnecter une
 * instance existante -- pas de reconnexion automatique en boucle à ce stade (v1).
 *
 * Les noms de paramètres créés côté VTube Studio ne sont connus qu'à la première frame reçue après
 * authentification (voir [send]) -- pas de liste ARKit codée en dur ici, pour rester indépendant de
 * ce que MediaPipe fournit réellement à l'exécution.
 */
class VTubeStudioSender(
    private val host: InetAddress,
    private val port: Int = DEFAULT_PORT,
    storedAuthToken: String?,
    private val onStateChanged: (VTubeStudioConnectionState) -> Unit,
    private val onNewAuthToken: (String) -> Unit,
) {
    companion object {
        private const val TAG = "VTubeStudioSender"

        /** Port par défaut du serveur WebSocket de l'API Plugin VTube Studio (configurable côté VTS). */
        const val DEFAULT_PORT = 8001
    }

    // Un OkHttpClient dédié par instance (donc par connexion) -- son pool de threads est fermé dans
    // close(), symétrique à la construction/fermeture d'une instance de VmcOscSender/IFacialMocapSender.
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    @Volatile private var authToken: String? = storedAuthToken

    @Volatile private var connectionState: VTubeStudioConnectionState = VTubeStudioConnectionState.Disconnected
        set(value) {
            field = value
            onStateChanged(value)
        }

    // Évite de relancer la création de paramètres à chaque frame pendant qu'on attend les réponses.
    private val parametersRequested = AtomicBoolean(false)
    private val pendingParameterCreationResponses = AtomicInteger(0)
    private val requestCounter = AtomicInteger(0)

    init {
        connect()
    }

    private fun nextRequestId(prefix: String): String = "$prefix-${requestCounter.incrementAndGet()}"

    private fun connect() {
        connectionState = nextVTubeStudioConnectionState(connectionState, VTubeStudioConnectionEvent.Connect)
        val request = Request.Builder().url("ws://${host.hostAddress}:$port").build()
        webSocket = client.newWebSocket(request, Listener())
    }

    /**
     * Retour anticipé si `!result.faceDetected` (même convention que [VmcOscSender.send]/
     * [IFacialMocapSender.send]) -- laisse VTube Studio revenir aux valeurs par défaut plutôt que
     * de forcer un keep-alive avec les dernières valeurs connues.
     */
    fun send(result: FaceTrackingResult) {
        val socket = webSocket ?: return
        when (connectionState) {
            VTubeStudioConnectionState.Authenticated -> {
                if (!result.faceDetected || result.blendshapes.isEmpty()) return
                if (parametersRequested.getAndSet(true)) return
                val names = result.blendshapes.map { it.name }
                pendingParameterCreationResponses.set(names.size)
                buildParameterCreationRequestsJson(nextRequestId("params"), names).forEach { socket.send(it) }
            }
            VTubeStudioConnectionState.ParametersRegistered -> {
                if (!result.faceDetected || result.blendshapes.isEmpty()) return
                socket.send(buildInjectParameterDataRequestJson(nextRequestId("inject"), result.blendshapes))
            }
            else -> Unit
        }
    }

    fun close() {
        applyEvent(VTubeStudioConnectionEvent.Disconnect)
        webSocket?.close(1000, "Fermé par l'app")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    private fun applyEvent(event: VTubeStudioConnectionEvent) {
        connectionState = nextVTubeStudioConnectionState(connectionState, event)
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val token = authToken
            applyEvent(VTubeStudioConnectionEvent.SocketOpened(hasStoredToken = token != null))
            if (token != null) {
                webSocket.send(buildAuthRequestJson(nextRequestId("auth"), token))
            } else {
                webSocket.send(buildAuthTokenRequestJson(nextRequestId("token")))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val envelope = try {
                parseEnvelope(text)
            } catch (e: Exception) {
                Log.w(TAG, "Message VTube Studio illisible, ignoré", e)
                return
            }

            when (envelope.messageType) {
                "AuthenticationTokenResponse" -> {
                    val token = parseAuthTokenResponse(envelope)?.authenticationToken ?: return
                    authToken = token
                    onNewAuthToken(token)
                    applyEvent(VTubeStudioConnectionEvent.AuthTokenApproved(token))
                    webSocket.send(buildAuthRequestJson(nextRequestId("auth"), token))
                }
                "AuthenticationResponse" -> {
                    val auth = parseAuthResponse(envelope)
                    if (auth?.authenticated == true) {
                        applyEvent(VTubeStudioConnectionEvent.Authenticated)
                    } else {
                        applyEvent(VTubeStudioConnectionEvent.AuthenticationFailed(auth?.reason ?: "Authentification refusée"))
                    }
                }
                "ParameterCreationResponse" -> {
                    if (pendingParameterCreationResponses.decrementAndGet() <= 0) {
                        applyEvent(VTubeStudioConnectionEvent.ParametersReady)
                    }
                }
                "APIError" -> {
                    val reason = parseApiError(envelope)?.message ?: "Erreur VTube Studio inconnue"
                    when (connectionState) {
                        VTubeStudioConnectionState.AwaitingUserApproval ->
                            applyEvent(VTubeStudioConnectionEvent.AuthTokenDenied(reason))
                        VTubeStudioConnectionState.Authenticating ->
                            applyEvent(VTubeStudioConnectionEvent.AuthenticationFailed(reason))
                        VTubeStudioConnectionState.Authenticated ->
                            applyEvent(VTubeStudioConnectionEvent.ParameterCreationFailed(reason))
                        else ->
                            Log.w(TAG, "Erreur VTube Studio ($reason) reçue hors séquence, état courant $connectionState")
                    }
                }
                else -> Unit // InjectParameterDataResponse et autres accusés de réception : rien à faire.
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Connexion WebSocket VTube Studio perdue ($host:$port)", t)
            applyEvent(VTubeStudioConnectionEvent.SocketFailed(t.message ?: "Connexion perdue"))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            applyEvent(VTubeStudioConnectionEvent.Disconnect)
        }
    }
}
