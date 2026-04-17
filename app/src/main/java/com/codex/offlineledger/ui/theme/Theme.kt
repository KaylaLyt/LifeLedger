package com.codex.offlineledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LightPalette.Primary,
    onPrimary = LightPalette.OnPrimary,
    primaryContainer = LightPalette.PrimaryContainer,
    onPrimaryContainer = LightPalette.OnPrimaryContainer,
    secondary = LightPalette.Primary,
    onSecondary = LightPalette.OnPrimary,
    secondaryContainer = LightPalette.PrimaryContainer,
    onSecondaryContainer = LightPalette.OnPrimaryContainer,
    tertiary = LightPalette.Primary,
    onTertiary = LightPalette.OnPrimary,
    background = LightPalette.Background,
    onBackground = LightPalette.OnBackground,
    surface = LightPalette.Surface,
    onSurface = LightPalette.OnSurface,
    surfaceVariant = LightPalette.SurfaceVariant,
    onSurfaceVariant = LightPalette.OnSurfaceVariant,
    outline = LightPalette.Outline,
)

private val DarkColors = darkColorScheme(
    primary = DarkPalette.Primary,
    onPrimary = DarkPalette.OnPrimary,
    primaryContainer = DarkPalette.PrimaryContainer,
    onPrimaryContainer = DarkPalette.OnPrimaryContainer,
    secondary = DarkPalette.Primary,
    onSecondary = DarkPalette.OnPrimary,
    secondaryContainer = DarkPalette.PrimaryContainer,
    onSecondaryContainer = DarkPalette.OnPrimaryContainer,
    tertiary = DarkPalette.Primary,
    onTertiary = DarkPalette.OnPrimary,
    background = DarkPalette.Background,
    onBackground = DarkPalette.OnBackground,
    surface = DarkPalette.Surface,
    onSurface = DarkPalette.OnSurface,
    surfaceVariant = DarkPalette.SurfaceVariant,
    onSurfaceVariant = DarkPalette.OnSurfaceVariant,
    outline = DarkPalette.Outline,
)

@Composable
fun OfflineLedgerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LedgerTypography,
        content = content,
    )
}
