package com.guyiome.androidmocap.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Accès au réseau local sous Android 17 (API 37).
 *
 * Android 17 a introduit une permission d'exécution pour toute communication sur le réseau local,
 * **obligatoire pour les apps qui ciblent l'API 37** (ce qui est notre cas, voir `targetSdk` dans
 * build.gradle.kts). Les apps qui ciblent l'API 36 ou moins continuent de l'obtenir implicitement
 * via `INTERNET` ; en Android 16 elle n'était qu'optionnelle, d'où des appareils qui fonctionnaient
 * parfaitement jusqu'à leur mise à jour système.
 *
 * Les quatre opérations soumises à permission couvrent la totalité de ce que fait cette app :
 * connexion TCP sortante, connexion TCP entrante, envoi UDP (unicast/multicast/broadcast) et
 * **réception UDP**. Autrement dit les trois transports sont concernés, pas seulement l'un d'eux :
 * VMC/OSC (envoi UDP), UDP/VBridger ([IFacialMocapSender], qui *reçoit* la poignée de main du PC)
 * et VTube Studio ([VTubeStudioSender], WebSocket TCP sortant). Seule la vérification de mise à
 * jour ([UpdateChecker]) y échappe, parce qu'elle sort sur Internet et non sur le réseau local.
 *
 * Sans la permission, le système ne remonte pas d'erreur exploitable : en UDP les paquets sont
 * refusés (code `EPERM`) ou simplement jamais délivrés, et en TCP la connexion expire. C'est
 * exactement le symptôme remonté par l'issue #41 -- la poignée de main de VBridger n'arrivait
 * jamais, donc l'app restait indéfiniment "en attente" sans rien à journaliser.
 *
 * Voir la revue technique (point 69) pour le diagnostic complet.
 */
object LocalNetworkPermission {

    /**
     * Nom littéral plutôt que `Manifest.permission.ACCESS_LOCAL_NETWORK` **volontairement** : la
     * constante n'existe pas dans toutes les révisions du SDK 37 (celle installée localement ne la
     * déclare pas), donc l'utiliser lierait la compilation à la révision exacte de la plateforme
     * installée. Un littéral est de toute façon ce qui finit dans le manifeste, et il reste correct
     * sur les appareils antérieurs où la permission n'existe pas -- `minSdk = 30` ici.
     */
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Première version d'Android qui impose la permission : Android 17. */
    const val FIRST_ENFORCING_SDK = 37

    /**
     * Fonction pure (testée en JVM) : la permission n'est demandée que sur les appareils qui
     * l'imposent réellement. En dessous, la demander afficherait au mieux rien, au pire une boîte
     * de dialogue pour une permission inconnue du système.
     */
    fun isEnforcedOn(sdkInt: Int): Boolean = sdkInt >= FIRST_ENFORCING_SDK

    /**
     * `true` si l'app peut légitimement parler au réseau local : soit l'appareil n'impose rien,
     * soit la permission a été accordée. Les appelants doivent vérifier **avant** toute ouverture
     * de socket, comme le demande la documentation Android.
     */
    fun isGranted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        !isEnforcedOn(sdkInt) ||
            ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED
}
