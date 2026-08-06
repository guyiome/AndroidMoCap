package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Catégorie dédiée aux fonctionnalités expérimentales -- une des quatre catégories de
 * [SettingsScreen] (voir rapport technique, point 21), créée par anticipation des points 15
 * (détection de la langue tirée) et 16 (détection des joues gonflées), aucun des deux pas encore
 * implémenté. Simple message d'attente pour l'instant plutôt que de masquer l'entrée du menu --
 * la catégorie reflète une décision de rangement déjà actée, pas seulement un contenu déjà là.
 */
@Composable
fun ExperimentalFeaturesScreen(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Fonctionnalités expérimentales",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Aucune fonctionnalité expérimentale disponible pour l'instant -- réservé pour la " +
                    "détection de la langue tirée et des joues gonflées (voir la revue technique).",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
