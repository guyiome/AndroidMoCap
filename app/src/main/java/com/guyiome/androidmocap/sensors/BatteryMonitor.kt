package com.guyiome.androidmocap.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Suit le niveau de batterie en direct via le broadcast sticky ACTION_BATTERY_CHANGED --
 * `registerReceiver` renvoie immédiatement l'état courant, pas besoin d'attendre un changement.
 * Utile en session de streaming prolongée : la caméra + le GPU tournent en continu, et le
 * téléphone est souvent posé loin de l'utilisateur sur un support, donc pas facile de surveiller
 * la batterie autrement qu'à l'écran.
 */
class BatteryMonitor(
    private val context: Context,
    private val onChanged: (percent: Int, isCharging: Boolean) -> Unit,
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val percent = (level * 100) / scale
            val pluggedState = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            onChanged(percent, pluggedState != 0)
        }
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Déjà désenregistré -- sans risque, on ignore.
        }
    }
}
