package com.offlinelandlord.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameColorScheme = darkColorScheme(
    primary = Color(0xFFFFD166),
    onPrimary = Color(0xFF2B2100),
    secondary = Color(0xFF8BD3A9),
    background = Color(0xFF0B261B),
    onBackground = Color(0xFFF4F7F1),
    surface = Color(0xFF123B2A),
    onSurface = Color(0xFFF4F7F1),
    error = Color(0xFFFF8A80),
)

@Composable
fun OfflineLandlordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        content = content,
    )
}

