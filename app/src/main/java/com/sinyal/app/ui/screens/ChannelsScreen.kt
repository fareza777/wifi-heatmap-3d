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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.SpectrumChart
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.Band
import com.sinyal.app.wifi.BandAdvice
import com.sinyal.app.wifi.ChannelAdvisor
import com.sinyal.app.wifi.NearbyAp
import com.sinyal.app.wifi.RatedChannel
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

data class ChannelsUiState(
    val link: WifiSnapshot = WifiSnapshot.Disconnected,
    val networks: List<NearbyAp> = emptyList(),
    val advice: Map<Band, BandAdvice> = emptyMap(),
    val band: Band = Band.GHZ_24,
)

class ChannelsViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        scanner.requestScan()
        val link = withContext(Dispatchers.Default) { monitor.read() }
        val networks = withContext(Dispatchers.Default) { scanner.cachedResults(link.bssid) }
        val advice = withContext(Dispatchers.Default) {
            listOf(Band.GHZ_24, Band.GHZ_5, Band.GHZ_6)
                .associateWith { ChannelAdvisor.evaluate(networks, it) }
        }
        _state.update {
            it.copy(
                link = link,
                networks = networks,
                advice = advice,
                band = if (it.networks.isEmpty() && link.band != Band.UNKNOWN) link.band else it.band,
            )
        }
    }

    fun selectBand(band: Band) = _state.update { it.copy(band = band) }
}

/**
 * The channel advisor: every channel of the band scored, the best one named.
 *
 * Where the spectrum screen shows what the airwaves look like, this screen
 * answers the actionable question — which channel the router should sit on.
 */
@Composable
fun ChannelsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val advice = state.advice[state.band]

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
            BackBar(title = stringResource(R.string.channels_title), onBack = onBack)

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.channels_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = TextTone.Primary,
            )
            Text(
                text = stringResource(R.string.channels_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            BandTabs(selected = state.band, onSelect = viewModel::selectBand)

            advice?.let { current ->
                Spacer(Modifier.height(14.dp))
                RecommendationCard(current, state.link)

                Spacer(Modifier.height(14.dp))
                BandSpectrum(current, state)

                Spacer(Modifier.height(14.dp))
                RatingCard(current, state.link)
            }

            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = stringResource(R.string.analysis_rescan),
                onClick = viewModel::refresh,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.channels_score_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BandTabs(selected: Band, onSelect: (Band) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Band.GHZ_24, Band.GHZ_5, Band.GHZ_6).forEach { band ->
            val active = band == selected
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (active) Accent.Glow else Ink.Surface)
                    .border(1.dp, if (active) Accent.Base else Ink.Stroke, shape)
                    .clickable { onSelect(band) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = band.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Accent.Bright else TextTone.Secondary,
                )
            }
        }
    }
}

/** The single answer: which channel the router should move to. */
@Composable
private fun RecommendationCard(advice: BandAdvice, link: WifiSnapshot) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.channels_recommendation_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))

        val best = advice.recommended
        if (best == null || advice.count == 0) {
            Text(
                text = stringResource(R.string.channels_recommendation_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
            return@GlassCard
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.channels_best),
                value = best.channel.toString(),
                valueColor = SignalColor.Excellent,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.channels_aps_here),
                value = "${best.onChannel + best.overlapping}",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.channels_band_total),
                value = "${advice.count}",
                modifier = Modifier.weight(1f),
            )
        }

        if (link.connected && link.band == advice.band && link.channel == best.channel) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.channels_already_best),
                style = MaterialTheme.typography.bodyMedium,
                color = SignalColor.Excellent,
            )
        }

        if (advice.alternates.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.channels_alternates,
                    advice.alternates.joinToString(", ") { it.channel.toString() },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
        }
    }
}

/** The band drawn spectrum-style so ratings can be checked against the humps. */
@Composable
private fun BandSpectrum(advice: BandAdvice, state: ChannelsUiState) {
    val networks = state.networks.filter { it.band == advice.band }
    if (networks.isEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.channels_empty_band, advice.band.label),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
        }
        return
    }

    val (minFreq, maxFreq) = when (advice.band) {
        Band.GHZ_24 -> 2400 to 2500
        else -> {
            val low = networks.minOf { it.frequencyMhz - 60 }
            val high = networks.maxOf { it.frequencyMhz + 60 }
            low to high
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.channels_spectrum_title, advice.band.label),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(8.dp))
        SpectrumChart(
            networks = networks,
            minFrequencyMhz = minFreq,
            maxFrequencyMhz = maxFreq,
            channelTicks = networks
                .map { it.channel to it.frequencyMhz }
                .distinct()
                .sortedBy { it.second },
            highlightChannels = listOfNotNull(advice.recommended?.channel),
        )
    }
}

/** Every channel's score row — the rating table a user tweaks from. */
@Composable
private fun RatingCard(advice: BandAdvice, link: WifiSnapshot) {
    val occupied = advice.channels.filter { it.onChannel + it.overlapping > 0 }
    val empty = advice.channels.size - occupied.size

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.channels_rating_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))

        val rows = if (advice.band == Band.GHZ_24) advice.channels else occupied
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.channels_empty_band, advice.band.label),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
        } else {
            rows.forEach { rated ->
                RatingRow(
                    rated = rated,
                    isCurrent = link.connected && link.band == advice.band &&
                        link.channel == rated.channel,
                    isRecommended = rated.channel == advice.recommended?.channel,
                )
            }
            if (advice.band != Band.GHZ_24 && empty > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.channels_free_count, empty),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun RatingRow(rated: RatedChannel, isCurrent: Boolean, isRecommended: Boolean) {
    val tint = when {
        isCurrent -> Accent.Bright
        isRecommended -> SignalColor.Excellent
        else -> TextTone.Primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rated.channel.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.width(38.dp),
        )
        ScoreDots(score = rated.score, modifier = Modifier.weight(1f))
        if (rated.isDfs) {
            Text(
                text = stringResource(R.string.channels_dfs),
                style = MaterialTheme.typography.labelSmall,
                color = SignalColor.Fair,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = stringResource(
                R.string.channels_ap_count,
                rated.onChannel + rated.overlapping,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
    }
}

/** Ten dots standing in for a star rating — drawable-free and themeable. */
@Composable
private fun ScoreDots(score: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(10) { index ->
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        when {
                            index >= score -> Ink.Stroke
                            score >= 8 -> SignalColor.Excellent
                            score >= 5 -> SignalColor.Fair
                            else -> SignalColor.Dead
                        },
                    ),
            )
        }
    }
}
