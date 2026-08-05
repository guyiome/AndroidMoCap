package com.guyiome.androidmocap.network

import android.util.Log
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
    }

    private val running = AtomicBoolean(false)
    private val sendExecutor = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null

    // La spec iFacialMocap précise que la réponse va toujours sur le port fixe 49983 du PC,
    // PAS sur le port source (souvent éphémère) du paquet de handshake reçu.
    @Volatile private var targetAddress: InetAddress? = null

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
                        if (running.get()) Log.w(TAG, "Erreur de réception UDP sur le port $PORT", e)
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    when {
                        text.startsWith(HANDSHAKE) -> {
                            targetAddress = packet.address
                            onStatusChanged(packet.address.hostAddress)
                        }
                        text.startsWith(STOP_HANDSHAKE) -> {
                            targetAddress = null
                            onStatusChanged(null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Impossible d'écouter sur le port $PORT", e)
            }
        }, "IFacialMocapListener").apply {
            isDaemon = true
            start()
        }
    }

    fun send(result: FaceTrackingResult) {
        val address = targetAddress ?: return
        if (!result.faceDetected || result.blendshapes.isEmpty()) return
        sendExecutor.execute {
            val sock = socket ?: return@execute
            try {
                val message = buildString {
                    for (blendshape in result.blendshapes) {
                        val name = toIFacialMocapName(blendshape.name) ?: continue
                        append(name)
                        append('-')
                        append((blendshape.score * 100f).toInt())
                        append('|')
                    }
                    // Pose de tête (matrice de transformation MediaPipe) + direction du regard
                    // (dérivée des blendshapes eyeLookUp/Down/In/Out). Position X/Y/Z pas encore
                    // branchée : zéro, sans casser le parseur côté récepteur.
                    val (pitch, yaw, roll) = result.headEulerDegrees
                    val (leftPitch, leftYaw, _) = result.leftEyeEulerDegrees
                    val (rightPitch, rightYaw, _) = result.rightEyeEulerDegrees
                    append("=head#$pitch,$yaw,$roll,0,0,0|rightEye#$rightPitch,$rightYaw,0|leftEye#$leftPitch,$leftYaw,0|")
                }
                val bytes = message.toByteArray(Charsets.UTF_8)
                sock.send(DatagramPacket(bytes, bytes.size, address, PORT))
            } catch (e: Exception) {
                // Cible momentanément injoignable : on laisse tomber cette frame plutôt que de bloquer.
            }
        }
    }

    /** MediaPipe utilise le suffixe "Left"/"Right" (ex. eyeBlinkLeft), iFacialMocap utilise "_L"/"_R". */
    private fun toIFacialMocapName(mediaPipeName: String): String? = when {
        mediaPipeName == "_neutral" -> null
        mediaPipeName.endsWith("Left") -> mediaPipeName.removeSuffix("Left") + "_L"
        mediaPipeName.endsWith("Right") -> mediaPipeName.removeSuffix("Right") + "_R"
        else -> mediaPipeName
    }

    fun stopListening() {
        if (!running.getAndSet(false)) return
        socket?.close()
        socket = null
        targetAddress = null
    }
}
