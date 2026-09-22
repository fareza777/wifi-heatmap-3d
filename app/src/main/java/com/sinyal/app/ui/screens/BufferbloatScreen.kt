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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.net.BloatGrade
import com.sinyal.app.net.BufferbloatPhase
import com.sinyal.app.net.BufferbloatReport
import com.sinyal.app.net.BufferbloatTester
import com.sinyal.app.net.LatencySample
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BufferbloatUiState(
    val running: Boolean = false,
    val phase: BufferbloatPhase = BufferbloatPhase.IDLE,
    val live: List<LatencySample> = emptyList(),
    val report: BufferbloatReport? = null,
    val failed: Boolean = false,
)

class BufferbloatViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(BufferbloatUiState())
    val state: StateFlow<BufferbloatUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update {
                it.copy(running = true, live = emptyList(), report = null, failed = false)
            }
            val report = BufferbloatTester.measure(
                onPhase = { phase -> _state.update { s -> s.copy(phase = phase) } },
                onSample = { sample ->
                    _state.update { s -> s.copy(live = (s.live + sample).takeLast(120)) }
                },
            )
            _state.update {
                it.copy(running = false, report = report, failed = report == null)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }
}

/**
 * "Why does the game lag when someone downloads?" — measured, not guessed.
 *
 * Latency is sampled twice: once while quiet, once while parallel downloads
 * saturate the link. The gap between the two medians is what a router's
 * oversized buffers add, and it is the number a speed test will never show.
 */
@Composable
fun BufferbloatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BufferbloatViewModel = viewModel(),
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
            BackBar(title = stringResource(R.string.bloat_title), onBack = onBack)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bloat_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )

            Spacer(Modifier.height(18.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                LatencyStrip(
                    samples = state.live,
                    phase = state.phase,
                    running = state.running,
                )
            }

            state.report?.let { report ->
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(gradeColor(report.grade).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = report.grade.name,
                            style = MaterialTheme.typography.displaySmall,
                            color = gradeColor(report.grade),
                        )
                    }
                    Column {
                        Text(
                            text = BufferbloatTester.formatIncrease(report.increaseMs),
                            style = MaterialTheme.typography.displaySmall,
                            color = TextTone.Primary,
                        )
                        Text(
                            text = stringResource(R.string.bloat_increase_caption),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTone.Tertiary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.bloat_idle_median),
                        value = BufferbloatTester.formatMs(report.idleMedianMs),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.bloat_loaded_median),
                        value = BufferbloatTester.formatMs(report.loadedMedianMs),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.bloat_loaded_p95),
                        value = BufferbloatTester.formatMs(report.loadedP95Ms),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.bloat_loaded_max),
                        value = BufferbloatTester.formatMs(report.loadedMaxMs),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(
                        when (report.grade) {
                            BloatGrade.A -> R.string.bloat_verdict_a
                            BloatGrade.B -> R.string.bloat_verdict_b
                            BloatGrade.C -> R.string.bloat_verdict_c
                            BloatGrade.D -> R.string.bloat_verdict_d
                            BloatGrade.F -> R.string.bloat_verdict_f
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Secondary,
                )
            }

            if (state.failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.bloat_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalColor.Dead,
                )
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = stringResource(
                    if (state.running) R.string.action_cancel else R.string.bloat_start,
                ),
                onClick = { if (state.running) viewModel.cancel() else viewModel.start() },
            )

            Spacer(Modifier.height(14.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                Text(
                    text = stringResource(R.string.bloat_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Sample-by-sample latency, idle dots then loaded dots.
 *
 * Drawn rather than reusing the sparkline because the split between quiet and
 * saturated samples is the whole point of the chart — a single line would hide
 * the moment the load began.
 */
@Composable
private fun LatencyStrip(
    samples: List<LatencySample>,
    phase: BufferbloatPhase,
    running: Boolean,
) {
    val idleColor = Accent.Bright
    val loadedColor = SignalColor.Fair
    val track = Ink.Stroke

    Column {
        Text(
            text = stringResource(
                when {
                    !running && samples.isEmpty() -> R.string.bloat_strip_empty
                    phase == BufferbloatPhase.IDLE -> R.string.bloat_phase_idle
                    phase == BufferbloatPhase.LOAD -> R.string.bloat_phase_load
                    else -> R.string.bloat_phase_done
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val maxMs = maxOf(samples.maxOfOrNull { it.ms } ?: 100.0, 60.0) * 1.1
            // Guide line at the idle level, once any sample exists.
            val idleLevel = samples.filter { !it.loaded }
                .map { it.ms }.sorted().let { it.getOrNull(it.size / 2) }
            drawLine(
                color = track,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f,
            )
            if (idleLevel != null) {
                val y = size.height - (idleLevel / maxMs * size.height).toFloat()
                drawLine(color = track, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
            }
            val n = maxOf(samples.size, 1)
            samples.forEachIndexed { index, sample ->
                val x = (index + 0.5f) / n * size.width
                val y = size.height - (sample.ms / maxMs * size.height).toFloat()
                drawCircle(
                    color = if (sample.loaded) loadedColor else idleColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y.coerceIn(0f, size.height)),
                )
            }
        }
    }
}

private fun gradeColor(grade: BloatGrade) = when (grade) {
    BloatGrade.A -> SignalColor.Excellent
    BloatGrade.B -> SignalColor.Good
    BloatGrade.C -> SignalColor.Fair
    BloatGrade.D -> SignalColor.Weak
    BloatGrade.F -> SignalColor.Dead
}
