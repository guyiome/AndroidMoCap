package com.guyiome.androidmocap.network

import android.util.Log
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.illposed.osc.BufferBytesReceiver
import com.illposed.osc.OSCBundle
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCPacket
import com.illposed.osc.OSCSerializerAndParserBuilder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
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
 *
 * ⚠️ **N'utilise volontairement PAS `OSCPortOut`/`OSCPort` de javaosc-core pour l'envoi réseau**
 * (correctif du 8 août 2026, crash confirmé sur device) : leur chemin de construction instancie
 * `UDPTransport`, qui référence `LibraryInfo` -- dont le bloc d'initialisation statique appelle
 * `ClassLoader.getDefinedPackage(String)` (API Java 9+, absente du runtime ART sur Android 11 / API
 * 30, confirmé par logcat : `NoSuchMethodError` dans `LibraryInfo.<clinit>`). Une fois cette classe
 * en échec d'initialisation, elle le reste pour tout le reste du process (sémantique JVM standard :
 * un échec de static initializer marque la classe durablement en erreur) -- ce n'était donc pas un
 * cas limite lié à une IP invalide, mais une régression totale : *toute* tentative de connexion VMC
 * plantait l'app sur cet appareil, quelle que soit l'adresse saisie.
 *
 * Vérifié sur les sources exactes de javaosc-core 0.9 (`javaosc-core-0.9-sources.jar`) : seuls
 * `LibraryInfo.java` et `UDPTransport.java` référencent `LibraryInfo` dans toute la bibliothèque --
 * la partie encodage du protocole (`OSCSerializerAndParserBuilder`/`OSCSerializer`/
 * `BufferBytesReceiver`, utilisée ci-dessous) en est totalement indépendante. Ce fichier reproduit
 * exactement ce que fait `OSCDatagramChannel.send()` en interne (construire un `OSCSerializer` via
 * le builder, écrire le paquet dans un buffer, envoyer les octets) mais avec un [DatagramSocket]
 * classique à la place de `UDPTransport`/`DatagramChannel` -- même sémantique "non connecté", adresse
 * distante précisée à chaque envoi, déjà le comportement de ce fichier avant ce correctif.
 */
class VmcOscSender(
    private val host: InetAddress,
    private val port: Int = DEFAULT_PORT,
) {
    companion object {
        private const val TAG = "VmcOscSender"

        /** Port conventionnel du protocole VMC (cf. protocol.vmc.info) ; à adapter selon la config VTube Studio. */
        const val DEFAULT_PORT = 39539

        /**
         * Reprend telle quelle `UDPTransport.BUFFER_SIZE` de javaosc-core (maximum théorique d'un
         * datagramme UDP) plutôt que d'estimer une taille -- un bundle réel (~52 blendshapes) tient
         * en quelques Ko, largement en dessous, mais autant garder la même marge que le
         * comportement d'origine plutôt que deviner un chiffre plus serré.
         */
        private const val SEND_BUFFER_SIZE = 65507

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

        /**
         * Sérialise [packet] en octets prêts à envoyer, en réutilisant [buffer] -- extrait en
         * fonction pure (`internal`) pour être testable en JVM sans socket, voir
         * `VmcOscSenderTest.kt` : c'est précisément ce mécanisme (`OSCSerializerAndParserBuilder`/
         * `OSCSerializer`/`BufferBytesReceiver`, aucune référence à `LibraryInfo`) qui remplace
         * `OSCPortOut` depuis le correctif du 8 août 2026 -- voir le kdoc de tête de cette classe.
         */
        internal fun serializeToBytes(
            packet: OSCPacket,
            builder: OSCSerializerAndParserBuilder,
            buffer: ByteBuffer,
        ): ByteArray {
            val serializer = builder.buildSerializer(BufferBytesReceiver(buffer))
            buffer.rewind()
            serializer.write(packet)
            buffer.flip()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return bytes
        }
    }

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    // Builder + buffer réutilisés à chaque envoi plutôt que recréés par frame -- accès toujours
    // depuis sendExecutor (thread unique), donc sans besoin de synchronisation.
    private val serializerBuilder = OSCSerializerAndParserBuilder()
    private val sendBuffer = ByteBuffer.allocate(SEND_BUFFER_SIZE)
    private var socket: DatagramSocket? = null

    /**
     * Reflète si le socket UDP local a bien pu être ouvert (voir [connect]) -- à vérifier par
     * l'appelant juste après construction plutôt que de supposer la connexion établie : le
     * constructeur ne propage jamais d'exception (voir [connect]), un échec silencieux passerait
     * sinon inaperçu. Relecture globale du 7 août 2026, point 2 : jusqu'ici `MainViewModel`
     * affichait "connecté" inconditionnellement après construction, y compris en cas d'échec.
     */
    val isConnected: Boolean
        get() = socket != null

    init {
        connect()
    }

    /**
     * Échec possible mais rare (`DatagramSocket()` sans argument se contente de réserver un port
     * local éphémère, aucun aller-retour réseau ici) : ressources système épuisées, permission
     * réseau refusée. Loggé -- voir [isConnected] pour la doc complète du raisonnement.
     */
    private fun connect() {
        socket = try {
            DatagramSocket()
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'ouvrir le socket UDP local pour la cible VMC $host:$port", e)
            null
        }
    }

    fun send(result: FaceTrackingResult) {
        if (!result.faceDetected || result.blendshapes.isEmpty()) return
        sendExecutor.execute {
            val out = socket ?: return@execute
            try {
                val bytes = serializeToBytes(buildBundle(result), serializerBuilder, sendBuffer)
                out.send(DatagramPacket(bytes, bytes.size, host, port))
            } catch (e: Exception) {
                // Réseau momentanément indisponible : on laisse tomber cette frame plutôt que de bloquer.
            }
        }
    }

    fun close() {
        sendExecutor.execute { socket?.close() }
        sendExecutor.shutdown()
    }
}
