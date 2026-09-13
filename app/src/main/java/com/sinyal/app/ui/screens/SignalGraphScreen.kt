package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.SignalSeries
import com.sinyal.app.ui.components.SignalTimeChart
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.SignalTracker
import com.sinyal.app.wifi.TrackedAp
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

/** Eight hues far enough apart to stay separable when lines overlap. */
private val LineColors = listOf(
    Color(0xFF4C8DFF),
    Color(0xFF10C98E),
    Color(0xFFF0A81E),
    Color(0xFFEC3D5C),
    Color(0xFF9B6BFF),
    Color(0xFF16C0D8),
    Color(0xFFFF7A45),
    Color(0xFFB0BF3A),
)

/** How many lines can share the chart before it turns into noise. */
private const val MAX_LINES = 8

class SignalGraphViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)
    private val monitor = WifiMonitor(app)
    private val tracker = SignalTracker()

    private val _tracked = MutableStateFlow<List<TrackedAp>>(emptyList())
    val tracked: StateFlow<List<TrackedAp>> = _tracked.asStateFlow()

    private val _elapsed = MutableStateFlow(0)
    val elapsed: StateFlow<Int> = _elapsed.asStateFlow()

    private var polling: Job? = null

    /**
     * Samples the platform's scan cache on a short interval, and asks for a
     * fresh sweep far less often.
     *
     * Since Android 9 a foreground app gets four [WifiScanner.requestScan] calls
     * per two minutes; asking more often is silently ignored, so the refresh is
     * spaced to stay inside that budget while the cache is read continuously.
     */
    fun start() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                if (tick % REFRESH_EVERY == 0) {
                    withContext(Dispatchers.Default) { scanner.requestScan() }
                }
                val bssid = withContext(Dispatchers.Default) { monitor.read().bssid }
                val scan = withContext(Dispatchers.Default) { scanner.cachedResults(bssid) }
                if (scan.isNotEmpty()) {
                    _tracked.value = tracker.record(scan)
                    _elapsed.value = (tick + 1) * (SAMPLE_MS / 1000).toInt()
                }
                tick++
                delay(SAMPLE_MS)
            }
        }
    }

    fun stop() {
        polling?.cancel()
        polling = null
    }

    fun reset() {
        tracker.reset()
        _tracked.value = emptyList()
        _elapsed.value = 0
    }

    private companion object {
        const val SAMPLE_MS = 2_000L
        /** Every 16th sample is 32 s apart — under the four-per-two-minutes cap. */
        const val REFRESH_EVERY = 16
    }
}

/**
 * Signal strength over time, several networks at once.
 *
 * One reading is close to meaningless — Wi-Fi swings a few dB while nothing in
 * the room moves. What answers a real question is the shape over a minute: a
 * line that holds steady, one that sags when the microwave runs, or two that
 * cross when the phone would have been better off on the other band.
 */
@Composable
fun SignalGraphScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignalGraphViewModel = viewModel(),
) {
    val tracked by viewModel.tracked.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()

    // Sampling stops with the screen: a background poll would drain the battery
    // for a chart nobody is looking at.
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    val charted = tracked.take(MAX_LINES)
    val series = charted.mapIndexed { index, ap ->
        SignalSeries(ap = ap, color = LineColors[index % LineColors.size])
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            BackBar(title = stringResource(R.string.graph_title), onBack = onBack)

            Spacer(Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (charted.isEmpty()) R.string.graph_waiting else R.string.graph_axis_unit,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Text(
                        text = stringResource(R.string.graph_window, elapsed),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                SignalTimeChart(series = series, modifier = Modifier.fillMaxWidth())
            }

            if (charted.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.graph_tracked_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                series.forEach { entry ->
                    LegendRow(entry)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            ResetButton(onClick = viewModel::reset)

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.graph_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LegendRow(entry: SignalSeries) {
    val ap = entry.ap
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(entry.color),
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = if (ap.isCurrent) {
                        stringResource(R.string.graph_connected_suffix, ap.ssid)
                    } else {
                        ap.ssid
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ap.isCurrent) Accent.Bright else TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.graph_line_detail,
                        ap.band.label,
                        ap.channel,
                        ap.average,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.unit_dbm, ap.current),
                    style = MaterialTheme.typography.titleMedium,
                    color = entry.color,
                )
                Text(
                    text = if (ap.spreadDb > 0) {
                        stringResource(R.string.graph_spread, ap.spreadDb)
                    } else {
                        stringResource(R.string.value_none)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Raised)
            .border(1.dp, Ink.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.graph_restart),
            style = MaterialTheme.typography.labelLarge,
            color = TextTone.Secondary,
        )
    }
}
