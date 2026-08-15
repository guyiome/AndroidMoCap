package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.tracking.BlendshapeCatalog
import com.guyiome.androidmocap.tracking.BlendshapeCategory

/**
 * Libellé affiché pour une catégorie -- [BlendshapeCategory.label] reste la valeur technique/de
 * secours (fonction pure, pas d'accès à `stringResource` en dehors d'un `@Composable`) ; c'est ce
 * mapping-ci qui fournit le texte localisé réellement affiché à l'écran.
 */
@Composable
private fun BlendshapeCategory.displayLabel(): String = when (this) {
    BlendshapeCategory.BROW -> stringResource(R.string.category_brow)
    BlendshapeCategory.EYE -> stringResource(R.string.category_eye)
    BlendshapeCategory.CHEEK -> stringResource(R.string.category_cheek)
    BlendshapeCategory.NOSE -> stringResource(R.string.category_nose)
    BlendshapeCategory.JAW -> stringResource(R.string.category_jaw)
    BlendshapeCategory.MOUTH -> stringResource(R.string.category_mouth)
    BlendshapeCategory.TONGUE -> stringResource(R.string.category_tongue)
}

/**
 * Catégorie de premier niveau dédiée aux blendshapes -- promue depuis un sous-écran de
 * "Affichage & confort" (ex-`BlendshapeSelectionScreen`) parce qu'elle est appelée à grossir : la
 * pondération par blendshape (réglage fin à venir, pas encore construit) trouvera sa place ici.
 * Catalogue complet des 52 blendshapes ARKit, groupés par catégorie repliable, avec recherche. Par
 * défaut, la sélection n'est PAS persistée (remise à zéro à chaque lancement de l'app, comportement
 * historique) -- [persistSelectionEnabled] permet de la conserver d'une session à l'autre, voir
 * revue technique point 18. [onDeselectAll] vide la sélection sans confirmation (bon marché à
 * refaire) ; [onResetWeights] passe par [ConfirmationDialog] avant d'agir (les pondérations, une
 * fois le réglage fin construit, seront coûteuses à reperdre).
 */
@Composable
fun BlendshapesScreen(
    selectedNames: Set<String>,
    onToggle: (String) -> Unit,
    persistSelectionEnabled: Boolean,
    onSetPersistSelection: (Boolean) -> Unit,
    onDeselectAll: () -> Unit,
    onResetWeights: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<BlendshapeCategory>()) }
    var showResetWeightsConfirmation by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .blockClicksBehind()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.blendshape_selection_title),
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.display_persist_selection_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = persistSelectionEnabled, onCheckedChange = onSetPersistSelection)
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDeselectAll, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.blendshape_deselect_all_button))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showResetWeightsConfirmation = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reset))
                }
            }
            if (showResetWeightsConfirmation) {
                ConfirmationDialog(
                    title = stringResource(R.string.blendshape_reset_weights_confirm_title),
                    message = stringResource(R.string.blendshape_reset_weights_confirm_message),
                    confirmLabel = stringResource(R.string.action_reset),
                    onConfirm = {
                        onResetWeights()
                        showResetWeightsConfirmation = false
                    },
                    onDismiss = { showResetWeightsConfirmation = false },
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (query.isBlank()) {
                    BlendshapeCatalog.byCategory.forEach { (category, names) ->
                        val expanded = category in expandedCategories
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategories = if (expanded) {
                                        expandedCategories - category
                                    } else {
                                        expandedCategories + category
                                    }
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                category.displayLabel(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                        if (expanded) {
                            names.forEach { name ->
                                BlendshapeSelectionRow(
                                    name = name,
                                    checked = name in selectedNames,
                                    onToggle = { onToggle(name) },
                                )
                            }
                        }
                    }
                } else {
                    val matches = BlendshapeCatalog.all
                        .map { it.second }
                        .filter { it.contains(query, ignoreCase = true) }
                    matches.forEach { name ->
                        BlendshapeSelectionRow(
                            name = name,
                            checked = name in selectedNames,
                            onToggle = { onToggle(name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlendshapeSelectionRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Text(name, color = Color.White)
        // Avertissement discret, compact (petite icône, pas de texte) pour les blendshapes connus
        // pour être peu fiables chez MediaPipe -- voir BlendshapeCatalog.unreliable et le rapport
        // technique, point 17. Purement informatif : n'empêche pas la sélection.
        if (name in BlendshapeCatalog.unreliable) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = stringResource(R.string.cd_unreliable_blendshape),
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
