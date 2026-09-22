package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.NetworkRow
import com.sinyal.app.ui.components.SpectrumChart
import com.sinyal.app.ui.components.SpectrumLegend
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.Band
import com.sinyal.app.wifi.ChannelAnalysis
import com.sinyal.app.wifi.ChannelLoad
import com.sinyal.app.wifi.ChannelReport
import com.sinyal.app.wifi.NearbyAp
import com.sinyal.app.wifi.SecurityType
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import com.sinyal.app.wifi.WifiSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import com.sinyal.app.wifi.CurrentChannelVerdict
import com.sinyal.app.ui.components.LinkRow

data class AnalysisUiState(
    val link: WifiSnapshot = WifiSnapshot.Disconnected,
    val networks: List<NearbyAp> = emptyList(),
    val report: ChannelReport? = null,
)

class AnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        scanner.requestScan()
        val link = withContext(Dispatchers.Default) { monitor.read() }
        val networks = withContext(Dispatchers.Default) { scanner.cachedResults(link.bssid) }
        val report = withContext(Dispatchers.Default) { ChannelAnalysis.analyse(networks, link) }
        _state.update { it.copy(link = link, networks = networks, report = report) }
    }
}

/**
 * The radio environment this home sits in.
 *
 * A heatmap answers "where is my signal weak"; this screen answers the other
 * half — whether the weakness is distance at all, or a neighbour parked on the
 * same channel. Moving the router cannot fix the second one.
 */
@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    onOpenGraph: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalysisViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report = state.report

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
            BackBar(title = stringResource(R.string.analysis_title), onBack = onBack)

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.analysis_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = TextTone.Primary,
            )
            Text(
                text = stringResource(R.string.analysis_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            CurrentLinkCard(state.link, report)

            Spacer(Modifier.height(10.dp))
            LinkRow(text = stringResource(R.string.analysis_open_graph), onClick = onOpenGraph)

            if (state.networks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SurveyCard(state.networks)

                report?.verdict?.let { verdict ->
                    Spacer(Modifier.height(14.dp))
                    VerdictCard(verdict)
                }

                report?.let { current ->
                    Spacer(Modifier.height(14.dp))
                    BandCompareCard(current)

                    Spacer(Modifier.height(14.dp))
                    OccupancyCard(current)

                    Spacer(Modifier.height(14.dp))
                    QuietestCard(current)
                }

                val open = state.networks.filter { it.security.joinableWithoutPassword }
                if (open.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    OpenNetworksCard(open)
                }
            }

            if (report != null) {
                Spacer(Modifier.height(14.dp))
                BandCard(
                    networks = state.networks.filter { it.band == Band.GHZ_24 },
                    minFrequencyMhz = 2400,
                    maxFrequencyMhz = 2500,
                    channelTicks = (1..13).map { it to 2407 + it * 5 },
                    highlightChannels = listOf(1, 6, 11),
                    title = stringResource(R.string.analysis_band_24_title),
                    subtitle = stringResource(R.string.analysis_band_24_sub),
                    loads = report.band24,
                    currentChannel = report.currentChannel.takeIf {
                        report.currentBand == Band.GHZ_24
                    },
                    recommended = report.recommended24,
                )

                Spacer(Modifier.height(14.dp))
                if (report.band5.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.analysis_band_5_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTone.Tertiary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.analysis_band_5_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTone.Secondary,
                        )
                    }
                } else {
                    BandCard(
                        networks = state.networks.filter { it.band == Band.GHZ_5 },
                        minFrequencyMhz = (state.networks
                            .filter { it.band == Band.GHZ_5 }
                            .minOfOrNull { it.frequencyMhz - 60 } ?: 5150),
                        maxFrequencyMhz = (state.networks
                            .filter { it.band == Band.GHZ_5 }
                            .maxOfOrNull { it.frequencyMhz + 60 } ?: 5900),
                        channelTicks = state.networks
                            .filter { it.band == Band.GHZ_5 }
                            .map { it.channel to it.frequencyMhz }
                            .distinct()
                            .sortedBy { pair -> pair.second },
                        title = stringResource(R.string.analysis_band_5_title),
                        subtitle = stringResource(R.string.analysis_band_5_sub),
                        loads = report.band5,
                        currentChannel = report.currentChannel.takeIf {
                            report.currentBand == Band.GHZ_5
                        },
                        recommended = report.recommended5,
                    )
                }
            }

            if (state.networks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.analysis_all_networks, state.networks.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                    state.networks.take(MAX_LISTED).forEach { ap ->
                        DetailedNetworkRow(ap)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = stringResource(R.string.analysis_rescan),
                onClick = viewModel::refresh,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.analysis_throttle_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}


/**
 * What the survey found, at a glance.
 *
 * Counts come before charts because the first question is how crowded the area
 * is at all — a chart of three networks and a chart of thirty look similar until
 * the number is stated.
 */
/** Co-channel versus adjacent, which need different fixes. */
@Composable
private fun VerdictCard(verdict: CurrentChannelVerdict) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.analysis_verdict_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.analysis_verdict_channel,
                verdict.channel,
                verdict.band.label,
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = TextTone.Primary,
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.analysis_co_channel),
                value = "${verdict.coChannel}",
                valueColor = if (verdict.coChannel > 0) SignalColor.Fair else SignalColor.Excellent,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.analysis_adjacent),
                value = "${verdict.adjacent}",
                valueColor = if (verdict.adjacent > 0) SignalColor.Weak else SignalColor.Excellent,
                modifier = Modifier.weight(1f),
            )
        }

        verdict.strongestRivalDbm?.let { rival ->
            Spacer(Modifier.height(10.dp))
            StatTile(
                label = stringResource(R.string.analysis_strongest_rival),
                value = stringResource(R.string.unit_dbm, rival),
                valueColor = signalColorFor(rival),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                verdict.coChannel == 0 && verdict.adjacent == 0 ->
                    stringResource(R.string.analysis_clear_note)
                verdict.adjacent > 0 -> stringResource(R.string.analysis_adjacent_note)
                else -> stringResource(R.string.analysis_co_note)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
    }
}

/** Which band is emptier where the phone is standing. */
@Composable
private fun BandCompareCard(report: ChannelReport) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.analysis_bands_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.networks_filter_24),
                value = stringResource(R.string.analysis_band_count, report.count24),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.networks_filter_5),
                value = stringResource(R.string.analysis_band_count, report.count5),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                when {
                    report.count24 == 0 || report.count5 == 0 ->
                        R.string.analysis_band_advice_none
                    report.count5 < report.count24 -> R.string.analysis_band_advice_5
                    else -> R.string.analysis_band_advice_24
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
    }
}

/** Every occupied channel of the active band, busiest first. */
@Composable
private fun OccupancyCard(report: ChannelReport) {
    val ranked = report.rankedCurrentBand
    if (ranked.isEmpty()) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.analysis_occupancy_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))

        val peak = report.peakScore.coerceAtLeast(1e-9f)
        ranked.take(MAX_OCCUPANCY_ROWS).forEach { load ->
            OccupancyRow(
                load = load,
                fraction = (load.interferenceScore / peak).coerceIn(0f, 1f),
                isCurrent = load.channel == report.currentChannel,
            )
        }
    }
}

@Composable
private fun OccupancyRow(load: ChannelLoad, fraction: Float, isCurrent: Boolean) {
    val tint = if (isCurrent) Accent.Bright else TextTone.Secondary

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.analysis_occupancy_row, load.channel),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
            Text(
                text = stringResource(
                    R.string.analysis_occupancy_counts,
                    load.networkCount,
                    load.adjacentCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Ink.Raised),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isCurrent) Accent.Base else TextTone.Tertiary),
            )
        }
    }
}

/** The three emptiest channels the router could be moved to. */
@Composable
private fun QuietestCard(report: ChannelReport) {
    val quietest = report.quietestCurrentBand

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.analysis_quietest_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))

        if (quietest.isEmpty()) {
            Text(
                text = stringResource(R.string.analysis_quietest_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
            return@GlassCard
        }

        quietest.forEachIndexed { index, load ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.analysis_quietest_row, load.channel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index == 0) SignalColor.Excellent else TextTone.Primary,
                )
                Text(
                    text = stringResource(
                        R.string.analysis_occupancy_counts,
                        load.networkCount,
                        load.adjacentCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
        }
    }
}

/** Enough to see the shape of the band without turning into a spreadsheet. */
private const val MAX_OCCUPANCY_ROWS = 8

@Composable
private fun SurveyCard(networks: List<NearbyAp>) {
    val open = networks.count { it.security.joinableWithoutPassword }
    val on24 = networks.count { it.band == Band.GHZ_24 }
    val on5 = networks.count { it.band == Band.GHZ_5 }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.analysis_survey_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.analysis_survey_networks),
                value = "${networks.size}",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.networks_filter_24),
                value = "$on24",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.networks_filter_5),
                value = "$on5",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.analysis_survey_open),
                value = "$open",
                valueColor = if (open > 0) SignalColor.Fair else TextTone.Primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Networks that need no password.
 *
 * Two very different things land in this list and the difference is worth
 * spelling out: a plain open network carries traffic in the clear, while an
 * OWE network is passwordless yet still encrypted. The system Wi-Fi picker
 * shows them identically.
 */
@Composable
private fun OpenNetworksCard(open: List<NearbyAp>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.analysis_open_title),
            style = MaterialTheme.typography.labelSmall,
            color = SignalColor.Fair,
        )
        Spacer(Modifier.height(8.dp))
        open.sortedByDescending { it.rssiDbm }.forEach { ap ->
            DetailedNetworkRow(ap)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.analysis_open_note),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )
    }
}

/** One access point with everything the scan actually knows about it. */
@Composable
private fun DetailedNetworkRow(ap: NearbyAp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ap.ssid,
                style = MaterialTheme.typography.titleMedium,
                color = if (ap.isHidden) TextTone.Tertiary else TextTone.Primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.unit_dbm, ap.rssiDbm),
                style = MaterialTheme.typography.titleMedium,
                color = signalColorFor(ap.rssiDbm),
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(
                text = stringResource(ap.security.label),
                tint = when {
                    ap.security.isUnencrypted -> SignalColor.Weak
                    ap.security.joinableWithoutPassword -> SignalColor.Fair
                    else -> Accent.Bright
                },
            )
            Chip(
                text = stringResource(R.string.networks_band_channel, ap.band.label, ap.channel),
                tint = TextTone.Secondary,
            )
            Chip(
                text = stringResource(R.string.networks_width, ap.channelWidthMhz),
                tint = TextTone.Secondary,
            )
            if (ap.generation != com.sinyal.app.wifi.WifiGeneration.UNKNOWN) {
                Chip(text = ap.generation.label, tint = TextTone.Secondary)
            }
        }

        if (ap.isCurrent || ap.supportsRanging) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    stringResource(R.string.analysis_tag_connected).takeIf { ap.isCurrent },
                    stringResource(R.string.analysis_tag_ranging).takeIf { ap.supportsRanging },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Accent.Bright,
            )
        }
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Ink.Raised)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun CurrentLinkCard(link: WifiSnapshot, report: ChannelReport?) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = link.ssid ?: stringResource(R.string.state_not_connected),
            style = MaterialTheme.typography.headlineSmall,
            color = TextTone.Primary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.home_stat_channel),
                value = if (link.channel > 0) {
                    "${link.channel}"
                } else {
                    stringResource(R.string.value_none)
                },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_stat_band),
                value = link.band.label,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_stat_standard),
                value = link.generation.label,
                modifier = Modifier.weight(1f),
            )
        }

        if (report != null) {
            Spacer(Modifier.height(12.dp))
            val crowded = report.isCurrentCrowded
            Text(
                text = stringResource(
                    if (crowded) {
                        R.string.analysis_channel_crowded
                    } else {
                        R.string.analysis_channel_clear
                    },
                    report.currentChannel,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (crowded) SignalColor.Fair else SignalColor.Excellent,
            )
        }
    }
}

@Composable
private fun BandCard(
    networks: List<NearbyAp>,
    minFrequencyMhz: Int,
    maxFrequencyMhz: Int,
    channelTicks: List<Pair<Int, Int>>,
    highlightChannels: List<Int> = emptyList(),
    title: String,
    subtitle: String,
    loads: List<ChannelLoad>,
    currentChannel: Int?,
    recommended: Int?,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )

        Spacer(Modifier.height(16.dp))
        // The spectrum shows overlap; the bars below show which channel to pick.
        SpectrumChart(
            networks = networks,
            minFrequencyMhz = minFrequencyMhz,
            maxFrequencyMhz = maxFrequencyMhz,
            channelTicks = channelTicks,
            highlightChannels = highlightChannels,
        )

        Spacer(Modifier.height(12.dp))
        SpectrumLegend(networks)

        Spacer(Modifier.height(18.dp))
        ChannelBars(
            loads = loads,
            currentChannel = currentChannel,
            recommended = recommended,
        )

        if (recommended != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (recommended == currentChannel) {
                        R.string.analysis_channel_already_best
                    } else {
                        R.string.analysis_channel_suggest
                    },
                    recommended,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Accent.Bright,
            )
        }
    }
}

/**
 * Congestion per channel as a bar row.
 *
 * Bars are scaled against the busiest channel in the band rather than an
 * absolute figure — the question being asked is comparative, "which of these
 * should I move to", not "how many milliwatts is this".
 */
@Composable
private fun ChannelBars(
    loads: List<ChannelLoad>,
    currentChannel: Int?,
    recommended: Int?,
) {
    val peak = loads.maxOfOrNull { it.interferenceScore }?.takeIf { it > 0f } ?: 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        loads.forEach { load ->
            val fraction = (load.interferenceScore / peak).coerceIn(0.03f, 1f)
            val tint = when {
                load.channel == currentChannel -> Accent.Bright
                load.channel == recommended -> SignalColor.Excellent
                else -> Color.White.copy(alpha = 0.22f)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(tint),
                )
                Text(
                    text = "${load.channel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (load.channel == currentChannel) {
                        Accent.Bright
                    } else {
                        TextTone.Tertiary
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(Accent.Bright, stringResource(R.string.analysis_legend_yours))
        LegendDot(SignalColor.Excellent, stringResource(R.string.analysis_legend_clearest))
    }
}

@Composable
private fun LegendDot(tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(9.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(tint),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextTone.Tertiary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private const val MAX_LISTED = 30
