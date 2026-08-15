package com.guyiome.androidmocap.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.guyiome.androidmocap.R

/**
 * Dialogue de confirmation générique, réutilisable -- premier `AlertDialog` de l'app (aucun pattern
 * existant à reprendre). Pensé pour les actions destructrices peu fréquentes (ex. réinitialiser des
 * réglages qui ont coûté du temps à ajuster), pas pour un usage systématique : la plupart des
 * actions de cette app (désélectionner, désactiver un switch...) restent sans confirmation quand
 * elles sont bon marché à annuler.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350)),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
