package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.logging.AppLog
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.PortUnreachableException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implémente le protocole iFacialMocap (le même que celui utilisé par MeowFace sur Android),
 * compatible nativement avec les sources d'entrée "MeowFace" / iFacialMocap de VBridger, VTube
 * Studio, VSeeFace...
 *
 * Contrairement à VMC, c'est le PC qui initie la connexion : il envoie une chaîne de handshake
 * en UDP sur le port 49983 du téléphone, et le téléphone lui répond ensuite en streaming sur ce
 * même port. Pas besoin de saisir l'IP du PC côté app -- juste de renseigner l'IP du téléphone
 * côté VBridger / VTube Studio (voir [getLocalIpAddress]).
 */
class IFacialMocapSender(
    private val onStatusChanged: (connectedTo: String?) -> Unit,
) {
    companion object {
        private const val TAG = "IFacialMocapSender"
        const val PORT = 49983
        private const val HANDSHAKE = "iFacialMocap_sahuasouryya9218sauhuiayeta91555dy3719"
        private const val STOP_HANDSHAKE = "iFacialMocap_UDPTCPSTOP_sahuasouryya9218sauhuiayeta91555dy3719"

        /**
         * Construit le message texte du protocole iFacialMocap à partir d'un résultat de tracking
         * -- extrait en fonction pure (aucune dépendance socket/réseau) pour être testable en JVM,
         * voir [send] qui en dépend pour construire le paquet UDP envoyé.
         */
        internal fun buildMessage(result: FaceTrackingResult): String = buildString {
            for (blendshape in result.blendshapes) {
                val name = toIFacialMocapName(blendshape.name) ?: continue
                append(name)
                append('-')
                append((blendshape.score * 100f).toInt())
                append('|')
            }
            // Pose de tête (matrice de transformation MediaPipe) + direction du regard (dérivée
            // des blendshapes eyeLookUp/Down/In/Out). Position X/Y/Z pas encore branchée : zéro,
            // sans casser le parseur côté récepteur.
            val (pitch, yaw, roll) = result.headEulerDegrees
            val (leftPitch, leftYaw, _) = result.leftEyeEulerDegrees
            val (rightPitch, rightYaw, _) = result.rightEyeEulerDegrees
            append("=head#$pitch,$yaw,$roll,0,0,0|rightEye#$rightPitch,$rightYaw,0|leftEye#$leftPitch,$leftYaw,0|")
        }

        /** MediaPipe utilise le suffixe "Left"/"Right" (ex. eyeBlinkLeft), iFacialMocap utilise "_L"/"_R". */
        internal fun toIFacialMocapName(mediaPipeName: String): String? = when {
            mediaPipeName == "_neutral" -> null
            mediaPipeName.endsWith("Left") -> mediaPipeName.removeSuffix("Left") + "_L"
            mediaPipeName.endsWith("Right") -> mediaPipeName.removeSuffix("Right") + "_R"
            else -> mediaPipeName
        }
    }

    private val running = AtomicBoolean(false)
    private val sendExecutor = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null

    // La spec iFacialMocap précise que la réponse va toujours sur le port fixe 49983 du PC,
    // PAS sur le port source (souvent éphémère) du paquet de handshake reçu.
    @Volatile private var targetAddress: InetAddress? = null

    // Socket dédié à l'envoi, distinct de [socket] (qui écoute sur le port fixe 49983 et doit
    // rester non connecté pour pouvoir recevoir un handshake de n'importe quelle IP) -- connecté à
    // la cible (voir [openSendSocket]) uniquement pour détecter sa déconnexion via
    // [PortUnreachableException], voir [send]. Muté depuis deux threads (écoute + envoi), d'où
    // @Volatile plutôt qu'un accès protégé, cohérent avec [targetAddress] juste au-dessus.
    @Volatile private var sendSocket: DatagramSocket? = null

    /**
     * Connecté à [address]/[PORT] (revue de code, 13 août 2026 : l'icône restait "connecté"
     * indéfiniment si VBridger se fermait sans envoyer [STOP_HANDSHAKE], ex. fermeture forcée/crash
     * plutôt qu'un arrêt propre) -- même principe que `VmcOscSender.connect()`, voir son kdoc pour
     * le détail du mécanisme ICMP/[PortUnreachableException] et ses limites.
     */
    private fun openSendSocket(address: InetAddress): DatagramSocket? = try {
        DatagramSocket().apply { connect(address, PORT) }
    } catch (e: Exception) {
        AppLog.w(TAG, "Impossible d'ouvrir le socket UDP d'envoi vers $address:$PORT", e)
        null
    }

    /** Démarre l'écoute du handshake sur le port 49983. Idempotent. */
    fun startListening() {
        if (running.getAndSet(true)) return
        Thread({
            try {
                val sock = DatagramSocket(PORT)
                socket = sock
                val buffer = ByteArray(4096)
                while (running.get()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        sock.receive(packet)
                    } catch (e: Exception) {
                        if (running.get()) AppLog.w(TAG, "Erreur de réception UDP sur le port $PORT", e)
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    when {
                        text.startsWith(HANDSHAKE) -> {
                            targetAddress = packet.address
                            sendSocket?.close()
                            sendSocket = openSendSocket(packet.address)
                            onStatusChanged(packet.address.hostAddress)
                        }
                        text.startsWith(STOP_HANDSHAKE) -> {
                            targetAddress = null
                            sendSocket?.close()
                            sendSocket = null
                            onStatusChanged(null)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Impossible d'écouter sur le port $PORT", e)
            }
        }, "IFacialMocapListener").apply {
            isDaemon = true
            start()
        }
    }

    fun send(result: FaceTrackingResult) {
        if (targetAddress == null) return
        if (!result.faceDetected || result.blendshapes.isEmpty()) return
        sendExecutor.execute {
            val out = sendSocket ?: return@execute
            try {
                val bytes = buildMessage(result).toByteArray(Charsets.UTF_8)
                out.send(DatagramPacket(bytes, bytes.size))
            } catch (e: PortUnreachableException) {
                // Identité vérifiée : si un nouveau handshake est arrivé entretemps (autre thread,
                // voir startListening()), sendSocket pointe déjà vers une connexion différente --
                // ce callback appartient alors à une connexion déjà abandonnée, à ignorer.
                if (sendSocket === out) {
                    targetAddress = null
                    sendSocket = null
                    AppLog.i(TAG, "Cible UDP/VBridger injoignable (ICMP port injoignable) -- déconnexion détectée")
                    onStatusChanged(null)
                }
                out.close()
            } catch (e: Exception) {
                // Cible momentanément injoignable : on laisse tomber cette frame plutôt que de bloquer.
            }
        }
    }

    fun stopListening() {
        if (!running.getAndSet(false)) return
        socket?.close()
        socket = null
        sendSocket?.close()
        sendSocket = null
        targetAddress = null
    }
}
