package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.logging.AppLog
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Émetteur pour l'API Plugin de VTube Studio (point 39) -- alternative directe à [VmcOscSender],
 * que VTube Studio ne reçoit vraisemblablement pas nativement (voir revue technique). Protocole
 * encodé/décodé par [VTubeStudioProtocol] (pur, testé), cycle de connexion piloté par
 * [nextVTubeStudioConnectionState] (pur, testé) -- cette classe est la seule pièce non testable en
 * JVM : elle enveloppe un vrai [WebSocket] et traduit ses événements en appels à `next()`.
 *
 * **Client WebSocket : nv-websocket-client, pas OkHttp** (revue technique, point 39 -- essai/erreur
 * sur device le 8 août 2026, pas une préférence a priori). OkHttp propose systématiquement
 * l'extension `permessage-deflate` sur ses WebSocket, sans réglage public pour la désactiver -- même
 * un intercepteur réseau retirant l'en-tête `Sec-WebSocket-Extensions` de la requête ne change rien,
 * confirmé sur device : OkHttp l'ajoute à un niveau plus bas que la chaîne d'intercepteurs. Le
 * serveur VTube Studio (`Server: websocket-sharp/1.0`) a une implémentation buguée/incomplète de
 * cette extension avec `no_context_takeover` -- incompatibilité documentée
 * (github.com/sta/websocket-sharp/issues/666) : la connexion s'ouvrait normalement (poignée de main
 * réussie, négociation `permessage-deflate` acceptée par le serveur), la requête
 * `AuthenticationTokenRequest` partait bien, mais aucune réponse n'était jamais lisible côté app --
 * confirmé indépendamment de notre code via un testeur WebSocket générique et la console JS d'un
 * navigateur (qui, eux, ne proposent pas cette extension et reçoivent leur réponse normalement
 * contre le même serveur). nv-websocket-client ne propose `permessage-deflate` que si on l'active
 * explicitement (`addExtension`) -- jamais par défaut.
 *
 * Les callbacks [WebSocketAdapter] (donc [onStateChanged]/[onNewAuthToken]) sont invoqués depuis un
 * thread interne à nv-websocket-client, jamais le thread appelant -- même situation que
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

    // PAS de pingInterval/setPingInterval : testé sur device le 8 août 2026, le serveur VTube
    // Studio ne répond à aucun ping WebSocket ("sent ping but didn't receive pong") -- avec un
    // ping périodique activé (essayé côté OkHttp avant la bascule vers cette bibliothèque), la
    // connexion était donc systématiquement coupée au bout de l'intervalle configuré, quoi qu'il
    // arrive. Si un vrai besoin de keep-alive se confirme (connexion coupée en silence pendant
    // l'attente du popup d'autorisation), la piste à explorer est un keep-alive applicatif (ex.
    // renvoyer un message léger de l'API VTube Studio elle-même) plutôt que le ping/pong du
    // protocole WebSocket, que ce serveur ignore.
    private val factory = WebSocketFactory()
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

    // Jeton stocké refusé par VTube Studio (ex. révoqué depuis son panneau "Plugin config/
    // permissions") -- confirmé sur device le 8 août 2026 : ré-authentification directe échouant
    // avec "token is invalid or has been revoked". usedStoredToken distingue ce cas d'un vrai échec
    // d'authentification (jeton flambant neuf refusé, situation différente, pas de retry auto
    // possible) ; retriedAfterInvalidToken évite une boucle si le nouveau jeton échoue aussi.
    @Volatile private var usedStoredToken = false
    private val retriedAfterInvalidToken = AtomicBoolean(false)

    // true dès close() -- une instance fermée ne doit plus jamais notifier onStateChanged, même si
    // nv-websocket-client déclenche encore un callback asynchrone après coup (ex. onDisconnected
    // arrivant après qu'une NOUVELLE instance a déjà été construite pour une reconnexion, voir
    // MainViewModel.connectVtsTarget -- sans cette garde, le callback tardif de l'ancienne instance
    // écraserait l'état affiché de la nouvelle connexion, les deux partageant le même callback côté
    // MainViewModel). Même patron que IFacialMocapSender.running (AtomicBoolean, garde contre un
    // callback/travail en cours après l'arrêt demandé).
    @Volatile private var closed = false

    init {
        connect()
    }

    private fun nextRequestId(prefix: String): String = "$prefix-${requestCounter.incrementAndGet()}"

    private fun connect() {
        connectionState = nextVTubeStudioConnectionState(connectionState, VTubeStudioConnectionEvent.Connect)
        val url = "ws://${host.hostAddress}:$port"
        AppLog.d(TAG, "Connexion vers $url")
        try {
            webSocket = factory.createSocket(url).apply {
                addListener(Listener())
                connectAsynchronously()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Impossible de préparer la connexion WebSocket vers $url", e)
            applyEvent(VTubeStudioConnectionEvent.SocketFailed(e.message ?: "Connexion impossible"))
        }
    }

    /**
     * `WebSocket.sendText()` ne signale pas d'échec synchrone (mise en file interne, pas de valeur
     * de retour exploitable côté succès/échec) -- logué tel quel, un vrai échec d'envoi remonterait
     * via [WebSocketAdapter.onError] plutôt qu'ici.
     */
    private fun sendLogged(socket: WebSocket, description: String, text: String) {
        socket.sendText(text)
        AppLog.d(TAG, "Envoi $description : $text")
    }

    /**
     * Retour anticipé si `!result.faceDetected` (même convention que [VmcOscSender.send]/
     * [IFacialMocapSender.send]) -- laisse VTube Studio revenir aux valeurs par défaut plutôt que
     * de forcer un keep-alive avec les dernières valeurs connues.
     *
     * [useVBridgerTranslation] bascule entre les 52 blendshapes ARKit bruts (`false`, comportement
     * historique) et les formules composites VBridger seules (`true`, point 41, [VBridgerFormulas])
     * -- **exclusif, pas additif** : en mode traduction, les bruts ne partent plus du tout, pour une
     * parité exacte avec ce que VBridger lui-même envoie à VTS (voir le kdoc de tête de
     * [VBridgerFormulas]). Lu à chaque appel plutôt que figé à la construction, pour que basculer le
     * réglage prenne effet immédiatement sans reconnexion.
     *
     * La phase de création de paramètres (`Authenticated`), elle, crée **toujours** les deux groupes
     * (52 noms bruts + 28 formules composites, voir [VBridgerFormulas] -- toutes demandent
     * `ParameterCreationRequest`, y compris celles qui s'avèrent déjà natives à VTS, sans
     * conséquence dans ce cas, voir le kdoc de tête de [VBridgerFormulas]) indépendamment de
     * [useVBridgerTranslation] -- créer les deux par avance, même si le réglage démarre dans un seul
     * des deux modes, évite qu'un basculement plus tard dans la même connexion tente d'injecter des
     * noms jamais enregistrés.
     */
    fun send(result: FaceTrackingResult, useVBridgerTranslation: Boolean = false) {
        val socket = webSocket ?: return
        when (connectionState) {
            VTubeStudioConnectionState.Authenticated -> {
                if (parametersRequested.getAndSet(true)) return
                val names = (VBridgerFormulas.rawArkitFormulas + VBridgerFormulas.vbridgerCompositeFormulas)
                    .filter { it.requiresParameterCreation }
                    .map { it.outputName }
                pendingParameterCreationResponses.set(names.size)
                AppLog.d(TAG, "Envoi de ${names.size} ParameterCreationRequest")
                buildParameterCreationRequestsJson(nextRequestId("params"), names)
                    .forEach { sendLogged(socket, "ParameterCreationRequest", it) }
            }
            VTubeStudioConnectionState.ParametersRegistered -> {
                // Pas de sendLogged() ici volontairement (20-60 Hz) -- inonderait logcat pour un
                // envoi en régime établi, contrairement à la phase de connexion (ouverture, auth,
                // création des paramètres) qui ne survient qu'une fois et vaut la peine d'être tracée.
                if (!result.faceDetected || result.blendshapes.isEmpty()) return
                val values = VBridgerFormulas.evaluate(VBridgerFormulas.activeFormulas(useVBridgerTranslation), result)
                socket.sendText(buildInjectParameterDataRequestJson(nextRequestId("inject"), values))
            }
            else -> Unit
        }
    }

    fun close() {
        closed = true
        applyEvent(VTubeStudioConnectionEvent.Disconnect)
        webSocket?.disconnect()
        webSocket = null
    }

    private fun applyEvent(event: VTubeStudioConnectionEvent) {
        val previous = connectionState
        connectionState = nextVTubeStudioConnectionState(connectionState, event)
        AppLog.d(TAG, "État : $previous --($event)--> $connectionState")
    }

    /** Décompte une réponse de création de paramètre, succès ou échec confondus -- voir l'appel côté "APIError". */
    private fun countDownParameterCreation() {
        if (pendingParameterCreationResponses.decrementAndGet() <= 0) {
            applyEvent(VTubeStudioConnectionEvent.ParametersReady)
        }
    }

    private inner class Listener : WebSocketAdapter() {
        override fun onConnected(webSocket: WebSocket, headers: Map<String, List<String>>) {
            if (closed) return
            val token = authToken
            usedStoredToken = token != null
            AppLog.d(TAG, "Socket ouvert, jeton stocké = $usedStoredToken")
            AppLog.d(TAG, "En-têtes de la poignée de main : $headers")
            applyEvent(VTubeStudioConnectionEvent.SocketOpened(hasStoredToken = token != null))
            if (token != null) {
                sendLogged(webSocket, "AuthenticationRequest", buildAuthRequestJson(nextRequestId("auth"), token))
            } else {
                sendLogged(webSocket, "AuthenticationTokenRequest", buildAuthTokenRequestJson(nextRequestId("token")))
            }
        }

        override fun onTextMessage(webSocket: WebSocket, text: String) {
            if (closed) return
            AppLog.d(TAG, "Message reçu : $text")
            val envelope = try {
                parseEnvelope(text)
            } catch (e: Exception) {
                AppLog.w(TAG, "Message VTube Studio illisible, ignoré", e)
                return
            }

            when (envelope.messageType) {
                "AuthenticationTokenResponse" -> {
                    val token = parseAuthTokenResponse(envelope)?.authenticationToken ?: return
                    authToken = token
                    onNewAuthToken(token)
                    applyEvent(VTubeStudioConnectionEvent.AuthTokenApproved(token))
                    sendLogged(webSocket, "AuthenticationRequest", buildAuthRequestJson(nextRequestId("auth"), token))
                }
                "AuthenticationResponse" -> {
                    val auth = parseAuthResponse(envelope)
                    when {
                        auth?.authenticated == true -> applyEvent(VTubeStudioConnectionEvent.Authenticated)
                        // Jeton stocké refusé (ex. révoqué côté VTube Studio) : on retente une fois
                        // avec un nouveau popup d'autorisation plutôt que d'abandonner -- voir
                        // usedStoredToken/retriedAfterInvalidToken. Un jeton flambant neuf refusé
                        // (donc usedStoredToken == false) tombe dans le else, pas de retry possible.
                        usedStoredToken && !retriedAfterInvalidToken.getAndSet(true) -> {
                            AppLog.w(TAG, "Jeton stocké refusé (${auth?.reason}), nouvelle demande d'autorisation")
                            authToken = null
                            applyEvent(VTubeStudioConnectionEvent.StoredTokenRejected)
                            sendLogged(webSocket, "AuthenticationTokenRequest", buildAuthTokenRequestJson(nextRequestId("token")))
                        }
                        else -> applyEvent(VTubeStudioConnectionEvent.AuthenticationFailed(auth?.reason ?: "Authentification refusée"))
                    }
                }
                "ParameterCreationResponse" -> countDownParameterCreation()
                "APIError" -> {
                    val reason = parseApiError(envelope)?.message ?: "Erreur VTube Studio inconnue"
                    when (connectionState) {
                        VTubeStudioConnectionState.AwaitingUserApproval ->
                            applyEvent(VTubeStudioConnectionEvent.AuthTokenDenied(reason))
                        VTubeStudioConnectionState.Authenticating ->
                            applyEvent(VTubeStudioConnectionEvent.AuthenticationFailed(reason))
                        VTubeStudioConnectionState.Authenticated -> {
                            // Collision de nom avec un autre plugin déjà connecté (ex. VBridger,
                            // mêmes noms ARKit) le plus souvent -- confirmé sur device le 8 août
                            // 2026 (errorID 352, "Another plugin has already created a custom
                            // parameter..."). Ne fait plus échouer toute la connexion : seul ce
                            // paramètre est perdu (ne sera jamais injecté), les autres continuent
                            // normalement. Compté comme "traité" au même titre qu'un succès pour ne
                            // pas bloquer indéfiniment en attente de sa réponse.
                            AppLog.w(TAG, "Création de paramètre refusée, ignorée : $reason")
                            countDownParameterCreation()
                        }
                        else ->
                            AppLog.w(TAG, "Erreur VTube Studio ($reason) reçue hors séquence, état courant $connectionState")
                    }
                }
                else -> Unit // InjectParameterDataResponse et autres accusés de réception : rien à faire.
            }
        }

        override fun onDisconnected(
            webSocket: WebSocket,
            serverCloseFrame: WebSocketFrame?,
            clientCloseFrame: WebSocketFrame?,
            closedByServer: Boolean,
        ) {
            if (closed) return
            AppLog.d(TAG, "Socket fermé (par ${if (closedByServer) "le serveur" else "l'app"})")
            applyEvent(VTubeStudioConnectionEvent.Disconnect)
        }

        override fun onError(webSocket: WebSocket, cause: WebSocketException) {
            if (closed) return
            AppLog.w(TAG, "Erreur WebSocket VTube Studio ($host:$port)", cause)
            applyEvent(VTubeStudioConnectionEvent.SocketFailed(cause.message ?: "Erreur de connexion"))
        }

        override fun onConnectError(webSocket: WebSocket, exception: WebSocketException) {
            if (closed) return
            AppLog.w(TAG, "Échec de connexion WebSocket VTube Studio ($host:$port)", exception)
            applyEvent(VTubeStudioConnectionEvent.SocketFailed(exception.message ?: "Connexion échouée"))
        }
    }
}
