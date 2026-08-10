package com.guyiome.androidmocap.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R

/**
 * Avertissement "l'appareil peine" pour la détection de langue tirée (point 15) -- même structure
 * que [LowBatteryAlert] (icône outlined pulsante, superposée à l'aperçu, jamais plein écran), mais
 * teinte ambre (`0xFFFFB74D`, même couleur que les autres avertissements de performance de l'app --
 * `DiagnosticsScreen`, `MainHud`) au lieu de rouge -- catégorie moins sévère qu'une batterie basse.
 * Contrairement à [LowBatteryAlert], **cliquable** : amène directement au réglage pour couper la
 * fonctionnalité si besoin, plutôt que de forcer l'utilisateur à retrouver le chemin dans les menus.
 */
@Composable
fun TonguePerformanceWarning(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "tonguePerformancePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tonguePerformanceAlpha",
    )

    Box(
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = stringResource(R.string.cd_tongue_performance_warning),
            tint = Color(0xFFFFB74D).copy(alpha = alpha),
            modifier = Modifier.size(220.dp),
        )
    }
}
