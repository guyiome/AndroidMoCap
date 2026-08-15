package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.network.VBridgerFormulas

/**
 * Consultation en lecture seule des paramètres actuellement envoyés à VTube Studio (point 41) --
 * toujours les 52 blendshapes ARKit bruts, plus les formules composites VBridger si
 * [useVBridgerTranslation] est actif. Première brique visible d'un futur écran d'édition (activer/
 * désactiver une formule, modifier ses coefficients ou les blendshapes qui l'alimentent) -- pour
 * l'instant, juste la liste de ce qui part réellement, groupée pour la lisibilité.
 *
 * `EyeLeftX`/`EyeLeftY` portent une icône d'avertissement distincte (même esprit que
 * [BlendshapeCatalog.unreliable] dans [BlendshapesScreen]) : reconstruits par symétrie miroir, pas
 * transcrits depuis la capture d'écran source de VBridger -- voir le kdoc de
 * [com.guyiome.androidmocap.network.VBridgerFormulas].
 */
@Composable
fun VtsFormulasScreen(useVBridgerTranslation: Boolean, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .blockClicksBehind()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.vts_formulas_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.vts_formulas_group_raw),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            VBridgerFormulas.rawArkitFormulas.forEach { formula ->
                VtsParameterRow(name = formula.outputName, unverified = false)
            }

            if (useVBridgerTranslation) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.vts_formulas_group_vbridger),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                VBridgerFormulas.vbridgerCompositeFormulas.forEach { formula ->
                    VtsParameterRow(
                        name = formula.outputName,
                        unverified = formula.outputName == "EyeLeftX" || formula.outputName == "EyeLeftY",
                    )
                }
            }
        }
    }
}

@Composable
private fun VtsParameterRow(name: String, unverified: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, color = Color.White)
        if (unverified) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = stringResource(R.string.cd_unverified_vts_formula),
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
