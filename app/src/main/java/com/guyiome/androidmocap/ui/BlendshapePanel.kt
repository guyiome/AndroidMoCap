package com.guyiome.androidmocap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R

/**
 * Valeurs en direct des blendshapes cochées dans [BlendshapeSelectionScreen]. Pivote par pas de
 * 90° selon que le téléphone est tenu à la verticale ou à l'horizontale ([panelRotationDegrees] --
 * calculé dans [MainScreen] à partir de l'orientation physique) : pas de rotation continue ici
 * comme pour les icônes du HUD, un bloc de texte tournant en continu serait illisible pendant la
 * transition -- juste les deux cas simples, verticale ou horizontale.
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
            .rotate(panelRotationDegrees)
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
