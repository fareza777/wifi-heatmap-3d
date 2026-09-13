package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.net.LanScanner
import com.sinyal.app.net.PingReport
import com.sinyal.app.net.PingTester
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many echoes one run sends. */
private val COUNTS = listOf(10, 25, 50)

class PingViewModel(app: Application) : AndroidViewModel(app) {

    private val tester = PingTester()
    private val lan = LanScanner(app)

    private val _report = MutableStateFlow(PingReport(host = ""))
    val report: StateFlow<PingReport> = _report.asStateFlow()

    private val _gateway = MutableStateFlow<String?>(null)
    val gateway: StateFlow<String?> = _gateway.asStateFlow()

    private var running: Job? = null

    init {
        viewModelScope.launch {
            _gateway.value = withContext(Dispatchers.IO) { lan.readDetails().gateway }
        }
    }

    fun start(host: String, count: Int) {
        if (running?.isActive == true) return
        running = viewModelScope.launch {
            tester.run(host, count) { progress -> _report.value = progress }
        }
    }

    fun cancel() {
        running?.cancel()
        _report.value = _report.value.copy(running = false)
    }
}

/**
 * Round-trip time to a host the user picks.
 *
 * The speed test answers "how fast is my internet"; this answers "is the thing I
 * am actually talking to responding, and steadily". Those come apart constantly
 * — a fast connection with a 300 ms game server, or a perfect router with a
 * dying uplink — and only the second one explains a laggy call.
 */
@Composable
fun PingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialHost: String? = null,
    viewModel: PingViewModel = viewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val gateway by viewModel.gateway.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var host by remember { mutableStateOf(initialHost.orEmpty()) }
    var count by remember { mutableStateOf(COUNTS.first()) }

    // The router is the useful default: it is the first hop for everything, and
    // a bad reading here rules the rest of the network in or out immediately.
    LaunchedEffect(gateway) {
        if (host.isBlank()) host = gateway.orEmpty()
    }

    fun launch() {
        keyboard?.hide()
        viewModel.start(host, count)
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
            BackBar(title = stringResource(R.string.ping_title), onBack = onBack)

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                singleLine = true,
                label = { Text(stringResource(R.string.ping_host_label)) },
                placeholder = { Text(stringResource(R.string.ping_host_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { launch() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            SuggestionRow(gateway = gateway, onPick = { host = it })

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ping_count_label),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COUNTS.forEach { option ->
                    Pill(
                        text = stringResource(R.string.ping_count_value, option),
                        selected = option == count,
                        onClick = { count = option },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(
                    if (report.running) R.string.action_cancel else R.string.ping_start,
                ),
                onClick = { if (report.running) viewModel.cancel() else launch() },
                enabled = report.running || host.isNotBlank(),
            )

            report.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (error == PingTester.ERROR_UNRESOLVED) {
                            R.string.ping_error_unresolved
                        } else {
                            R.string.ping_error_empty
                        },
                        report.host,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalColor.Dead,
                )
            }

            if (report.sent > 0) {
                Spacer(Modifier.height(16.dp))
                ResultCard(report)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.ping_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SuggestionRow(gateway: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        gateway?.let {
            Pill(
                text = stringResource(R.string.ping_suggest_router),
                selected = false,
                onClick = { onPick(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Pill(
            text = stringResource(R.string.ping_suggest_dns),
            selected = false,
            onClick = { onPick(PUBLIC_DNS) },
            modifier = Modifier.weight(1f),
        )
        Pill(
            text = stringResource(R.string.ping_suggest_site),
            selected = false,
            onClick = { onPick(PUBLIC_SITE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultCard(report: PingReport) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = report.resolvedTo?.takeIf { it != report.host }
                ?.let { stringResource(R.string.ping_resolved, report.host, it) }
                ?: report.host,
            style = MaterialTheme.typography.titleMedium,
            color = TextTone.Primary,
        )

        Spacer(Modifier.height(12.dp))
        PingChart(report)

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.ping_stat_best),
                value = report.best.asMillis(),
                valueColor = SignalColor.Excellent,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.ping_stat_average),
                value = report.average.asMillis(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.ping_stat_worst),
                value = report.worst.asMillis(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.ping_stat_deviation),
                value = report.deviation.asMillis(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.ping_stat_sent),
                value = stringResource(
                    R.string.ping_sent_value,
                    report.received,
                    report.sent,
                ),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.ping_stat_loss),
                value = stringResource(R.string.ping_loss_value, report.lossPercent),
                valueColor = if (report.lossPercent > 0) SignalColor.Dead else SignalColor.Excellent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Round trips over the run.
 *
 * A lost reply leaves a gap rather than dropping to zero: a zero would read as a
 * fast reply, which is the opposite of what happened.
 */
@Composable
private fun PingChart(report: PingReport) {
    val grid = Ink.Stroke
    val line = Accent.Base
    val lossTint = SignalColor.Dead
    val ceiling = (report.worst ?: 1.0).coerceAtLeast(1.0)

    Box(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = if (report.sent > 1) size.width / (report.sent - 1) else size.width
            fun yFor(value: Double) = size.height - (value / ceiling * size.height).toFloat()

            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                val y = size.height * fraction
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val path = Path()
            var open = false
            report.replies.forEachIndexed { index, reply ->
                val x = index * stepX
                val millis = reply.millis
                if (millis == null) {
                    open = false
                    drawLine(
                        color = lossTint,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f,
                    )
                    return@forEachIndexed
                }
                val y = yFor(millis)
                if (!open) {
                    path.moveTo(x, y)
                    open = true
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = line,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Accent.Glow else Ink.Raised)
            .border(1.dp, if (selected) Accent.Base else Ink.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Accent.Bright else TextTone.Secondary,
        )
    }
}

@Composable
private fun Double?.asMillis(): String =
    this?.let { "%.1f ms".format(it) } ?: stringResource(R.string.value_none)

private const val PUBLIC_DNS = "1.1.1.1"
private const val PUBLIC_SITE = "google.com"
