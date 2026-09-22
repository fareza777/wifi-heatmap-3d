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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.net.ConnectionDetails
import com.sinyal.app.net.DiscoveredService
import com.sinyal.app.net.LanDevice
import com.sinyal.app.net.LanScanner
import com.sinyal.app.net.MdnsDiscovery
import com.sinyal.app.net.UpnpDevice
import com.sinyal.app.net.UpnpDiscovery
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

data class LanUiState(
    val details: ConnectionDetails = ConnectionDetails.Empty,
    val devices: List<LanDevice> = emptyList(),
    val scanning: Boolean = false,
    /** Passive service discovery still listening after the sweep finished. */
    val discovering: Boolean = false,
    val discoveryLeftSec: Int = 0,
) {
    val busy: Boolean get() = scanning || discovering
}

class LanScanViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = LanScanner(app)
    private val mdns = MdnsDiscovery(app)
    private val upnp = UpnpDiscovery(app)

    private val _state = MutableStateFlow(LanUiState())
    val state: StateFlow<LanUiState> = _state.asStateFlow()

    private var running: Job? = null

    init {
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) { scanner.readDetails() }
            _state.update { it.copy(details = details) }
        }
    }

    fun scan() {
        if (running?.isActive == true) return
        running = viewModelScope.launch {
            _state.update {
                it.copy(scanning = true, discovering = false, devices = emptyList())
            }
            val details = withContext(Dispatchers.IO) { scanner.readDetails() }
            _state.update { it.copy(details = details) }

            scanner.scan { device -> mergeDevice(device) }
            _state.update { it.copy(scanning = false) }

            if (isActive) discover()
        }
    }

    /**
     * Phase two: broadcast SSDP and browse mDNS, attaching whatever answers to
     * the devices the sweep already found — or adding them when a device stays
     * silent on TCP but still announces itself (Chromecasts and printers do).
     */
    private suspend fun discover() {
        _state.update { it.copy(discovering = true, discoveryLeftSec = DISCOVERY_WINDOW_SEC) }
        val countdown = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _state.update { it.copy(discoveryLeftSec = (it.discoveryLeftSec - 1).coerceAtLeast(0)) }
            }
        }
        try {
            coroutineScope {
                val ssdpJob = async {
                    runCatching {
                        upnp.discover(DISCOVERY_WINDOW_MS) { device ->
                            attachUpnp(device)
                        }
                    }
                }
                val mdnsJob = async {
                    runCatching {
                        mdns.discover(DISCOVERY_WINDOW_MS) { service ->
                            attachService(service)
                        }
                    }
                }
                awaitAll(ssdpJob, mdnsJob)
            }
        } finally {
            countdown.cancel()
            _state.update { it.copy(discovering = false, discoveryLeftSec = 0) }
        }
    }

    private fun attachService(service: DiscoveredService) {
        mergeDevice(
            LanDevice(
                ipAddress = service.ipAddress,
                hostName = null,
                isThisPhone = false,
                isGateway = false,
                responseMs = 0,
                services = listOf(service),
            ),
        )
    }

    private fun attachUpnp(device: UpnpDevice) {
        _state.update { current ->
            val existing = current.devices.firstOrNull { it.ipAddress == device.ipAddress }
            val base = existing ?: LanDevice(
                ipAddress = device.ipAddress,
                hostName = null,
                isThisPhone = false,
                isGateway = device.ipAddress == _state.value.details.gateway,
                responseMs = 0,
            )
            val updated = base.copy(upnp = device.fingerprint ?: base.upnp)
            current.copy(devices = sortDevices(current.devices - base + updated))
        }
    }

    private fun mergeDevice(device: LanDevice) {
        _state.update { current ->
            val existing = current.devices.firstOrNull { it.ipAddress == device.ipAddress }
            val merged = if (existing == null) {
                device
            } else {
                existing.copy(
                    hostName = existing.hostName ?: device.hostName,
                    isGateway = existing.isGateway || device.isGateway,
                    responseMs = if (existing.responseMs > 0) existing.responseMs else device.responseMs,
                    services = (existing.services + device.services)
                        .distinctBy { "${it.name}|${it.type}|${it.port}" },
                )
            }
            val rest = if (existing == null) current.devices else current.devices - existing
            current.copy(devices = sortDevices(rest + merged))
        }
    }

    private fun sortDevices(devices: List<LanDevice>): List<LanDevice> =
        devices.distinctBy { it.ipAddress }
            .sortedBy { it.ipAddress.substringAfterLast('.').toIntOrNull() ?: 0 }

    fun cancel() {
        running?.cancel()
        _state.update { it.copy(scanning = false, discovering = false) }
    }

    private companion object {
        /** Long enough for slow mDNS responders; short enough to feel bounded. */
        const val DISCOVERY_WINDOW_MS = 9_000L
        const val DISCOVERY_WINDOW_SEC = 9
    }
}

/**
 * Who else is on this Wi-Fi, and what address this phone holds.
 *
 * Android stopped exposing the ARP table around API 29, so the subnet is probed
 * rather than read. That takes a few seconds and can miss devices configured to
 * ignore unknown traffic — stated on screen, because a scanner that quietly
 * under-reports is worse than one that admits its limits.
 */
@Composable
fun LanScanScreen(
    onBack: () -> Unit,
    onOpenPing: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanScanViewModel = viewModel(),
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
            BackBar(title = stringResource(R.string.devices_title), onBack = onBack)

            Spacer(Modifier.height(18.dp))
            DetailsCard(state.details)

            Spacer(Modifier.height(10.dp))
            PingRow(text = stringResource(R.string.devices_open_ping), onClick = onOpenPing)

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(
                    if (state.busy) R.string.action_cancel else R.string.devices_scan,
                ),
                onClick = { if (state.busy) viewModel.cancel() else viewModel.scan() },
            )

            if (state.scanning || state.discovering) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent.Base,
                    trackColor = Ink.Stroke,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.discovering) {
                        stringResource(
                            R.string.devices_discovering,
                            state.discoveryLeftSec,
                        )
                    } else {
                        stringResource(R.string.devices_sweeping)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }

            if (state.devices.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.devices_found, state.devices.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                state.devices.forEach { device ->
                    DeviceRow(device)
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.devices_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Opens the ping tool, which is the natural next step after finding a device. */
@Composable
private fun PingRow(text: String, onClick: () -> Unit) {
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

@Composable
private fun DetailsCard(details: ConnectionDetails) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.devices_connection_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))
        DetailRow(
            stringResource(R.string.devices_ip),
            details.ipAddress ?: stringResource(R.string.value_none),
        )
        DetailRow(
            stringResource(R.string.devices_gateway),
            details.gateway ?: stringResource(R.string.value_none),
        )
        DetailRow(
            stringResource(R.string.devices_subnet),
            details.prefixLength?.let { prefix ->
                stringResource(R.string.devices_subnet_value, prefix, details.hostCount ?: 0)
            } ?: stringResource(R.string.value_none),
        )
        DetailRow(
            stringResource(R.string.devices_dns),
            details.dnsServers.take(2).joinToString(", ")
                .ifBlank { stringResource(R.string.value_none) },
        )
        DetailRow(
            stringResource(R.string.devices_interface),
            details.interfaceName ?: stringResource(R.string.value_none),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeviceRow(device: LanDevice) {
    val expandable = device.services.isNotEmpty() || device.upnp != null
    var expanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (expandable) {
                Modifier.clickable { expanded = !expanded }
            } else {
                Modifier
            },
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.Glow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        device.isThisPhone -> Icons.Rounded.PhoneAndroid
                        device.isGateway -> Icons.Rounded.Router
                        else -> Icons.Rounded.Devices
                    },
                    contentDescription = null,
                    tint = Accent.Bright,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = device.displayName(
                        thisPhone = stringResource(R.string.device_this_phone),
                        router = stringResource(R.string.device_router),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
                device.serviceSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent.Bright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (device.responseMs > 0 || expandable) {
                Text(
                    text = stringResource(R.string.unit_ms, device.responseMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device.responseMs < 100) {
                        SignalColor.Excellent
                    } else {
                        TextTone.Secondary
                    },
                )
            }
            if (expandable) {
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Tertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (expanded) {
            DeviceDetails(device)
        }
    }
}

/** What SSDP and mDNS said about this device — the fingerprint, not the ping. */
@Composable
private fun DeviceDetails(device: LanDevice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        device.upnp?.let { fingerprint ->
            fingerprint.friendlyName?.let {
                FingerprintRow(stringResource(R.string.devices_fingerprint_name), it)
            }
            fingerprint.manufacturer?.let {
                FingerprintRow(stringResource(R.string.devices_fingerprint_vendor), it)
            }
            fingerprint.modelName?.let {
                FingerprintRow(stringResource(R.string.devices_fingerprint_model), it)
            }
            fingerprint.deviceType?.let {
                FingerprintRow(stringResource(R.string.devices_fingerprint_type), it)
            }
        }

        if (device.services.isNotEmpty()) {
            if (device.upnp != null) Spacer(Modifier.height(6.dp))
            device.services.forEach { service ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = serviceLabel(service),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerprintRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun serviceLabel(service: DiscoveredService): String = buildString {
    append(service.type.ifBlank { service.via.name })
    if (service.port > 0) append(":", service.port)
}
