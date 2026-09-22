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
import com.sinyal.app.net.LanDevice
import com.sinyal.app.net.LanScanner
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.sinyal.app.ui.components.LinkRow

data class LanUiState(
    val details: ConnectionDetails = ConnectionDetails.Empty,
    val devices: List<LanDevice> = emptyList(),
    val scanning: Boolean = false,
)

class LanScanViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = LanScanner(app)

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
            _state.update { it.copy(scanning = true, devices = emptyList()) }
            val details = withContext(Dispatchers.IO) { scanner.readDetails() }
            _state.update { it.copy(details = details) }

            scanner.scan { device ->
                // Sorted as they arrive so the list does not reshuffle at the end.
                _state.update { current ->
                    current.copy(
                        devices = (current.devices + device)
                            .sortedBy { it.ipAddress.substringAfterLast('.').toIntOrNull() ?: 0 },
                    )
                }
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun cancel() {
        running?.cancel()
        _state.update { it.copy(scanning = false) }
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
            LinkRow(text = stringResource(R.string.devices_open_ping), onClick = onOpenPing)

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(
                    if (state.scanning) R.string.action_cancel else R.string.devices_scan,
                ),
                onClick = { if (state.scanning) viewModel.cancel() else viewModel.scan() },
            )

            if (state.scanning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent.Base,
                    trackColor = Ink.Stroke,
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
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            }
            Text(
                text = stringResource(R.string.unit_ms, device.responseMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (device.responseMs < 100) SignalColor.Excellent else TextTone.Secondary,
            )
        }
    }
}
