package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.Sparkline
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.LinkInspector
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One telemetry tick. */
data class TelemetrySample(
    val rssiDbm: Int,
    val linkMbps: Int,
    val txMbps: Int?,
    val rxMbps: Int?,
    val channel: Int,
)

data class RadioLabUiState(
    val connected: Boolean = false,
    val ssid: String? = null,
    val samples: List<TelemetrySample> = emptyList(),
    val rssiNow: Int = -127,
    val rateNow: Int = 0,
    val channelNow: Int = 0,
    val rssiStdDev: Double? = null,
    val running: Boolean = false,
)

/**
 * The closest thing Android has to a channel-state lab.
 *
 * Real CSI — per-subcarrier amplitude and phase, the ESP32-CSI research
 * firmware exposes — never reaches an app: the Wi-Fi driver holds it below the
 * API surface. What *does* come out is link telemetry, read often enough to
 * watch it breathe: RSSI, negotiated link speed, and per-direction PHY rates
 * at five samples a second. Saying so on screen is the honest version of
 * "CSI", and the version the radio can actually deliver.
 */
class RadioLabViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val inspector = LinkInspector(app)

    private val _state = MutableStateFlow(RadioLabUiState())
    val state: StateFlow<RadioLabUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        resume()
    }

    fun resume() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(running = true) }
            while (isActive) {
                tick()
                delay(SAMPLE_MS)
            }
        }
    }

    fun pause() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    override fun onCleared() {
        job?.cancel()
    }

    private fun tick() {
        val snapshot: WifiSnapshot = monitor.read()
        val rates = if (snapshot.connected) inspector.rates() else null
        val sample = TelemetrySample(
            rssiDbm = snapshot.rssiDbm,
            linkMbps = rates?.txMbps ?: snapshot.linkSpeedMbps,
            txMbps = rates?.txMbps,
            rxMbps = rates?.rxMbps,
            channel = snapshot.channel,
        )
        _state.update { current ->
            val samples = (current.samples + sample).takeLast(MAX_SAMPLES)
            current.copy(
                connected = snapshot.connected,
                ssid = snapshot.ssid,
                samples = samples,
                rssiNow = snapshot.rssiDbm,
                rateNow = sample.linkMbps,
                channelNow = snapshot.channel,
                rssiStdDev = stdDev(samples.map { it.rssiDbm }),
            )
        }
    }

    private fun stdDev(values: List<Int>): Double? {
        if (values.size < 4) return null
        val mean = values.average()
        return sqrt(values.sumOf { v -> (v - mean) * (v - mean) } / values.size)
    }

    private companion object {
        /** Five ticks a second — fast enough to see hold-steady wobble. */
        const val SAMPLE_MS = 200L
        const val MAX_SAMPLES = 240
    }
}

/** Link telemetry at radio rate. */
@Composable
fun RadioLabScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RadioLabViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            BackBar(title = stringResource(R.string.radiolab_title), onBack = onBack)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.radiolab_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )

            if (!state.connected) {
                Spacer(Modifier.height(18.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text(
                        text = stringResource(R.string.radiolab_disconnected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Tertiary,
                    )
                }
                Spacer(Modifier.height(28.dp))
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.radiolab_rssi_now),
                    value = stringResource(R.string.unit_dbm, state.rssiNow),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.radiolab_rate_now),
                    value = stringResource(R.string.unit_mbps, state.rateNow),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.radiolab_channel),
                    value = state.channelNow.takeIf { it > 0 }?.toString()
                        ?: stringResource(R.string.value_none),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.radiolab_stability),
                    value = state.rssiStdDev?.let { "±%.1f dB".format(it) }
                        ?: stringResource(R.string.value_none),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.radiolab_rssi_chart),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Sparkline(values = state.samples.map { it.rssiDbm })
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.radiolab_rate_chart),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                RateChart(
                    tx = state.samples.mapNotNull { it.txMbps },
                    rx = state.samples.mapNotNull { it.rxMbps },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.radiolab_csi_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * TX/RX negotiated rates as two lines; PHY numbers, not throughput.
 *
 * The two can diverge far on a marginal link — a router that hears you poorly
 * still shouts — which is exactly the asymmetry a single "link speed" hides.
 */
@Composable
private fun RateChart(tx: List<Int>, rx: List<Int>) {
    val txColor = Accent.Bright
    val rxColor = com.sinyal.app.ui.theme.SignalColor.Excellent
    val track = Ink.Stroke

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(color = txColor, label = stringResource(R.string.radiolab_tx))
            Legend(color = rxColor, label = stringResource(R.string.radiolab_rx))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
            val all = (tx + rx)
            val maxV = (all.maxOrNull() ?: 0).toFloat().coerceAtLeast(1f) * 1.05f
            drawLine(
                color = track,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f,
            )
            drawSeries(tx, txColor, maxV)
            drawSeries(rx, rxColor, maxV)
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Secondary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Int>,
    color: androidx.compose.ui.graphics.Color,
    maxV: Float,
) {
    if (values.size < 2) return
    val path = Path()
    values.forEachIndexed { index, v ->
        val x = index.toFloat() / (values.size - 1) * size.width
        val y = size.height - (v / maxV * size.height)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = color, style = Stroke(width = 3f))
}
