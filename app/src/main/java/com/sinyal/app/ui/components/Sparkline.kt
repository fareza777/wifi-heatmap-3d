package com.sinyal.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.wifi.SignalQuality

/**
 * RSSI history as an area chart.
 *
 * The vertical scale is pinned to the full dBm range rather than the data range,
 * so the curve's height means the same thing between sessions instead of
 * rescaling itself into looking dramatic.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
) {
    val guideColor = Ink.Stroke

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (values.size < 2) return@Canvas

            val w = size.width
            val h = size.height
            val stepX = w / (values.size - 1).toFloat()

            fun yFor(rssi: Int): Float = h - (SignalQuality.normalize(rssi) * h)

            drawBaselines(h, w, guideColor)

            val line = Path()
            val area = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) {
                    line.moveTo(x, y)
                    area.moveTo(x, h)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(w, h)
            area.close()

            val tint = signalColorFor(values.last())
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0f)),
                ),
            )
            drawPath(
                path = line,
                brush = Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.35f), tint),
                ),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(
                color = tint,
                radius = 3.5.dp.toPx(),
                center = Offset(w, yFor(values.last())),
            )
        }
    }
}

/** Faint guides at the -50 / -67 / -80 dBm decision points. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBaselines(
    h: Float,
    w: Float,
    guideColor: androidx.compose.ui.graphics.Color,
) {
    listOf(-50, -67, -80).forEach { marker ->
        val y = h - (SignalQuality.normalize(marker) * h)
        drawLine(
            color = guideColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1f,
        )
    }
}
