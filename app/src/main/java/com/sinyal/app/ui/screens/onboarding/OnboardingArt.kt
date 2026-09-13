package com.sinyal.app.ui.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.components.signalColorAt
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import kotlin.math.sin

private val ArtHeight = 220.dp

/** Page 1: signal radiating outward and fading — the invisible thing being measured. */
@Composable
fun RadiatingArt(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "radiate").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "radiatePhase",
    )

    val accentSoft = Accent.Bright
    val accentSolid = Accent.Base
    val panelColor = Ink.SurfaceHigh
    val panelStroke = Ink.StrokeStrong

    Box(modifier.fillMaxWidth().height(ArtHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(ArtHeight)) {
            val center = Offset(size.width / 2f, size.height * 0.62f)
            val maxRadius = size.minDimension * 0.42f

            repeat(4) { index ->
                val fraction = (phase + index / 4f) % 1f
                drawArc(
                    color = accentSoft.copy(alpha = 0.55f * (1f - fraction)),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - maxRadius * fraction,
                        center.y - maxRadius * fraction,
                    ),
                    size = Size(maxRadius * 2 * fraction, maxRadius * 2 * fraction),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(accentSolid, radius = 9.dp.toPx(), center = center)
        }
    }
}

/** Page 2: the two sensors the scan depends on, shown as what they are. */
@Composable
fun PermissionArt(modifier: Modifier = Modifier) {
    val accentSoft = Accent.Bright
    val accentSolid = Accent.Base
    val panelColor = Ink.SurfaceHigh
    val panelStroke = Ink.StrokeStrong

    Box(modifier.fillMaxWidth().height(ArtHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(ArtHeight)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val boxSize = 92.dp.toPx()
            val gap = 26.dp.toPx()

            // Location: a pin.
            val leftCenter = Offset(cx - boxSize / 2f - gap / 2f, cy)
            drawRoundedPanel(leftCenter, boxSize, panelColor, panelStroke)
            drawCircle(
                color = accentSoft,
                radius = 15.dp.toPx(),
                center = leftCenter.copy(y = leftCenter.y - 10.dp.toPx()),
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(
                color = accentSoft,
                radius = 4.dp.toPx(),
                center = leftCenter.copy(y = leftCenter.y - 10.dp.toPx()),
            )
            drawLine(
                color = accentSoft,
                start = leftCenter.copy(y = leftCenter.y + 3.dp.toPx()),
                end = leftCenter.copy(y = leftCenter.y + 20.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Camera: a body with a lens.
            val rightCenter = Offset(cx + boxSize / 2f + gap / 2f, cy)
            drawRoundedPanel(rightCenter, boxSize, panelColor, panelStroke)
            drawCircle(
                color = accentSoft,
                radius = 16.dp.toPx(),
                center = rightCenter,
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(color = accentSoft, radius = 6.dp.toPx(), center = rightCenter)
        }
    }
}

/** Page 3: the walk itself, leaving coloured readings behind it. */
@Composable
fun WalkPathArt(modifier: Modifier = Modifier) {
    val accentSoft = Accent.Bright
    val accentSolid = Accent.Base
    val panelColor = Ink.SurfaceHigh
    val panelStroke = Ink.StrokeStrong

    Box(modifier.fillMaxWidth().height(ArtHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(ArtHeight)) {
            val margin = 40.dp.toPx()
            val usableWidth = size.width - margin * 2
            val midY = size.height / 2f
            val samples = 26

            repeat(samples) { index ->
                val t = index / (samples - 1f)
                val x = margin + usableWidth * t
                val y = midY + sin(t * 6.2f) * (size.height * 0.22f)
                // Strength rises toward the middle, the way it does near a router.
                val strength = 1f - kotlin.math.abs(t - 0.5f) * 1.7f
                drawCircle(
                    color = signalColorAt(strength.coerceIn(0f, 1f)),
                    radius = (5 + 4 * strength.coerceIn(0f, 1f)).dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedPanel(
    center: Offset,
    boxSize: Float,
    panelColor: Color,
    strokeColor: Color,
) {
    drawRoundRect(
        color = panelColor,
        topLeft = Offset(center.x - boxSize / 2f, center.y - boxSize / 2f),
        size = Size(boxSize, boxSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(center.x - boxSize / 2f, center.y - boxSize / 2f),
        size = Size(boxSize, boxSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
}

/** Page 4: a miniature of the result — relief tiles, a route, and two pins. */
@Composable
fun ResultPreviewArt(modifier: Modifier = Modifier) {
    val accentSoft = Accent.Bright
    val accentSolid = Accent.Base
    val panelColor = Ink.SurfaceHigh
    val panelStroke = Ink.StrokeStrong

    Box(modifier.fillMaxWidth().height(ArtHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(ArtHeight)) {
            val cols = 7
            val rows = 5
            val cell = size.width * 0.085f
            val originX = size.width / 2f - cols * cell / 2f
            val originY = size.height * 0.62f - rows * cell * 0.5f

            // A flat isometric grid: enough to read as "a plan", not a real render.
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val strength = 1f - ((col + row) / (cols + rows).toFloat())
                    val x = originX + col * cell + row * cell * 0.35f
                    val y = originY + row * cell * 0.55f
                    val lift = strength * cell * 0.35f
                    drawRect(
                        color = signalColorAt(strength),
                        topLeft = Offset(x, y - lift),
                        size = Size(cell * 0.92f, cell * 0.55f),
                    )
                }
            }

            val routeY = originY + rows * cell * 0.28f
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(originX + cell * 0.5f, routeY + cell),
                end = Offset(originX + cols * cell, routeY - cell * 0.2f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )

            drawCircle(
                color = signalColorAt(1f),
                radius = 8.dp.toPx(),
                center = Offset(originX + cols * cell * 0.25f, originY),
            )
            drawCircle(
                color = signalColorAt(0f),
                radius = 8.dp.toPx(),
                center = Offset(originX + cols * cell * 0.95f, originY + rows * cell * 0.5f),
            )
        }
    }
}
