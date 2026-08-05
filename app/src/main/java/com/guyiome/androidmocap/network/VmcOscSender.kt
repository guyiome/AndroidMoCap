package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.illposed.osc.OSCMessage
import com.illposed.osc.transport.OSCPortOut
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Envoie les blendshapes en protocole VMC (OSC over UDP) : `/VMC/Ext/Blend/Val` par blendshape
 * puis `/VMC/Ext/Blend/Apply` -- format reçu nativement par VTube Studio (Settings > VTube Studio >
 * VirtualMotionCapture), et compatible Blender (addon VMC) / Unity (uVMC).
 *
 * Les noms de blendshapes envoyés sont ceux de MediaPipe (déjà au format ARKit, ex. "jawOpen",
 * "eyeBlinkLeft") -- c'est le format "PerfectSync" reconnu par VTube Studio/VSeeFace. Le protocole
 * étant sensible à la casse, ne pas modifier ces noms sans vérifier le mapping côté récepteur.
 *
 * Tout l'envoi réseau passe par un unique thread dédié pour ne jamais bloquer le thread d'inférence
 * MediaPipe ni l'UI.
 */
class VmcOscSender(
    private var host: InetAddress,
    private var port: Int = DEFAULT_PORT,
) {
    companion object {
        /** Port conventionnel du protocole VMC (cf. protocol.vmc.info) ; à adapter selon la config VTube Studio. */
        const val DEFAULT_PORT = 39539
    }

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var oscPortOut: OSCPortOut? = null

    init {
        connect()
    }

    private fun connect() {
        oscPortOut = try {
            OSCPortOut(host, port)
        } catch (e: Exception) {
            null
        }
    }

    /** Change la cible (IP/port du PC qui fait tourner VTube Studio / Blender / Unity), ex. depuis l'écran de réglages. */
    fun updateTarget(newHost: InetAddress, newPort: Int = DEFAULT_PORT) {
        sendExecutor.execute {
            oscPortOut?.close()
            host = newHost
            port = newPort
            connect()
        }
    }

    fun send(result: FaceTrackingResult) {
        if (!result.faceDetected || result.blendshapes.isEmpty()) return
        sendExecutor.execute {
            val out = oscPortOut ?: return@execute
            try {
                for (blendshape in result.blendshapes) {
                    out.send(OSCMessage("/VMC/Ext/Blend/Val", listOf(blendshape.name, blendshape.score)))
                }
                out.send(OSCMessage("/VMC/Ext/Blend/Apply", emptyList<Any>()))
            } catch (e: Exception) {
                // Réseau momentanément indisponible : on laisse tomber cette frame plutôt que de bloquer.
            }
        }
    }

    fun close() {
        sendExecutor.execute { oscPortOut?.close() }
        sendExecutor.shutdown()
    }
}
