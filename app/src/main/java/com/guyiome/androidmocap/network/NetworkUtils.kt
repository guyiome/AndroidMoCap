package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.logging.AppLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.util.Collections

/** Adresse IPv4 locale du téléphone sur le réseau Wi-Fi (à renseigner côté VBridger/VTube Studio). */
fun getLocalIpAddress(): String? = try {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull()
        ?.hostAddress
} catch (e: Exception) {
    null
}

/**
 * Démarre une sonde de vivacité sur [socket] (déjà `connect()`-é à sa cible) -- partagée entre
 * [VmcOscSender] et [IFacialMocapSender], qui l'utilisent toutes deux pour détecter une cible
 * devenue injoignable, voir leurs kdoc respectifs pour le contexte du 13 août 2026.
 *
 * ⚠️ **Pourquoi ce fichier existe, confirmé sur device (ROG Phone) le 13 août 2026** : le
 * mécanisme initial (catch de [PortUnreachableException] directement dans `send()`) ne suffisait
 * pas -- confirmé par test complet (logs propres, `connect()` réussi, données bien reçues côté
 * PC) qu'une fois le récepteur fermé, `send()` seul ne remontait **jamais** l'erreur ICMP, malgré
 * un socket correctement connecté. Sur certaines piles réseau Android (des ROG Phone ont des
 * optimisations réseau custom pour le "mode jeu"), l'erreur ICMP en attente sur un socket UDP
 * connecté n'est apparemment posée par le noyau qu'au prochain `receive()`, pas juste `send()` --
 * contraire au comportement Linux "standard" habituellement documenté, mais reproductible ici.
 *
 * Sonde volontairement **découplée du chemin d'envoi** (qui tourne à la cadence de frame, jusqu'à
 * 60 Hz) pour ne pas lui ajouter de latence : thread dédié, `receive()` à timeout court une fois
 * par seconde, suffisant pour un signal de perte de connexion (pas un besoin temps réel). Ne
 * garantit toujours pas une détection à 100% -- reste dépendant de la délivrance effective du
 * paquet ICMP par le réseau (un routeur/pare-feu qui le filtre entièrement resterait un angle
 * mort), ce correctif comble spécifiquement le cas confirmé ci-dessus (ICMP délivré mais pas
 * surfacé par `send()` seul), rien de plus.
 *
 * Arrêt automatique et silencieux quand [socket] est fermé (`receive()` sur un socket fermé lève
 * une exception, capturée ci-dessous) -- pas besoin de signal d'arrêt explicite séparé, l'appelant
 * n'a qu'à fermer le socket comme il le ferait de toute façon.
 */
fun startUdpLivenessProbe(
    socket: DatagramSocket,
    threadName: String,
    tag: String,
    intervalMs: Long = 1000L,
    timeoutMs: Int = 200,
    onUnreachable: () -> Unit,
): Thread {
    socket.soTimeout = timeoutMs
    val buffer = ByteArray(1)
    return Thread({
        while (!socket.isClosed) {
            try {
                // N'est jamais censé recevoir quoi que ce soit (VMC/iFacialMocap sont des
                // protocoles d'envoi uniquement) -- seul le déclenchement de
                // PortUnreachableException nous intéresse ici, un paquet reçu serait ignoré.
                socket.receive(DatagramPacket(buffer, buffer.size))
            } catch (e: PortUnreachableException) {
                onUnreachable()
                return@Thread
            } catch (e: SocketTimeoutException) {
                // Cas normal : rien reçu dans la fenêtre, on reboucle après la pause.
            } catch (e: Exception) {
                // Socket fermé/remplacé entretemps (reconnexion, fermeture explicite) -- la sonde
                // n'a plus lieu d'être, silencieux (pas une vraie erreur).
                return@Thread
            }
            try {
                Thread.sleep(intervalMs)
            } catch (e: InterruptedException) {
                return@Thread
            }
        }
    }, threadName).apply {
        isDaemon = true
        start()
    }.also { AppLog.d(tag, "Sonde de vivacité UDP démarrée ($threadName)") }
}
