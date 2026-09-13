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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.data.CompletedScan
import com.sinyal.app.data.ScanRepository
import com.sinyal.app.data.ScanSummary
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.signalColorAt
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.SignalQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

data class CompareUiState(
    val available: List<ScanSummary> = emptyList(),
    val before: CompletedScan? = null,
    val after: CompletedScan? = null,
    val picking: Slot = Slot.BEFORE,
)

enum class Slot { BEFORE, AFTER }

class CompareViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScanRepository(app)

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val scans = withContext(Dispatchers.IO) { repository.list() }
            _state.update { it.copy(available = scans) }

            // Two most recent scans are the overwhelmingly common comparison,
            // so pre-fill them rather than opening on an empty screen.
            if (scans.size >= 2) {
                val after = withContext(Dispatchers.IO) { repository.load(scans[0].id) }
                val before = withContext(Dispatchers.IO) { repository.load(scans[1].id) }
                _state.update { it.copy(before = before, after = after) }
            }
        }
    }

    fun choosePicking(slot: Slot) {
        _state.update { it.copy(picking = slot) }
    }

    fun select(id: String) = viewModelScope.launch {
        val scan = withContext(Dispatchers.IO) { repository.load(id) } ?: return@launch
        _state.update {
            if (it.picking == Slot.BEFORE) it.copy(before = scan) else it.copy(after = scan)
        }
    }
}

/**
 * Two scans side by side.
 *
 * The point of moving a router is that the weak parts of the home get better,
 * and nothing else in the app could ever show whether that actually happened.
 * The comparison is deliberately weighted to the weak end: an average that
 * improves while the dead corner stays dead is not a success.
 */
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = viewModel(),
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
            BackBar(title = stringResource(R.string.compare_title), onBack = onBack)

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.compare_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = TextTone.Primary,
            )
            Text(
                text = stringResource(R.string.compare_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.available.size < 2) {
                Spacer(Modifier.height(18.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.compare_need_two),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextTone.Tertiary,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                return@Column
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SlotButton(
                    label = stringResource(R.string.compare_before),
                    scan = state.before,
                    selected = state.picking == Slot.BEFORE,
                    onClick = { viewModel.choosePicking(Slot.BEFORE) },
                    modifier = Modifier.weight(1f),
                )
                SlotButton(
                    label = stringResource(R.string.compare_after),
                    scan = state.after,
                    selected = state.picking == Slot.AFTER,
                    onClick = { viewModel.choosePicking(Slot.AFTER) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.compare_pick_for,
                        stringResource(
                            if (state.picking == Slot.BEFORE) {
                                R.string.compare_before_upper
                            } else {
                                R.string.compare_after_upper
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent.Bright,
                )
                Spacer(Modifier.height(4.dp))
                state.available.take(MAX_PICKABLE).forEach { summary ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.select(summary.id) }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.compare_row_label,
                                summary.ssid ?: stringResource(R.string.value_none),
                                dateOf(summary.savedAtMs),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTone.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = summary.weakestDbm
                                ?.let { stringResource(R.string.unit_dbm, it) }
                                ?: stringResource(R.string.value_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = summary.weakestDbm?.let { signalColorFor(it) }
                                ?: TextTone.Tertiary,
                        )
                    }
                }
            }

            val before = state.before
            val after = state.after
            if (before != null && after != null) {
                Spacer(Modifier.height(14.dp))
                VerdictCard(before, after)

                Spacer(Modifier.height(14.dp))
                DistributionCard(before, after)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SlotButton(
    label: String,
    scan: CompletedScan?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Accent.Glow else Ink.Surface)
            .border(1.dp, if (selected) Accent.Base else Ink.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Accent.Bright else TextTone.Tertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = scan?.let { dateOf(it.savedAtMs) }
                ?: stringResource(R.string.compare_unpicked),
            style = MaterialTheme.typography.titleMedium,
            color = TextTone.Primary,
        )
        Text(
            text = scan?.grid?.weakestRssi
                ?.let { stringResource(R.string.compare_weakest_value, it) }
                ?: stringResource(R.string.value_none),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )
    }
}

/** The one number that decides whether the move was worth it. */
@Composable
private fun VerdictCard(before: CompletedScan, after: CompletedScan) {
    val weakBefore = before.grid.weakestRssi ?: 0
    val weakAfter = after.grid.weakestRssi ?: 0
    val delta = weakAfter - weakBefore

    val meanBefore = meanDbm(before)
    val meanAfter = meanDbm(after)
    val meanDelta = meanAfter - meanBefore

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.compare_result_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                delta >= MEANINGFUL_DB ->
                    stringResource(R.string.compare_result_better, delta)
                delta <= -MEANINGFUL_DB ->
                    stringResource(R.string.compare_result_worse, -delta)
                else ->
                    stringResource(R.string.compare_result_same, signed(delta))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                delta >= 3 -> SignalColor.Excellent
                delta <= -3 -> SignalColor.Weak
                else -> TextTone.Primary
            },
        )

        Spacer(Modifier.height(14.dp))
        DeltaRow(stringResource(R.string.compare_row_weakest), weakBefore, weakAfter)
        DeltaRow(stringResource(R.string.compare_row_mean), meanBefore, meanAfter)
        DeltaRow(
            stringResource(R.string.compare_row_strongest),
            before.grid.strongestRssi ?: 0,
            after.grid.strongestRssi ?: 0,
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.compare_mean_note, signed(meanDelta)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )
    }
}

@Composable
private fun DeltaRow(label: String, before: Int, after: Int) {
    val delta = after - before
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.compare_delta_pair, before, after),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Primary,
            )
            Text(
                text = "  ${signed(delta)}",
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    delta >= 2 -> SignalColor.Excellent
                    delta <= -2 -> SignalColor.Weak
                    else -> TextTone.Tertiary
                },
            )
        }
    }
}

/**
 * How the readings are spread, before against after.
 *
 * A single number hides the shape of the change: a router move can lift the
 * middle of the home while flattening a corner, and only a distribution shows
 * that.
 */
@Composable
private fun DistributionCard(before: CompletedScan, after: CompletedScan) {
    val beforeBins = histogram(before)
    val afterBins = histogram(after)
    val peak = (beforeBins + afterBins).maxOrNull()?.coerceAtLeast(1) ?: 1
    val trackColor = Ink.Stroke

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.compare_spread_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val slot = size.width / BIN_COUNT
            val barWidth = slot * 0.36f

            repeat(BIN_COUNT) { index ->
                val tint = signalColorAt(index / (BIN_COUNT - 1f))
                val left = index * slot

                val hBefore = size.height * (beforeBins[index] / peak.toFloat())
                drawRect(
                    color = trackColor,
                    topLeft = Offset(left + slot * 0.12f, size.height - hBefore),
                    size = Size(barWidth, hBefore),
                )

                val hAfter = size.height * (afterBins[index] / peak.toFloat())
                drawRect(
                    color = tint,
                    topLeft = Offset(left + slot * 0.52f, size.height - hAfter),
                    size = Size(barWidth, hAfter),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.compare_weak),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
            Text(
                text = stringResource(R.string.compare_spread_legend),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.compare_strong),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
        }
    }
}

private const val BIN_COUNT = 10
private const val MAX_PICKABLE = 6

/** Counts cells per strength band, normalised so different scan sizes compare. */
private fun histogram(scan: CompletedScan): List<Int> {
    val bins = IntArray(BIN_COUNT)
    scan.grid.occupiedCells.forEach { cell ->
        val index = (SignalQuality.normalize(cell.rssiDbm) * (BIN_COUNT - 1)).toInt()
        bins[index.coerceIn(0, BIN_COUNT - 1)]++
    }
    val total = bins.sum().coerceAtLeast(1)
    return bins.map { it * 100 / total }
}

/** Mean in linear power, then back to dBm — averaging decibels is meaningless. */
private fun meanDbm(scan: CompletedScan): Int {
    val cells = scan.grid.occupiedCells
    if (cells.isEmpty()) return 0
    val power = cells.sumOf { Math.pow(10.0, it.rssiDbm / 10.0) } / cells.size
    return (10.0 * Math.log10(power)).toInt()
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else "$value"

/**
 * Smallest change worth calling a change.
 *
 * Wi-Fi readings wander a couple of dB on their own, so anything under this is
 * indistinguishable from the measurement wandering.
 */
private const val MEANINGFUL_DB = 3

private fun dateOf(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
