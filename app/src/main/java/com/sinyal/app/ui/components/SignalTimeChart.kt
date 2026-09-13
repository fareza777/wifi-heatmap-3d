package com.sinyal.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.TrackedAp

private const val FLOOR_DBM = -100f
private const val CEILING_DBM = -30f

/** One line on the chart: a network, its colour, and its readings. */
data class SignalSeries(
    val ap: TrackedAp,
    val color: Color,
)

/**
 * Several networks' signal strength over time, on one shared axis.
 *
 * Sharing the axis is the entire value: a single network's line tells you it
 * fluctuates, but two lines crossing tell you the moment one overtook the other,
 * which is what decides whether to switch band, move a router, or ignore the
 * neighbour's access point.
 *
 * The vertical scale is fixed at −100..−30 dBm rather than fitted to the data,
 * so a flat line reads as flat instead of being stretched into drama.
 */
@Composable
fun SignalTimeChart(
    series: List<SignalSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    /** Points the axis expects, so a short history draws from the left edge. */
    capacity: Int = 60,
) {
    val grid = Ink.Stroke
    val labelTone = TextTone.Tertiary
    val measurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gutter = 34.dp.toPx()
            val plotWidth = size.width - gutter
            if (plotWidth <= 0f) return@Canvas

            drawGrid(gutter, plotWidth, grid, labelTone, measurer)

            val stepX = plotWidth / (capacity - 1).coerceAtLeast(1).toFloat()

            series.forEach { entry ->
                val points = entry.ap.history
                if (points.size < 2) return@forEach

                val path = Path()
                points.forEachIndexed { index, rssi ->
                    // Anchored to the right edge so the newest reading is always
                    // at the same place while the history fills in behind it.
                    val x = size.width - (points.lastIndex - index) * stepX
                    val y = yFor(rssi, size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = entry.color,
                    style = Stroke(
                        width = if (entry.ap.isCurrent) 3.dp.toPx() else 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawCircle(
                    color = entry.color,
                    radius = 3.dp.toPx(),
                    center = Offset(size.width, yFor(points.last(), size.height)),
                )
            }
        }
    }
}

private fun yFor(rssi: Int, height: Float): Float {
    val fraction = ((rssi - FLOOR_DBM) / (CEILING_DBM - FLOOR_DBM)).coerceIn(0f, 1f)
    return height - fraction * height
}

/** Horizontal guides every 20 dB, labelled in the left gutter. */
private fun DrawScope.drawGrid(
    gutter: Float,
    plotWidth: Float,
    grid: Color,
    labelTone: Color,
    measurer: TextMeasurer,
) {
    val style = TextStyle(color = labelTone, fontSize = 9.sp)
    listOf(-40, -60, -80, -100).forEach { marker ->
        val y = yFor(marker, size.height)
        drawLine(
            color = grid,
            start = Offset(gutter, y),
            end = Offset(gutter + plotWidth, y),
            strokeWidth = 1f,
        )
        val label = measurer.measure("$marker", style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(0f, (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)),
        )
    }
}
