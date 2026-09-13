package com.sinyal.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sinyal.app.R

/**
 * Selectable accent colours.
 *
 * Each option carries its own values for light and dark rather than deriving one
 * from the other: a hue that reads well on near-black is usually too pale to be
 * legible as text on white, so the light variants are deliberately darker.
 */
enum class AccentPalette(
    @StringRes val label: Int,
    val swatch: Color,
    internal val lightDeep: Color,
    internal val lightBase: Color,
    internal val lightBright: Color,
    internal val darkDeep: Color,
    internal val darkBase: Color,
    internal val darkBright: Color,
) {
    VIOLET(
        R.string.accent_violet, Color(0xFF6C5CE7),
        Color(0xFF7C6BFF), Color(0xFF6C5CE7), Color(0xFF4C3BD4),
        Color(0xFF4C3BD4), Color(0xFF7C6BFF), Color(0xFFA78BFA),
    ),
    OCEAN(
        R.string.accent_ocean, Color(0xFF2F80ED),
        Color(0xFF56A0F5), Color(0xFF2F80ED), Color(0xFF1B5FBF),
        Color(0xFF1B5FBF), Color(0xFF2F80ED), Color(0xFF7FB6FF),
    ),
    TEAL(
        R.string.accent_teal, Color(0xFF0EA5A5),
        Color(0xFF3EC5C5), Color(0xFF0EA5A5), Color(0xFF067777),
        Color(0xFF067777), Color(0xFF0EA5A5), Color(0xFF5FDCDC),
    ),
    EMBER(
        R.string.accent_ember, Color(0xFFE8622C),
        Color(0xFFF58A5C), Color(0xFFE8622C), Color(0xFFB4451A),
        Color(0xFFB4451A), Color(0xFFE8622C), Color(0xFFFF9E75),
    ),
    ROSE(
        R.string.accent_rose, Color(0xFFD6336C),
        Color(0xFFE86192), Color(0xFFD6336C), Color(0xFFA31E4E),
        Color(0xFFA31E4E), Color(0xFFD6336C), Color(0xFFFF7BA6),
    ),
}

/** Applies an accent choice on top of a base palette. */
fun SinyalPalette.withAccent(accent: AccentPalette): SinyalPalette = copy(
    accentDeep = if (isLight) accent.lightDeep else accent.darkDeep,
    accentBase = if (isLight) accent.lightBase else accent.darkBase,
    accentBright = if (isLight) accent.lightBright else accent.darkBright,
    accentGlow = accent.swatch.copy(alpha = if (isLight) 0.12f else 0.20f),
)
