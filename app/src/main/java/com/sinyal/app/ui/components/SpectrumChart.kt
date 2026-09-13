package com.sinyal.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import com.sinyal.app.wifi.NearbyAp
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.ui.geometry.Size
import com.sinyal.app.ui.theme.SignalColor

private const val FLOOR_DBM = -100f
private const val CEILING_DBM = -30f

/**
 * The band drawn the way a spectrum analyser draws it.
 *
 * Each network becomes a hump centred on its carrier, as wide as the channel it
 * occupies and as tall as its signal. Two networks whose humps overlap are
 * genuinely interfering with each other — which a bar-per-channel chart cannot
 * show, because it has no way to draw a 40 MHz carrier sitting across four
 * channels at once.
 *
 * Overlap is the whole point of the picture, so humps are translucent and drawn
 * weakest-first: the strong ones stay legible on top without hiding what is
 * underneath them.
 */
@Composable
fun SpectrumChart(
    networks: List<NearbyAp>,
    minFrequencyMhz: Int,
    maxFrequencyMhz: Int,
    channelTicks: List<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    /** Channels worth marking out, e.g. the non-overlapping 1/6/11 in 2.4 GHz. */
    highlightChannels: List<Int> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val gridColor = Ink.StrokeStrong
    val axisLabel = TextTone.Tertiary
    val seriesLabel = TextTone.Secondary
    val clashTint = SignalColor.Dead

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val plotHeight = size.height - AXIS_SPACE
            val span = (maxFrequencyMhz - minFrequencyMhz).toFloat().coerceAtLeast(1f)

            fun xOf(frequency: Float): Float =
                (frequency - minFrequencyMhz) / span * size.width

            fun yOf(dbm: Float): Float {
                val fraction = ((dbm - FLOOR_DBM) / (CEILING_DBM - FLOOR_DBM))
                    .coerceIn(0f, 1f)
                return plotHeight - fraction * plotHeight
            }

            drawGuides(plotHeight, ::yOf, measurer, gridColor, axisLabel)
            drawChannelAxis(plotHeight, channelTicks, ::xOf, measurer, gridColor, axisLabel)

            // The clear channels, marked so the eye can check them against the
            // humps without counting ticks.
            channelTicks
                .filter { it.first in highlightChannels }
                .forEach { (_, frequency) ->
                    val x = xOf(frequency.toFloat())
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, plotHeight),
                        strokeWidth = 2.5f,
                    )
                }

            // The overlap is the whole reason to look at a spectrum rather than
            // a bar per channel, so it is painted first and underneath: every
            // rival carrier that actually crosses yours, shaded where it does.
            networks.firstOrNull { it.isCurrent }?.let { mine ->
                drawOverlap(mine, networks, ::xOf, plotHeight, clashTint)
            }

            networks
                .sortedBy { it.rssiDbm }
                // Yours is drawn last so it is never buried under a neighbour.
                .sortedBy { it.isCurrent }
                .forEach { network ->
                    drawNetwork(network, ::xOf, ::yOf, plotHeight, measurer, seriesLabel)
                }
        }
    }
}

private val AXIS_SPACE = 34f

private fun DrawScope.drawGuides(
    plotHeight: Float,
    yOf: (Float) -> Float,
    measurer: TextMeasurer,
    gridColor: Color,
    labelColor: Color,
) {
    listOf(-40f, -60f, -80f).forEach { level ->
        val y = yOf(level)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        drawText(
            textMeasurer = measurer,
            text = "${level.toInt()}",
            topLeft = Offset(2f, y - 26f),
            style = TextStyle(color = labelColor, fontSize = 9.sp),
        )
    }
    drawLine(
        color = gridColor,
        start = Offset(0f, plotHeight),
        end = Offset(size.width, plotHeight),
        strokeWidth = 2f,
    )
}

private fun DrawScope.drawChannelAxis(
    plotHeight: Float,
    ticks: List<Pair<Int, Int>>,
    xOf: (Float) -> Float,
    measurer: TextMeasurer,
    gridColor: Color,
    labelColor: Color,
) {
    ticks.forEach { (channel, frequency) ->
        val x = xOf(frequency.toFloat())
        drawLine(
            color = gridColor,
            start = Offset(x, plotHeight),
            end = Offset(x, plotHeight + 6f),
            strokeWidth = 1.5f,
        )
        drawText(
            textMeasurer = measurer,
            text = "$channel",
            topLeft = Offset(x - 8f, plotHeight + 8f),
            style = TextStyle(color = labelColor, fontSize = 9.sp),
        )
    }
}

/**
 * Where other carriers land on top of yours.
 *
 * Two networks sharing air is not visible in a hump chart until you look for
 * it, and it is the one thing on this screen that explains a fast link feeling
 * slow. Each crossing is shaded over the full height of the plot, so the width
 * of the shading is literally how much of your channel is contested.
 *
 * Carriers on the exact same centre frequency are skipped: that is co-channel,
 * where radios politely take turns, and shading the whole carrier red would
 * overstate a milder problem.
 */
private fun DrawScope.drawOverlap(
    mine: NearbyAp,
    networks: List<NearbyAp>,
    xOf: (Float) -> Float,
    plotHeight: Float,
    tint: Color,
) {
    val myHalf = mine.channelWidthMhz / 2f
    val myLow = mine.frequencyMhz - myHalf
    val myHigh = mine.frequencyMhz + myHalf

    networks.forEach { other ->
        if (other.bssid == mine.bssid) return@forEach
        if (other.frequencyMhz == mine.frequencyMhz) return@forEach

        val half = other.channelWidthMhz / 2f
        val low = maxOf(myLow, other.frequencyMhz - half)
        val high = minOf(myHigh, other.frequencyMhz + half)
        if (high <= low) return@forEach

        val left = xOf(low.toFloat())
        val right = xOf(high.toFloat())
        drawRect(
            color = tint.copy(alpha = OVERLAP_ALPHA),
            topLeft = Offset(left, 0f),
            size = Size(right - left, plotHeight),
        )
    }
}

/** Faint enough to stack: three overlapping rivals should read darker than one. */
private const val OVERLAP_ALPHA = 0.10f

/** One network: a rounded hump from carrier edge to carrier edge. */
private fun DrawScope.drawNetwork(
    network: NearbyAp,
    xOf: (Float) -> Float,
    yOf: (Float) -> Float,
    plotHeight: Float,
    measurer: TextMeasurer,
    labelColor: Color,
) {
    val half = network.channelWidthMhz / 2f
    val left = xOf(network.frequencyMhz - half)
    val right = xOf(network.frequencyMhz + half)
    val centre = xOf(network.frequencyMhz.toFloat())
    val peak = yOf(network.rssiDbm.toFloat())

    val hue = hueFor(network.bssid)
    val tint = Color.hsv(hue, 0.62f, 1f)

    val path = Path().apply {
        moveTo(left, plotHeight)
        // Shoulders sit a quarter-width in, which is what gives the classic
        // rounded-trapezoid silhouette rather than a triangle.
        cubicTo(
            left + (centre - left) * 0.45f, plotHeight,
            left + (centre - left) * 0.55f, peak,
            centre, peak,
        )
        cubicTo(
            right - (right - centre) * 0.55f, peak,
            right - (right - centre) * 0.45f, plotHeight,
            right, plotHeight,
        )
        close()
    }

    drawPath(path, color = tint.copy(alpha = if (network.isCurrent) 0.34f else 0.16f))
    drawPath(
        path = path,
        color = tint.copy(alpha = if (network.isCurrent) 1f else 0.65f),
        style = Stroke(
            width = if (network.isCurrent) 3.5f else 2f,
            cap = StrokeCap.Round,
        ),
    )

    val label = network.ssid.take(MAX_LABEL_CHARS)
    if (label.isNotBlank()) {
        drawText(
            textMeasurer = measurer,
            text = label,
            topLeft = Offset(centre - label.length * 2.6f, peak - 32f),
            style = TextStyle(
                color = if (network.isCurrent) tint else labelColor,
                fontSize = 10.sp,
            ),
        )
        // The peak value, so a hump can be read without measuring against the axis.
        drawText(
            textMeasurer = measurer,
            text = "${network.rssiDbm}",
            topLeft = Offset(centre - 12f, peak - 16f),
            style = TextStyle(color = tint, fontSize = 9.sp),
        )
    }
}

/**
 * Names the humps.
 *
 * The chart labels each peak, but peaks overlap and labels collide; a listed
 * legend is what makes a crowded band actually readable.
 */
@Composable
fun SpectrumLegend(networks: List<NearbyAp>, modifier: Modifier = Modifier) {
    if (networks.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        networks
            .sortedByDescending { it.rssiDbm }
            .take(MAX_LEGEND_ROWS)
            .forEach { network ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.hsv(hueFor(network.bssid), 0.62f, 1f)),
                    )
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (network.isCurrent) TextTone.Primary else TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.spectrum_legend_row,
                            network.channel,
                            network.channelWidthMhz,
                            network.rssiDbm,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                }
            }
    }
}

private const val MAX_LEGEND_ROWS = 8

/** Stable colour per access point, so the same network keeps its hue on refresh. */
private fun hueFor(bssid: String): Float =
    (abs(bssid.hashCode()) % 360).toFloat()

private const val MAX_LABEL_CHARS = 12
