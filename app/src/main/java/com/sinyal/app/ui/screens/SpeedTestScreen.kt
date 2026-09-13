package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.net.SpeedPhase
import com.sinyal.app.net.SpeedResult
import com.sinyal.app.net.SpeedTester
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

class SpeedTestViewModel(app: Application) : AndroidViewModel(app) {

    private val tester = SpeedTester()
    private val monitor = WifiMonitor(app)

    private val _state = MutableStateFlow(SpeedResult())
    val state: StateFlow<SpeedResult> = _state.asStateFlow()

    private val _link = MutableStateFlow(WifiSnapshot.Disconnected)
    val link: StateFlow<WifiSnapshot> = _link.asStateFlow()

    private var running: Job? = null

    init {
        viewModelScope.launch { _link.value = monitor.read() }
    }

    fun start() {
        if (running?.isActive == true) return
        running = viewModelScope.launch {
            _link.value = monitor.read()
            tester.run { progress -> _state.value = progress }
        }
    }

    fun cancel() {
        running?.cancel()
        _state.update { it.copy(phase = SpeedPhase.IDLE, liveMbps = 0.0) }
    }
}

/**
 * Internet throughput over the current Wi-Fi link.
 *
 * The distinction stated on screen matters more than the number: this measures
 * the internet connection reached through Wi-Fi, not the Wi-Fi link itself. A
 * fast router on a slow subscription reads slow here, and the fix is not moving
 * the router.
 */
@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeedTestViewModel = viewModel(),
) {
    val result by viewModel.state.collectAsStateWithLifecycle()
    val link by viewModel.link.collectAsStateWithLifecycle()
    val running = result.phase in RUNNING_PHASES

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BackBar(title = stringResource(R.string.speed_title), onBack = onBack)

            Spacer(Modifier.height(10.dp))
            SpeedDial(
                mbps = when (result.phase) {
                    SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD -> result.liveMbps
                    else -> result.downloadMbps ?: 0.0
                },
                caption = stringResource(result.phase.label),
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.speed_download_label),
                    value = result.downloadMbps?.let { "%.1f".format(it) }
                        ?: stringResource(R.string.value_none),
                    valueColor = SignalColor.Excellent,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.speed_upload_label),
                    value = result.uploadMbps?.let { "%.1f".format(it) }
                        ?: stringResource(R.string.value_none),
                    valueColor = Accent.Bright,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.speed_latency_label),
                    value = result.latencyMs?.let { stringResource(R.string.unit_ms, it) }
                        ?: stringResource(R.string.value_none),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.speed_jitter_label),
                    value = result.jitterMs?.let { stringResource(R.string.unit_ms, it) }
                        ?: stringResource(R.string.value_none),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = stringResource(
                    if (running) R.string.action_cancel else R.string.speed_start,
                ),
                onClick = { if (running) viewModel.cancel() else viewModel.start() },
            )

            if (result.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(requireNotNull(result.error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalColor.Dead,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.speed_explainer_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.speed_explainer_body,
                        link.linkSpeedMbps,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Secondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.speed_explainer_server),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

private val RUNNING_PHASES = setOf(
    SpeedPhase.LATENCY,
    SpeedPhase.DOWNLOAD,
    SpeedPhase.UPLOAD,
)

/**
 * The live figure on a logarithmic arc.
 *
 * Linear would waste most of the dial: household connections cluster below
 * 100 Mbps while the scale has to reach far higher, so a linear needle barely
 * leaves the start for the readings that matter most.
 */
@Composable
private fun SpeedDial(mbps: Double, caption: String) {
    val clamped = mbps.coerceIn(0.0, MAX_MBPS)
    val fraction = (kotlin.math.ln(1 + clamped) / kotlin.math.ln(1 + MAX_MBPS)).toFloat()
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 140f),
        label = "speedDial",
    )
    val track = Ink.Stroke
    val fill = Accent.Base

    Box(modifier = Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(250.dp)) {
            val stroke = 18.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f - 6.dp.toPx()
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = fill,
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(mbps),
                style = MaterialTheme.typography.displayLarge,
                color = TextTone.Primary,
            )
            Text(
                text = stringResource(R.string.speed_unit),
                style = MaterialTheme.typography.labelMedium,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = Accent.Bright,
            )
        }
    }
}

private const val MAX_MBPS = 1000.0
