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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
 * Écran de sélection des blendshapes à afficher sur la page principale -- catalogue complet des
 * 52 blendshapes ARKit, groupés par catégorie repliable, avec recherche. La sélection n'est PAS
 * persistée : elle est remise à zéro à chaque lancement de l'app, comme demandé.
 */
@Composable
fun BlendshapeSelectionScreen(
    selectedNames: Set<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<BlendshapeCategory>()) }

    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
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

            Spacer(Modifier.height(12.dp))

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
