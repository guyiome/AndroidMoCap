package com.guyiome.androidmocap.network

import java.net.Inet4Address
import java.net.NetworkInterface
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
