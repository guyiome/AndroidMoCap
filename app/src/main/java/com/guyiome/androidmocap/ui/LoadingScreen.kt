package com.guyiome.androidmocap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R

/**
 * Affiché à la place de l'aperçu caméra/HUD tant que [MainUiState.isInitializing] est vrai --
 * couvre la sélection de palier, le chargement du modèle MediaPipe et la construction de la source
 * caméra (voir MainViewModel.initializeTracking). Volontairement simple (logo + message + barre de
 * progression indéterminée) : aucune étape de [MainViewModel.initializeTracking] ne remonte de
 * progression réelle exploitable (un seul appel bloquant, `FaceLandmarkerHelper.setup()`, domine le
 * temps total), une barre déterminée inventerait une précision qui n'existe pas.
 *
 * Réutilise l'illustration de l'icône de lancement ([R.drawable.ic_launcher_foreground_source],
 * déjà versionnée pour générer l'icône adaptative) comme logo -- pas de doublon d'asset à
 * maintenir, cohérent avec ce que l'utilisateur voit déjà sur son écran d'accueil. Toujours un
 * placeholder (point 53 de la todo/revue technique, en attente d'un logo d'artiste) -- crédit en
 * bas à gauche par discrétion/honnêteté envers l'auteurice de l'image actuelle, sans revendiquer un
 * logo final.
 */
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Même couleur que le fond de l'icône adaptative (ic_launcher_background.xml) --
            // continuité visuelle avec l'icône affichée juste avant que cet écran apparaisse.
            .background(Color(0xFF1B1F27)),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground_source),
                // Décoratif -- le message texte juste en dessous porte déjà l'information d'état.
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.loading_message),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                // Indéterminée (pas de progress: Float passé) -- voir kdoc de tête.
                modifier = Modifier.width(200.dp),
                // Même cyan que le maillage de tracking (FaceMeshOverlay) -- cohérence visuelle
                // plutôt que la couleur primaire par défaut de Material (l'app n'utilise nulle part
                // ailleurs MaterialTheme.colorScheme). Piste atténuée pour la même teinte plutôt que
                // la couleur de fond -- sinon la piste se fondrait entièrement dans l'écran, ne
                // laissant voir qu'un segment flottant sans repère de bornes.
                color = Color(0xFF7CE0FF),
                trackColor = Color(0xFF7CE0FF).copy(alpha = 0.2f),
            )
        }

        // Crédit du placeholder actuel (point 53) -- discret, coin bas-gauche, jamais confondu
        // avec le contenu central (message/progression). Pas un habillage permanent : à retirer
        // quand un vrai logo d'artiste remplace ce placeholder.
        Text(
            stringResource(R.string.loading_logo_credit),
            color = Color.White.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}
