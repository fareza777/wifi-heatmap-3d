package com.sinyal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun schemeFor(palette: SinyalPalette) = if (palette.isLight) {
    lightColorScheme(
        primary = palette.accentBase,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = palette.base,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceHigh,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.strokeStrong,
        outlineVariant = palette.stroke,
        error = SignalColor.Dead,
    )
} else {
    darkColorScheme(
        primary = palette.accentBase,
        onPrimary = palette.textPrimary,
        background = palette.base,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceHigh,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.strokeStrong,
        outlineVariant = palette.stroke,
        error = SignalColor.Dead,
    )
}

/** Resolves [ThemeMode] against the device setting and publishes the palette. */
@Composable
fun SinyalTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    accent: AccentPalette = AccentPalette.VIOLET,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = (if (dark) DarkPalette else LightPalette).withAccent(accent)

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = SinyalTypography,
            content = content,
        )
    }
}
