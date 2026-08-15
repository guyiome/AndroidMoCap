package com.guyiome.androidmocap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Empêche un tap sur cet élément d'atteindre ce qui est composé en dessous, dans la même pile
 * Compose. `Modifier.background(...)`, à lui seul, ne fait que dessiner -- il ne consomme aucun
 * événement tactile, donc un tap sur une zone non couverte par un élément interactif (marge, espace
 * entre deux lignes, coin vide...) retombe directement sur l'écran précédent.
 *
 * Tous les écrans de réglages ([SettingsScreen] et ses six sous-écrans) restent composés en dessous
 * de l'écran actif : la navigation empile de simples booléens (`showX` dans `MainScreen.kt`) plutôt
 * que de démonter l'écran précédent, pour que le retour révèle l'écran du dessous sans devoir le
 * reconstruire. Sans ce blocage, ça laissait passer les taps à travers les zones vides d'un
 * sous-menu jusqu'au menu parent en dessous -- bug critique constaté sur device (retour
 * utilisateur : "on peut cliquer et naviguer sur les menus précédents par transparence"). À poser
 * sur la racine plein écran de tout nouvel écran superposé de la même famille.
 */
@Composable
fun Modifier.blockClicksBehind(): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = {},
)
