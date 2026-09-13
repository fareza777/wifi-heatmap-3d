package com.sinyal.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.wifi.SignalQuality

/**
 * Continuous colour ramp for signal strength.
 *
 * The stops sit at the normalised positions of the [SignalQuality] thresholds, so
 * the colour a user sees always agrees with the label next to it.
 */
private val Stops: List<Pair<Float, Color>> = listOf(
    0f to SignalColor.Dead,
    SignalQuality.normalize(-75) to SignalColor.Weak,
    SignalQuality.normalize(-67) to SignalColor.Fair,
    SignalQuality.normalize(-60) to SignalColor.Good,
    SignalQuality.normalize(-50) to SignalColor.Excellent,
    1f to SignalColor.Excellent,
)

/** Colour for a normalised 0f..1f signal strength. */
fun signalColorAt(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    for (i in 0 until Stops.lastIndex) {
        val (lowPos, lowColor) = Stops[i]
        val (highPos, highColor) = Stops[i + 1]
        if (f <= highPos) {
            val span = highPos - lowPos
            val t = if (span <= 0f) 0f else (f - lowPos) / span
            return lerp(lowColor, highColor, t)
        }
    }
    return Stops.last().second
}

/** Colour for a raw RSSI reading in dBm. */
fun signalColorFor(rssiDbm: Int): Color = signalColorAt(SignalQuality.normalize(rssiDbm))
