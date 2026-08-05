package com.guyiome.androidmocap.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Grand contour de batterie rouge superposé à l'aperçu caméra quand la batterie est basse et que
 * le téléphone n'est pas en charge. Volontairement en contour (icône "outlined", pas remplie) et
 * pulsant en semi-transparence -- attire l'œil sans jamais cacher l'image en dessous.
 */
@Composable
fun LowBatteryAlert() {
    val transition = rememberInfiniteTransition(label = "lowBatteryPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lowBatteryAlpha",
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.BatteryAlert,
            contentDescription = "Batterie faible",
            tint = Color.Red.copy(alpha = alpha),
            modifier = Modifier.size(220.dp),
        )
    }
}
