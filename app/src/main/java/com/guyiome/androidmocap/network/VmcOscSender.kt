package com.guyiome.androidmocap.network

import android.util.Log
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.illposed.osc.OSCBundle
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
 * Tous les messages d'une frame sont regroupés en un seul `OSCBundle` (voir [buildBundle]) envoyé
 * en un seul paquet UDP -- le regroupement en bundle fait partie du cœur du spec OSC, géré par
 * n'importe quel récepteur OSC conforme (VTube Studio compris). Auparavant, un paquet UDP séparé
 * était envoyé par blendshape (jusqu'à une cinquantaine par frame).
 *
 * Tout l'envoi réseau passe par un unique thread dédié pour ne jamais bloquer le thread d'inférence
 * MediaPipe ni l'UI.
 */
class VmcOscSender(
    private var host: InetAddress,
    private var port: Int = DEFAULT_PORT,
) {
    companion object {
        private const val TAG = "VmcOscSender"

        /** Port conventionnel du protocole VMC (cf. protocol.vmc.info) ; à adapter selon la config VTube Studio. */
        const val DEFAULT_PORT = 39539

        /**
         * Regroupe tous les messages `/VMC/Ext/Blend/Val` (un par blendshape) + le `/Apply` final
         * dans un seul `OSCBundle` -- extrait en fonction pure (`internal`, aucune dépendance
         * socket) pour être testable en JVM, voir [send] qui l'envoie en un seul paquet UDP au
         * lieu d'un envoi séparé par blendshape (jusqu'à une cinquantaine par frame auparavant).
         */
        internal fun buildBundle(result: FaceTrackingResult): OSCBundle {
            val bundle = OSCBundle()
            for (blendshape in result.blendshapes) {
                bundle.addPacket(OSCMessage("/VMC/Ext/Blend/Val", listOf(blendshape.name, blendshape.score)))
            }
            bundle.addPacket(OSCMessage("/VMC/Ext/Blend/Apply", emptyList<Any>()))
            return bundle
        }
    }

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var oscPortOut: OSCPortOut? = null

    /**
     * Reflète si le port UDP local a bien pu être ouvert (voir [connect]) -- à vérifier par
     * l'appelant juste après construction plutôt que de supposer la connexion établie : le
     * constructeur ne propage jamais d'exception (voir [connect]), un échec silencieux passerait
     * sinon inaperçu. Relecture globale du 7 août 2026, point 2 : jusqu'ici `MainViewModel`
     * affichait "connecté" inconditionnellement après construction, y compris en cas d'échec.
     */
    val isConnected: Boolean
        get() = oscPortOut != null

    init {
        connect()
    }

    /**
     * Échec possible mais rare en UDP (`OSCPortOut` ouvre juste un socket local, aucun aller-retour
     * réseau ici -- contrairement à une vraie connexion TCP, un hôte injoignable ne fait pas
     * échouer cet appel) : port hors plage, ressources système épuisées, permission refusée. Loggé
     * (voir [isConnected] pour la doc complète du raisonnement) -- auparavant totalement silencieux,
     * seul fichier de ce type dans le projet à ne rien logger sur un chemin d'échec.
     */
    private fun connect() {
        oscPortOut = try {
            OSCPortOut(host, port)
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'ouvrir le port UDP local pour la cible VMC $host:$port", e)
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
                out.send(buildBundle(result))
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
