package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.guyiome.androidmocap.logging.LogLevel

/**
 * Réglages de journalisation (revue technique, point 50) -- cinquième catégorie de [SettingsScreen]
 * (voir rapport technique, point 21) : niveau minimal conservé dans le fichier de logs exportable
 * (ERROR par défaut, réglable jusqu'à WARN/INFO), et partage de ce fichier via le système
 * ([MainViewModel.buildShareLogsIntent]). Le bouton de partage reste toujours visible (pas caché
 * derrière le déverrouillage debug de [DiagnosticsScreen]) -- son but est justement de permettre à
 * n'importe quel utilisateur de remonter un souci, pas seulement au développeur.
 */
@Composable
fun LoggingSettingsScreen(
    logLevel: LogLevel,
    hasLogsToShare: Boolean,
    onClose: () -> Unit,
    onSetLogLevel: (LogLevel) -> Unit,
    onShareLogs: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.logging_settings_title),
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
                stringResource(R.string.logging_level_label),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = logLevel == LogLevel.ERROR,
                    onClick = { onSetLogLevel(LogLevel.ERROR) },
                    label = { Text(stringResource(R.string.logging_level_error)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = logLevel == LogLevel.WARN,
                    onClick = { onSetLogLevel(LogLevel.WARN) },
                    label = { Text(stringResource(R.string.logging_level_warn)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = logLevel == LogLevel.INFO,
                    onClick = { onSetLogLevel(LogLevel.INFO) },
                    label = { Text(stringResource(R.string.logging_level_info)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.logging_explanation),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(24.dp))
            Button(onClick = onShareLogs, enabled = hasLogsToShare) {
                Text(stringResource(R.string.logging_share_button))
            }
            if (!hasLogsToShare) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.logging_share_empty_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
