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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.export.NetworkReportWriter
import com.sinyal.app.export.ShareHelper
import com.sinyal.app.net.OuiLookup
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.Band
import com.sinyal.app.wifi.InternetStatus
import com.sinyal.app.wifi.InternetStatusReader
import com.sinyal.app.wifi.NearbyAp
import com.sinyal.app.wifi.SignalQuality
import com.sinyal.app.wifi.WifiGeneration
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import com.sinyal.app.wifi.WifiSnapshot
import com.sinyal.app.wifi.estimatedDistanceMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import com.sinyal.app.ui.components.WifiSignalIcon
import com.sinyal.app.wifi.NetworkJoiner

/** Which slice of the survey to show. */
enum class NetworkFilter(@StringRes val label: Int) {
    ALL(R.string.networks_filter_all),
    OPEN(R.string.networks_filter_open),
    BAND_24(R.string.networks_filter_24),
    BAND_5(R.string.networks_filter_5),
    SECURED(R.string.networks_filter_secured),
}

data class NetworksUiState(
    val link: WifiSnapshot = WifiSnapshot.Disconnected,
    val internet: InternetStatus = InternetStatus.Unknown,
    val networks: List<NearbyAp> = emptyList(),
    /** BSSID to the company that registered its MAC prefix. */
    val vendors: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
)

class NetworksViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val scanner = WifiScanner(app)
    private val internetReader = InternetStatusReader(app)
    private val oui = OuiLookup(app)
    val joiner = NetworkJoiner(app)

    private val _state = MutableStateFlow(NetworksUiState())
    val state: StateFlow<NetworksUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        scanner.requestScan()
        val link = withContext(Dispatchers.Default) { monitor.read() }
        val networks = withContext(Dispatchers.Default) { scanner.cachedResults(link.bssid) }
        val internet = withContext(Dispatchers.Default) { internetReader.read() }
        _state.update {
            it.copy(link = link, internet = internet, networks = networks, loading = false)
        }

        // Resolved after the list is already on screen: the table is 40 000 rows
        // and the names are an enrichment, not something worth waiting for.
        val vendors = networks.mapNotNull { ap ->
            oui.vendorOf(ap.bssid)?.let { ap.bssid to it }
        }.toMap()
        _state.update { it.copy(vendors = vendors) }
    }
}

/**
 * Every network in range, and what it would take to use each one.
 *
 * Separate from the spectrum screen on purpose: that one answers "why is my
 * connection poor", this one answers "what else could I connect to" — a
 * different question, asked at a different moment.
 */
@Composable
fun NetworksScreen(
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworksViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var query by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<NearbyAp?>(null) }
    var filter by remember { mutableStateOf(NetworkFilter.ALL) }

    val visible = state.networks
        .filter { ap -> query.isBlank() || ap.ssid.contains(query, ignoreCase = true) }
        .filter { ap ->
            when (filter) {
                NetworkFilter.ALL -> true
                NetworkFilter.OPEN -> ap.security.joinableWithoutPassword
                NetworkFilter.BAND_24 -> ap.band == Band.GHZ_24
                NetworkFilter.BAND_5 -> ap.band == Band.GHZ_5
                NetworkFilter.SECURED -> !ap.security.joinableWithoutPassword
            }
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
            BackBar(title = stringResource(R.string.networks_title), onBack = onBack)

            Spacer(Modifier.height(18.dp))
            ConnectedCard(state.link, state.internet)

            Spacer(Modifier.height(10.dp))
            LinkRow(
                text = stringResource(R.string.networks_open_details),
                onClick = onOpenDetails,
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.networks_search)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            FilterRow(current = filter, onChange = { filter = it })

            Spacer(Modifier.height(14.dp))
            if (visible.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            if (state.networks.isEmpty()) {
                                R.string.networks_empty_no_scan
                            } else {
                                R.string.networks_empty_filtered
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextTone.Tertiary,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.networks_count, visible.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Text(
                        text = stringResource(R.string.networks_tap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent.Bright,
                    )
                }
                Spacer(Modifier.height(8.dp))
                visible.forEach { ap ->
                    NetworkCard(
                        ap = ap,
                        vendor = state.vendors[ap.bssid],
                        onClick = { opened = ap },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = stringResource(
                    if (state.loading) R.string.networks_scanning else R.string.networks_rescan,
                ),
                onClick = viewModel::refresh,
                enabled = !state.loading,
            )

            if (state.networks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ExportButton {
                    ShareHelper.shareDocument(
                        context = context,
                        text = NetworkReportWriter.write(
                            res = resources,
                            link = state.link,
                            internet = state.internet,
                            networks = state.networks,
                            vendors = state.vendors,
                        ),
                        fileName = resources.getString(R.string.networks_report_filename),
                        caption = resources.getString(R.string.networks_report_caption),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        opened?.let { ap ->
            NetworkSheet(
                ap = ap,
                vendor = state.vendors[ap.bssid],
                route = viewModel.joiner.routeFor(ap),
                onJoin = {
                    viewModel.joiner.join(ap)
                    opened = null
                },
                onOpenPicker = {
                    viewModel.joiner.openPicker()
                    opened = null
                },
                onDismiss = { opened = null },
            )
        }
    }
}

@Composable
private fun ConnectedCard(link: WifiSnapshot, internet: InternetStatus) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.networks_connected_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = link.ssid ?: stringResource(R.string.state_not_connected),
            style = MaterialTheme.typography.headlineSmall,
            color = TextTone.Primary,
        )

        if (link.connected) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(internet.summary),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    internet.needsSignIn -> SignalColor.Fair
                    internet.isValidated -> SignalColor.Excellent
                    else -> TextTone.Secondary
                },
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.networks_stat_signal),
                    value = stringResource(R.string.unit_dbm, link.rssiDbm),
                    valueColor = signalColorFor(link.rssiDbm),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.networks_stat_link),
                    value = stringResource(R.string.unit_mbps, link.linkSpeedMbps),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.networks_stat_estimate),
                    value = if (internet.downstreamKbps > 0) {
                        stringResource(R.string.unit_mbps, internet.downstreamKbps / 1000)
                    } else {
                        stringResource(R.string.value_none)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterRow(current: NetworkFilter, onChange: (NetworkFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NetworkFilter.entries.forEach { option ->
            val selected = option == current
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) Accent.Glow else Ink.Raised)
                    .border(1.dp, if (selected) Accent.Base else Ink.Stroke, shape)
                    .clickable { onChange(option) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = stringResource(option.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Accent.Bright else TextTone.Secondary,
                )
            }
        }
    }
}

/**
 * One network, with everything a scan can tell and nothing it cannot.
 *
 * Whether a given open network will present a login page is deliberately framed
 * as a possibility rather than a fact: Android reports captive portals only for
 * the network already joined, so any claim about one merely seen in a scan would
 * be invention.
 */
@Composable
private fun NetworkCard(ap: NearbyAp, vendor: String?, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WifiSignalIcon(
                rssiDbm = ap.rssiDbm,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ap.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ap.isHidden) TextTone.Tertiary else TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = vendor?.let {
                        stringResource(R.string.networks_vendor_line, it, ap.bssid)
                    } ?: ap.bssid,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.unit_dbm, ap.rssiDbm),
                    style = MaterialTheme.typography.titleMedium,
                    color = signalColorFor(ap.rssiDbm),
                )
                Text(
                    text = stringResource(ap.quality.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(
                text = stringResource(ap.security.label),
                tint = when {
                    ap.security.isUnencrypted -> SignalColor.Weak
                    ap.security.joinableWithoutPassword -> SignalColor.Fair
                    else -> Accent.Bright
                },
            )
            Badge(
                text = stringResource(R.string.networks_band_channel, ap.band.label, ap.channel),
                tint = TextTone.Secondary,
            )
            Badge(
                text = stringResource(R.string.networks_width, ap.channelWidthMhz),
                tint = TextTone.Secondary,
            )
            if (ap.generation != WifiGeneration.UNKNOWN) {
                Badge(text = ap.generation.label, tint = TextTone.Secondary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                R.string.networks_distance,
                estimatedDistanceMeters(ap.rssiDbm),
                (SignalQuality.normalize(ap.rssiDbm) * 100).toInt(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )

        if (ap.security.joinableWithoutPassword) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (ap.security.isUnencrypted) {
                        R.string.networks_open_unencrypted
                    } else {
                        R.string.networks_open_encrypted
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = SignalColor.Fair,
            )
        }

        if (ap.supportsRanging) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.networks_ranging),
                style = MaterialTheme.typography.labelSmall,
                color = Accent.Bright,
            )
        }
    }
}

/** A quiet row that opens a deeper screen without competing with the survey. */
@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Raised)
            .border(1.dp, Ink.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Accent.Bright,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = Accent.Bright,
        )
    }
}

/** Writes the survey out as a forwardable text file. */
@Composable
private fun ExportButton(onClick: () -> Unit) {
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
            text = stringResource(R.string.networks_export),
            style = MaterialTheme.typography.labelLarge,
            color = TextTone.Secondary,
        )
    }
}

@Composable
private fun Badge(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Ink.Raised)
            .border(1.dp, Ink.Stroke, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
