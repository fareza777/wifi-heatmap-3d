package com.sinyal.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Ink

/**
 * The familiar Wi-Fi fan, drawn with only as many arcs lit as the signal earns.
 *
 * A dBm figure means nothing to most people and a bare colour swatch says
 * "good or bad" without saying how much. The arc count is the one signal
 * notation everybody already reads, off their own status bar — so the number,
 * the colour and the shape all say the same thing, three ways.
 */
@Composable
fun WifiSignalIcon(
    rssiDbm: Int,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val level = levelOf(rssiDbm)
    val lit = signalColorFor(rssiDbm)
    val dim = Ink.Stroke

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val width = this.size.width
            val height = this.size.height
            val stroke = width * 0.11f

            // The fan radiates from a dot near the bottom, the way the platform
            // icon does, so the arcs read as one shape rather than stacked bows.
            val originX = width / 2f
            val originY = height * 0.86f

            drawCircle(
                color = if (level >= 1) lit else dim,
                radius = stroke * 0.75f,
                center = Offset(originX, originY),
            )

            repeat(ARCS) { index ->
                val radius = width * (0.24f + 0.17f * index)
                drawArc(
                    color = if (level >= index + 2) lit else dim,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = Offset(originX - radius, originY - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * Zero to four, on the thresholds Android's own Wi-Fi picker uses.
 *
 * Deliberately not derived from the heatmap's adaptive scale: that one stretches
 * to whatever range a single scan happened to cover, which is right for
 * comparing corners of one room and wrong for an icon meant to mean the same
 * thing every time it is drawn.
 */
private fun levelOf(rssiDbm: Int): Int = when {
    rssiDbm >= -55 -> 4
    rssiDbm >= -66 -> 3
    rssiDbm >= -77 -> 2
    rssiDbm >= -88 -> 1
    else -> 0
}

/** Three arcs plus the dot, giving five distinguishable states. */
private const val ARCS = 3
private const val START_ANGLE = -135f
private const val SWEEP_ANGLE = 90f
