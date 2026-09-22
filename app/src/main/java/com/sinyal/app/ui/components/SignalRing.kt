package com.sinyal.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val TOTAL_SWEEP = 270f
private const val TICK_COUNT = 44

/**
 * The hero gauge: an instrument dial for the connected AP.
 *
 * The dial reads as a scale rather than a single number — ticks are tinted along
 * the whole signal ramp so the current reading is always seen in context of what
 * good and bad look like.
 */
@Composable
fun SignalRing(
    fraction: Float,
    rssiDbm: Int,
    qualityLabel: String,
    connected: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 268.dp,
) {
    val target = if (connected) fraction.coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "signalFraction",
    )
    val liveColor by animateColorAsState(
        targetValue = if (connected) signalColorAt(animatedFraction) else Ink.StrokeStrong,
        animationSpec = tween(500),
        label = "signalColor",
    )

    val ripple by rememberInfiniteTransition(label = "ripple").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripplePhase",
    )

    // Draw scopes cannot read a CompositionLocal, so the theme colours are
    // resolved here and handed down.
    val trackColor = Ink.Stroke

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val stroke = 16.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f - 22.dp.toPx()

            if (connected) drawRipples(center, radius, ripple, liveColor)
            drawTicks(center, radius + 20.dp.toPx(), animatedFraction, connected)
            drawTrack(center, radius, stroke, trackColor)
            if (connected && animatedFraction > 0f) {
                drawValueArc(center, radius, stroke, animatedFraction, liveColor)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (connected) "$rssiDbm" else "—",
                    // The figure scales with the ring, or a compact hero ends up
                    // with a number that crowds its own arc.
                    style = if (diameter >= LARGE_RING) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    color = TextTone.Primary,
                )
                if (connected) {
                    Text(
                        text = "dBm",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTone.Tertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    )
                }
            }
            Text(
                text = qualityLabel,
                style = MaterialTheme.typography.titleMedium,
                color = if (connected) liveColor else TextTone.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Concentric pulses, evenly spaced from a single phase value. */
private fun DrawScope.drawRipples(center: Offset, radius: Float, phase: Float, color: Color) {
    repeat(3) { i ->
        val frac = (phase + i / 3f) % 1f
        val r = radius * (0.42f + 0.58f * frac)
        drawCircle(
            color = color.copy(alpha = 0.16f * (1f - frac)),
            radius = r,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

/** Scale marks tinted along the ramp; marks past the reading fade back. */
private fun DrawScope.drawTicks(
    center: Offset,
    radius: Float,
    fraction: Float,
    connected: Boolean,
) {
    val inner = radius
    val outer = radius + 7.dp.toPx()
    repeat(TICK_COUNT + 1) { i ->
        val t = i / TICK_COUNT.toFloat()
        val angleRad = Math.toRadians((START_ANGLE + TOTAL_SWEEP * t).toDouble())
        val dx = cos(angleRad).toFloat()
        val dy = sin(angleRad).toFloat()
        val reached = connected && t <= fraction
        drawLine(
            color = if (reached) signalColorAt(t).copy(alpha = 0.95f)
            else signalColorAt(t).copy(alpha = 0.13f),
            start = Offset(center.x + dx * inner, center.y + dy * inner),
            end = Offset(center.x + dx * outer, center.y + dy * outer),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawTrack(
    center: Offset,
    radius: Float,
    stroke: Float,
    trackColor: Color,
) {
    drawArc(
        color = trackColor,
        startAngle = START_ANGLE,
        sweepAngle = TOTAL_SWEEP,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** Drawn three times at widening strokes to fake a bloom without a blur pass. */
private fun DrawScope.drawValueArc(
    center: Offset,
    radius: Float,
    stroke: Float,
    fraction: Float,
    color: Color,
) {
    val sweep = TOTAL_SWEEP * fraction
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    listOf(2.6f to 0.10f, 1.7f to 0.16f, 1f to 1f).forEach { (widthScale, alpha) ->
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke * widthScale, cap = StrokeCap.Round),
        )
    }

    val endRad = Math.toRadians((START_ANGLE + sweep).toDouble())
    val endPoint = Offset(
        center.x + cos(endRad).toFloat() * radius,
        center.y + sin(endRad).toFloat() * radius,
    )
    drawCircle(color = Color.White, radius = stroke * 0.24f, center = endPoint)
}

/** At or above this the ring has room for the display-size figure. */
private val LARGE_RING = 240.dp
