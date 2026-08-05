package com.guyiome.androidmocap.sensors

import android.content.Context
import android.view.OrientationEventListener

/**
 * Angle de rotation du téléphone par rapport à sa position naturelle (portrait, haut vers le
 * haut), en degrés [0, 359] -- sert uniquement à faire pivoter les icônes de l'UI pour qu'elles
 * restent lisibles quel que soit l'angle auquel le téléphone est tenu ou posé (comme le bouton
 * d'un appareil photo qui reste horizontal). Rôle différent de [DeviceOrientationTracker], qui
 * sert lui à corriger le tracking facial -- on utilise ici l'API Android standard
 * (accéléromètre seul, via OrientationEventListener) plutôt que le gyroscope fusionné, plus
 * stable pour ce simple usage visuel et c'est l'outil qu'Android recommande pour ce cas précis.
 */
class IconOrientationTracker(
    context: Context,
    private val onChanged: (degrees: Int) -> Unit,
) : OrientationEventListener(context) {
    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        onChanged(orientation)
    }
}
