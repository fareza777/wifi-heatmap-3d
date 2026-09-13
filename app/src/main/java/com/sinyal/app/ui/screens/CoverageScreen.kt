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
import com.sinyal.app.ar.ApIdentity
import com.sinyal.app.ar.ApSurvey
import com.sinyal.app.ar.RoamEvent
import com.sinyal.app.data.ScanStore
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.WifiSignalIcon
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.RoamVerdict
import com.sinyal.app.wifi.RoamingAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoverageUiState(
    val survey: ApSurvey = ApSurvey(),
    val transmitters: List<ApIdentity> = emptyList(),
    val verdicts: List<RoamVerdict> = emptyList(),
    val connectedBssid: String? = null,
    val loaded: Boolean = false,
)

class CoverageViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(CoverageUiState())
    val state: StateFlow<CoverageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val scan = ScanStore.latest
            val survey = scan?.apSurvey ?: ApSurvey()
            _state.value = CoverageUiState(
                survey = survey,
                transmitters = survey.transmitters(),
                verdicts = RoamingAnalysis.run(survey),
                connectedBssid = null,
                loaded = true,
            )
        }
    }
}

/**
 * Which transmitter owns which part of the home, and how well the phone moved
 * between them.
 *
 * A single-AP heatmap cannot answer either question. With a mesh or a repeater,
 * the useful fact is not "signal is weak here" but "you are still talking to the
 * node in the front room from the back bedroom" — and that only becomes visible
 * once every transmitter is recorded, not just the one in use.
 */
@Composable
fun CoverageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoverageViewModel = viewModel(),
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
            BackBar(title = stringResource(R.string.coverage_title), onBack = onBack)

            if (state.loaded && state.survey.isEmpty) {
                Spacer(Modifier.height(18.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.coverage_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextTone.Tertiary,
                    )
                }
                Spacer(Modifier.height(28.dp))
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.coverage_transmitters_title),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(8.dp))
            state.transmitters.forEach { ap ->
                TransmitterRow(ap)
                Spacer(Modifier.height(8.dp))
            }

            if (state.verdicts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.coverage_roaming_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                state.verdicts.forEach { verdict ->
                    RoamRow(verdict)
                    Spacer(Modifier.height(8.dp))
                }
            } else if (state.transmitters.size > 1) {
                Spacer(Modifier.height(16.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text(
                        text = stringResource(R.string.coverage_no_roam),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Secondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.coverage_sampling_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TransmitterRow(ap: ApIdentity) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WifiSignalIcon(rssiDbm = ap.strongestDbm, size = 28.dp)
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = ap.ssid.ifBlank { stringResource(R.string.ssid_hidden) },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ap.bssid,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.unit_dbm, ap.strongestDbm),
                    style = MaterialTheme.typography.titleMedium,
                    color = signalColorFor(ap.strongestDbm),
                )
                Text(
                    text = stringResource(R.string.coverage_samples, ap.sampleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun RoamRow(verdict: RoamVerdict) {
    val tint = if (verdict.wasLate) SignalColor.Fair else SignalColor.Excellent

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Text(
                text = stringResource(
                    R.string.coverage_roam_line,
                    verdict.event.leavingDbm ?: 0,
                    verdict.event.joiningDbm,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Primary,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (verdict.wasLate) {
                    R.string.coverage_roam_late
                } else {
                    R.string.coverage_roam_fine
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (verdict.wasLate) TextTone.Secondary else TextTone.Tertiary,
        )
    }
}
