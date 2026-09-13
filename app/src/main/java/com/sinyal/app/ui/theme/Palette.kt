package com.sinyal.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import com.sinyal.app.R

/**
 * Every colour the app draws with, for one theme.
 *
 * Held in a [staticCompositionLocalOf] rather than as constants so that the same
 * call sites work in both themes. The accessor objects below keep the original
 * `Ink.Base` / `TextTone.Primary` spelling, which is why 200-odd usages did not
 * have to be rewritten when light mode arrived.
 */
data class SinyalPalette(
    val isLight: Boolean,
    val base: Color,
    val raised: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val stroke: Color,
    val strokeStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentDeep: Color,
    val accentBase: Color,
    val accentBright: Color,
    val accentGlow: Color,
    /** Ground the 3D scene is drawn against; must contrast with the heatmap. */
    val sceneFloor: Color,
    val sceneWall: Color,
)

/**
 * Light is the default, and is built as its own design rather than an inverted
 * dark theme: near-white ground, white cards separated by hairlines instead of
 * elevation, and an accent dark enough to read as text on white.
 */
val LightPalette = SinyalPalette(
    isLight = true,
    base = Color(0xFFF4F6FB),
    raised = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFFFFFFF),
    stroke = Color(0x140F172A),
    strokeStrong = Color(0x2E0F172A),
    textPrimary = Color(0xFF0F1420),
    textSecondary = Color(0xFF4A5266),
    textTertiary = Color(0xFF8A91A3),
    accentDeep = Color(0xFF7C6BFF),
    accentBase = Color(0xFF6C5CE7),
    accentBright = Color(0xFF4C3BD4),
    accentGlow = Color(0x1F6C5CE7),
    sceneFloor = Color(0xFFE3E7F0),
    sceneWall = Color(0xFFC9D0DE),
)

val DarkPalette = SinyalPalette(
    isLight = false,
    base = Color(0xFF06070A),
    raised = Color(0xFF0C0E15),
    surface = Color(0xFF12141D),
    surfaceHigh = Color(0xFF1A1D28),
    stroke = Color(0x14FFFFFF),
    strokeStrong = Color(0x24FFFFFF),
    textPrimary = Color(0xFFEEF1F7),
    textSecondary = Color(0xFF9AA1B4),
    textTertiary = Color(0xFF5C6376),
    accentDeep = Color(0xFF4C3BD4),
    accentBase = Color(0xFF7C6BFF),
    accentBright = Color(0xFFA78BFA),
    accentGlow = Color(0x337C6BFF),
    sceneFloor = Color(0xFF1B1F2B),
    sceneWall = Color(0xFFB9C2D6),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Which theme the app should use; [SYSTEM] follows the device setting. */
enum class ThemeMode(@StringRes val label: Int) {
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    SYSTEM(R.string.theme_system),
}
