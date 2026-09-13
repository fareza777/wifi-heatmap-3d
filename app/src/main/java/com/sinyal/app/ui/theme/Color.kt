package com.sinyal.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware accessors.
 *
 * These read [LocalPalette] through composable getters, so `Ink.Base` means
 * whatever the active theme says it means. Values needed inside a `Canvas`
 * lambda must be captured into a local first — draw scopes are not composable.
 */
object Ink {
    val Base: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.base
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceHigh
    val Stroke: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.stroke
    val StrokeStrong: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.strokeStrong
}

object TextTone {
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
    val Secondary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
    val Tertiary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textTertiary
}

object Accent {
    val Deep: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentDeep
    val Base: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentBase
    val Bright: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentBright
    val Glow: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentGlow
}

/**
 * Signal strength ramp, identical in both themes.
 *
 * Kept as plain constants because the ramp is read from draw scopes and from
 * the bitmap exporter, neither of which can touch a CompositionLocal. The
 * values are chosen to stay legible on white and on near-black alike, so one
 * set genuinely serves both.
 */
object SignalColor {
    val Excellent = Color(0xFF10C98E)
    val Good = Color(0xFF7BC94A)
    val Fair = Color(0xFFF0A81E)
    val Weak = Color(0xFFF5763C)
    val Dead = Color(0xFFEC3D5C)
}
