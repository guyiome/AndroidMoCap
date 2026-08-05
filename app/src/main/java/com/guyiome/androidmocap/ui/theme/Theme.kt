package com.guyiome.androidmocap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AndroidMoCapColorScheme = darkColorScheme(
    primary = Color(0xFF7CE0FF),
    secondary = Color(0xFF9FE7B0),
    background = Color(0xFF10131A),
    surface = Color(0xFF1B1F27),
)

@Composable
fun AndroidMoCapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AndroidMoCapColorScheme,
        content = content,
    )
}
