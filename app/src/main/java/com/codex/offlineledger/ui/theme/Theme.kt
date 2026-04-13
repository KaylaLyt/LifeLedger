package com.codex.offlineledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Fern,
    secondary = Clay,
    tertiary = Moss,
    surface = Sand,
    background = Sand,
    onPrimary = Sand,
    onSecondary = Sand,
    onBackground = Bark,
    onSurface = Bark,
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    secondary = Clay,
    tertiary = Sand,
    surface = Bark,
    background = ColorTokens.DarkBackground,
    onPrimary = Bark,
    onSecondary = Sand,
    onBackground = Sand,
    onSurface = Sand,
)

private object ColorTokens {
    val DarkBackground = androidx.compose.ui.graphics.Color(0xFF1F221E)
}

@Composable
fun OfflineLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = LedgerTypography,
        content = content,
    )
}
