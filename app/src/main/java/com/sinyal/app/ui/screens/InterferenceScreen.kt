package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.EnvironmentComparison
import com.sinyal.app.wifi.EnvironmentDiff
import com.sinyal.app.wifi.EnvironmentSnapshot
import com.sinyal.app.wifi.EnvironmentVerdict
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InterferenceUiState(
    val baseline: EnvironmentSnapshot? = null,
    val latest: EnvironmentSnapshot? = null,
    val comparison: EnvironmentComparison? = null,
    val scanning: Boolean = false,
    val baselineApCount: Int = 0,
)

/**
 * A saved moment of the airspace, compared with now.
 *
 * Baseline stays only in memory — this tool answers "did the interference
 * change since a moment ago?" (router moved, channel changed, neighbour
 * appeared), not "what was it like last month".
 */
class InterferenceViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)
    private val monitor = WifiMonitor(app)

    private val _state = MutableStateFlow(InterferenceUiState())
    val state: StateFlow<InterferenceUiState> = _state.asStateFlow()

    private var job: Job? = null

    private fun snapshotNow(): EnvironmentSnapshot {
        scanner.requestScan()
        return EnvironmentSnapshot(
            atMs = System.currentTimeMillis(),
            aps = scanner.cachedResults(monitor.read().bssid),
        )
    }

    /** First button: remembers the airspace as it is now. */
    fun takeBaseline() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            // Cached results lag a requested scan by a second or two; wait for
            // the sweep before reading so the baseline is genuinely "now".
            scanner.requestScan()
            delay(SCAN_SETTLE_MS)
            val snapshot = snapshotNow()
            _state.update {
                it.copy(
                    baseline = snapshot,
                    latest = null,
                    comparison = null,
                    scanning = false,
                    baselineApCount = snapshot.aps.size,
                )
            }
        }
    }

    /** Second button: re-reads the airspace and diffs against the baseline. */
    fun compareNow() {
        if (job?.isActive == true || _state.value.baseline == null) return
        job = viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            scanner.requestScan()
            delay(SCAN_SETTLE_MS)
            val latest = snapshotNow()
            _state.update {
                it.copy(
                    latest = latest,
                    comparison = EnvironmentDiff.diff(it.baseline!!, latest),
                    scanning = false,
                )
            }
        }
    }

    fun reset() {
        _state.value = InterferenceUiState()
    }

    private companion object {
        const val SCAN_SETTLE_MS = 2_500L
    }
}

/**
 * Before/after for the radio environment, not for saved scans — Compare
 * already owns those. This is the "did the neighbour's new router just eat
 * channel 6?" tool.
 */
@Composable
fun InterferenceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InterferenceViewModel = viewModel(),
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
            BackBar(title = stringResource(R.string.interference_title), onBack = onBack)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.interference_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )

            Spacer(Modifier.height(18.dp))
            if (state.baseline == null) {
                GradientButton(
                    text = stringResource(R.string.interference_take_baseline),
                    onClick = viewModel::takeBaseline,
                    enabled = !state.scanning,
                )
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.interference_baseline_saved,
                                    state.baselineApCount,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextTone.Primary,
                            )
                            Text(
                                text = stringResource(R.string.interference_baseline_when),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTone.Tertiary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(
                        text = stringResource(
                            if (state.scanning) {
                                R.string.interference_scanning
                            } else {
                                R.string.interference_compare_now
                            },
                        ),
                        onClick = viewModel::compareNow,
                        enabled = !state.scanning,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.interference_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent.Bright,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable(onClick = viewModel::reset)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            state.comparison?.let { comparison ->
                Spacer(Modifier.height(16.dp))
                VerdictCard(comparison)

                if (comparison.channelShifts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.interference_channels_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    comparison.channelShifts.take(6).forEach { shift ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 12.dp,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${shift.band.label} · ch ${shift.channel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextTone.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatDb(shift.deltaDb),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (shift.deltaDb > 0) {
                                        SignalColor.Dead
                                    } else {
                                        SignalColor.Excellent
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (comparison.newAps.isNotEmpty() || comparison.departedAps.isNotEmpty() ||
                    comparison.louderAps.isNotEmpty()
                ) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.interference_aps_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    comparison.newAps.take(4).forEach { ap ->
                        ApShiftRow(
                            ssid = ap.ssid,
                            detail = stringResource(
                                R.string.interference_new_ap,
                                ap.afterDbm ?: 0,
                            ),
                            tone = SignalColor.Dead,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    comparison.louderAps.take(4).forEach { ap ->
                        ApShiftRow(
                            ssid = ap.ssid,
                            detail = stringResource(
                                R.string.interference_louder_ap,
                                ap.beforeDbm ?: 0,
                                ap.afterDbm ?: 0,
                            ),
                            tone = SignalColor.Fair,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    comparison.departedAps.take(4).forEach { ap ->
                        ApShiftRow(
                            ssid = ap.ssid,
                            detail = stringResource(
                                R.string.interference_gone_ap,
                                ap.beforeDbm ?: 0,
                            ),
                            tone = SignalColor.Excellent,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                Text(
                    text = stringResource(R.string.interference_howto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun VerdictCard(comparison: EnvironmentComparison) {
    val (tone, verdictRes) = when (comparison.verdict) {
        EnvironmentVerdict.BUSIER -> SignalColor.Dead to R.string.interference_verdict_busier
        EnvironmentVerdict.QUIETER -> SignalColor.Excellent to R.string.interference_verdict_quieter
        EnvironmentVerdict.SAME -> Accent.Bright to R.string.interference_verdict_same
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = stringResource(verdictRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = tone,
                )
                Text(
                    text = stringResource(
                        R.string.interference_power_delta,
                        formatDb(comparison.powerDeltaDb),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Secondary,
                )
            }
        }
    }
}

@Composable
private fun ApShiftRow(ssid: String, detail: String, tone: androidx.compose.ui.graphics.Color) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
            Text(
                text = ssid,
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = TextTone.Secondary,
            )
        }
    }
}

/** "+8.0 dB" / "−8.0 dB" — signed deltas read wrong without the sign. */
private fun formatDb(db: Double): String = when {
    db == Double.POSITIVE_INFINITY -> "+∞ dB"
    db == Double.NEGATIVE_INFINITY -> "−∞ dB"
    db >= 0 -> "+%.1f dB".format(db)
    else -> "%.1f dB".format(db)
}
