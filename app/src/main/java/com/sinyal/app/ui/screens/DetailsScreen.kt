package com.sinyal.app.ui.screens

import android.app.Application
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.net.ConnectionDetails
import com.sinyal.app.net.LanScanner
import com.sinyal.app.net.OuiLookup
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.AdapterCapabilities
import com.sinyal.app.wifi.Capability
import com.sinyal.app.wifi.InternetStatus
import com.sinyal.app.wifi.InternetStatusReader
import com.sinyal.app.wifi.LinkAddressing
import com.sinyal.app.wifi.LinkInspector
import com.sinyal.app.wifi.LinkRates
import com.sinyal.app.wifi.NearbyAp
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
import com.sinyal.app.wifi.CapabilityAudit
import com.sinyal.app.wifi.Mismatch

data class DetailsUiState(
    val link: WifiSnapshot = WifiSnapshot.Disconnected,
    val internet: InternetStatus = InternetStatus.Unknown,
    val connection: ConnectionDetails = ConnectionDetails.Empty,
    val addressing: LinkAddressing? = null,
    val rates: LinkRates? = null,
    val capabilities: List<Capability> = emptyList(),
    /** The scan entry for the connected AP, which carries width and security. */
    val currentAp: NearbyAp? = null,
    val vendor: String? = null,
    val mismatches: List<Mismatch> = emptyList(),
    val loading: Boolean = true,
)

class DetailsViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val scanner = WifiScanner(app)
    private val lan = LanScanner(app)
    private val inspector = LinkInspector(app)
    private val adapter = AdapterCapabilities(app)
    private val internetReader = InternetStatusReader(app)
    private val oui = OuiLookup(app)

    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }

        val link = withContext(Dispatchers.Default) { monitor.read() }
        val readings = withContext(Dispatchers.IO) {
            DetailsUiState(
                link = link,
                internet = internetReader.read(),
                connection = lan.readDetails(),
                addressing = inspector.addressing(),
                rates = inspector.rates(),
                capabilities = adapter.read(),
                currentAp = scanner.cachedResults(link.bssid).firstOrNull { it.isCurrent },
                loading = false,
            )
        }

        val neighbours = withContext(Dispatchers.Default) {
            scanner.cachedResults(link.bssid)
        }
        _state.value = readings.copy(
            mismatches = CapabilityAudit.run(
                connected = readings.currentAp,
                neighbours = neighbours,
                capabilities = readings.capabilities,
                rates = readings.rates,
            ),
        )

        val vendor = oui.vendorOf(link.bssid)
        _state.update { it.copy(vendor = vendor) }
    }
}

/**
 * Everything the platform will say about this connection and this radio.
 *
 * A reference page rather than a dashboard: nothing here is interpreted, it is
 * the raw answer to "what exactly am I connected to", for the moments when
 * someone is reading numbers out to a support line or comparing two phones.
 *
 * Anything the platform will not answer is shown as unknown with the reason,
 * never as a plausible-looking guess.
 */
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dash = stringResource(R.string.value_none)

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
            BackBar(title = stringResource(R.string.details_title), onBack = onBack)

            Spacer(Modifier.height(16.dp))
            NetworkDetailsCard(state, dash)

            Spacer(Modifier.height(12.dp))
            AddressingCard(state, dash)

            if (state.link.connected) {
                Spacer(Modifier.height(12.dp))
                MismatchCard(state.mismatches)
            }

            Spacer(Modifier.height(12.dp))
            AdapterCard(state.capabilities)

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(
                    if (state.loading) R.string.details_reading else R.string.details_refresh,
                ),
                onClick = viewModel::refresh,
                enabled = !state.loading,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun NetworkDetailsCard(state: DetailsUiState, dash: String) {
    val link = state.link
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.details_network_title))

        if (!link.connected) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.state_not_connected),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Tertiary,
            )
            return@GlassCard
        }

        Spacer(Modifier.height(10.dp))
        DetailLine(stringResource(R.string.details_ssid), link.ssid ?: dash)
        DetailLine(stringResource(R.string.details_bssid), link.bssid ?: dash)
        state.vendor?.let { DetailLine(stringResource(R.string.details_vendor), it) }
        DetailLine(
            label = stringResource(R.string.details_signal),
            value = stringResource(R.string.unit_dbm, link.rssiDbm),
            valueColor = signalColorFor(link.rssiDbm),
        )
        DetailLine(
            stringResource(R.string.details_quality),
            stringResource(link.quality.label),
        )
        DetailLine(stringResource(R.string.details_frequency), "${link.frequencyMhz} MHz")
        DetailLine(stringResource(R.string.details_band), link.band.label)
        DetailLine(stringResource(R.string.details_channel), "${link.channel}")
        state.currentAp?.let { ap ->
            DetailLine(
                stringResource(R.string.details_width),
                stringResource(R.string.networks_width, ap.channelWidthMhz),
            )
            DetailLine(
                stringResource(R.string.details_security),
                stringResource(ap.security.label),
            )
        }
        DetailLine(stringResource(R.string.details_standard), link.generation.label)

        val rates = state.rates
        DetailLine(
            stringResource(R.string.details_tx),
            rates?.txMbps?.let { stringResource(R.string.unit_mbps, it) }
                ?: stringResource(R.string.unit_mbps, link.linkSpeedMbps),
        )
        rates?.rxMbps?.let {
            DetailLine(
                stringResource(R.string.details_rx),
                stringResource(R.string.unit_mbps, it),
            )
        }
        rates?.maxSupportedTxMbps?.let {
            DetailLine(
                stringResource(R.string.details_max_tx),
                stringResource(R.string.unit_mbps, it),
            )
        }
        rates?.maxSupportedRxMbps?.let {
            DetailLine(
                stringResource(R.string.details_max_rx),
                stringResource(R.string.unit_mbps, it),
            )
        }

        DetailLine(
            label = stringResource(R.string.details_internet),
            value = stringResource(state.internet.summary),
            valueColor = when {
                state.internet.needsSignIn -> SignalColor.Fair
                state.internet.isValidated -> SignalColor.Excellent
                else -> TextTone.Primary
            },
        )
    }
}

@Composable
private fun AddressingCard(state: DetailsUiState, dash: String) {
    val connection = state.connection
    val addressing = state.addressing

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.details_addressing_title))
        Spacer(Modifier.height(10.dp))

        DetailLine(stringResource(R.string.devices_ip), connection.ipAddress ?: dash)
        DetailLine(
            stringResource(R.string.details_subnet_mask),
            connection.subnetMask ?: addressing?.subnetMask ?: dash,
        )
        DetailLine(stringResource(R.string.devices_gateway), connection.gateway ?: dash)
        DetailLine(
            stringResource(R.string.details_prefix),
            connection.prefixLength?.let { "/$it" } ?: dash,
        )
        DetailLine(
            stringResource(R.string.details_dns1),
            connection.dnsServers.getOrNull(0) ?: addressing?.dnsPrimary ?: dash,
        )
        DetailLine(
            stringResource(R.string.details_dns2),
            connection.dnsServers.getOrNull(1) ?: addressing?.dnsSecondary ?: dash,
        )
        DetailLine(stringResource(R.string.details_dhcp_server), addressing?.dhcpServer ?: dash)
        DetailLine(
            stringResource(R.string.details_lease),
            addressing?.leaseSeconds?.let {
                stringResource(R.string.details_lease_value, it / 3600, (it % 3600) / 60)
            } ?: dash,
        )
        DetailLine(stringResource(R.string.details_interface), connection.interfaceName ?: dash)
        connection.domain?.takeIf { it.isNotBlank() }?.let {
            DetailLine(stringResource(R.string.details_domain), it)
        }
    }
}

/** The gap between what the hardware can do and what it is doing. */
@Composable
private fun MismatchCard(mismatches: List<Mismatch>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.audit_section_title))
        Spacer(Modifier.height(10.dp))

        if (mismatches.isEmpty()) {
            Text(
                text = stringResource(R.string.audit_all_good),
                style = MaterialTheme.typography.bodyMedium,
                color = SignalColor.Excellent,
            )
            return@GlassCard
        }

        mismatches.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(item.title),
                style = MaterialTheme.typography.titleMedium,
                color = SignalColor.Fair,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(item.detail, *item.args.toTypedArray()),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
        }
    }
}

@Composable
private fun AdapterCard(capabilities: List<Capability>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.details_adapter_title))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.details_adapter_note),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(12.dp))

        capabilities.forEach { capability ->
            CapabilityLine(capability)
        }
    }
}

@Composable
private fun CapabilityLine(capability: Capability) {
    val tint = when (capability.supported) {
        true -> SignalColor.Excellent
        false -> TextTone.Tertiary
        null -> SignalColor.Fair
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = stringResource(capability.name),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = when (capability.supported) {
                true -> stringResource(R.string.details_yes)
                false -> stringResource(R.string.details_no)
                null -> stringResource(R.string.details_cannot_ask)
            },
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTone.Tertiary,
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    valueColor: Color = TextTone.Primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
        )
    }
}
