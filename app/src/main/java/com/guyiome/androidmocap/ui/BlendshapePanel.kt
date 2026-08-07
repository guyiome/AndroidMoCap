package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R

/**
 * Valeurs en direct des blendshapes cochées dans [BlendshapeSelectionScreen]. Pivote par pas de
 * 90° selon que le téléphone est tenu à la verticale ou à l'horizontale ([panelRotationDegrees] --
 * calculé dans [MainScreen] à partir de l'orientation physique) : pas de rotation continue ici
 * comme pour les icônes du HUD, un bloc de texte tournant en continu serait illisible pendant la
 * transition -- juste les deux cas simples, verticale ou horizontale.
 *
 * ⚠️ Pivot volontairement au bas du bloc (`TransformOrigin(0.5f, 1f)`), pas au centre par défaut
 * de [androidx.compose.ui.draw.rotate] : le panneau est ancré en haut de l'écran
 * ([MainScreen] : `Alignment.TopStart`), et son bloc de texte est nettement plus large que haut
 * (peu de lignes, mais des noms de blendshapes longs). Avec un pivot centré, une rotation à ±90°
 * fait déborder le bloc *au-dessus* du bord physique de l'écran (le rectangle tourné, deux fois
 * plus "haut" que l'original une fois sur le côté, reste centré sur le même point) -- il se
 * retrouve alors sous le bandeau système, qui masque le début de chaque nom (retour utilisateur du
 * 7 août 2026 : "cheekPuff" affiché "eekPuff", etc., uniquement en tenant le téléphone à
 * l'horizontale). Pivoter depuis le bas du bloc fait déborder la rotation *vers l'intérieur* de
 * l'écran plutôt que vers son bord -- combiné à `windowInsetsPadding(WindowInsets.safeDrawing)`
 * côté [MainScreen] (marge de sécurité pour ce qui reste), pas revérifié visuellement sur device
 * (aucun device dans ce sandbox).
 */
@Composable
fun BlendshapePanel(
    values: List<Pair<String, Float>>,
    panelRotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return

    Column(
        modifier = modifier
            .graphicsLayer(rotationZ = panelRotationDegrees, transformOrigin = TransformOrigin(0.5f, 1f))
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        values.forEach { (name, score) ->
            Text(
                stringResource(R.string.blendshape_value_format, name, "%.2f".format(score)),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
