package com.sinyal.app.ui.screens

import android.app.Application
import android.net.wifi.WifiManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.net.BenchHost
import com.sinyal.app.net.LanBenchAdvertiser
import com.sinyal.app.net.LanBenchClient
import com.sinyal.app.net.LanBenchPhase
import com.sinyal.app.net.LanBenchResult
import com.sinyal.app.net.LanBenchSeeker
import com.sinyal.app.net.LanBenchServer
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.OptionCard
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LanMode { IDLE, HOSTING, MEASURING }

data class LanSpeedUiState(
    val mode: LanMode = LanMode.IDLE,
    val hostPort: Int = -1,
    val hostIp: String = "",
    val advertised: Boolean = false,
    val discovered: List<BenchHost> = emptyList(),
    val discovering: Boolean = false,
    val phase: LanBenchPhase = LanBenchPhase.IDLE,
    val liveMbps: Double = 0.0,
    val result: LanBenchResult? = null,
    val failed: Boolean = false,
)

/**
 * The two-phone benchmark: one phone hosts, the other measures.
 *
 * Hosting starts a bench server on a random port and advertises it over mDNS
 * as `_sinyalbench._tcp`, so the measuring phone sees it by name instead of
 * someone dictating an IP. Manual entry stays for the networks where
 * multicast never arrives — guest VLANs and most hotel Wi-Fi drop it.
 */
class LanSpeedViewModel(app: Application) : AndroidViewModel(app) {

    private val wifiManager =
        app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val server = LanBenchServer()
    private val client = LanBenchClient()
    private val advertiser = LanBenchAdvertiser(app)
    private val seeker = LanBenchSeeker(app)

    private val _state = MutableStateFlow(LanSpeedUiState())
    val state: StateFlow<LanSpeedUiState> = _state.asStateFlow()

    private var acceptJob: Job? = null
    private var benchJob: Job? = null
    private var discoverJob: Job? = null

    fun startHosting() {
        if (_state.value.mode == LanMode.HOSTING) return
        stopBench()
        runCatching {
            server.start()
            acceptJob = viewModelScope.launch(Dispatchers.IO) { server.acceptLoop() }
            advertiser.advertise(server.port, ADVERTISED_NAME)
            _state.update {
                it.copy(
                    mode = LanMode.HOSTING,
                    hostPort = server.port,
                    hostIp = localIp(),
                    advertised = true,
                    result = null,
                    failed = false,
                )
            }
        }
    }

    fun stopHosting() {
        advertiser.stop()
        acceptJob?.cancel()
        server.stop()
        _state.update {
            it.copy(mode = LanMode.IDLE, hostPort = -1, advertised = false)
        }
    }

    fun discover() {
        if (discoverJob?.isActive == true) return
        discoverJob = viewModelScope.launch {
            _state.update { it.copy(discovering = true, discovered = emptyList()) }
            seeker.discover(SEEK_WINDOW_MS) { host ->
                _state.update { s ->
                    s.copy(discovered = (s.discovered + host).distinctBy { "${it.host}|${it.port}" })
                }
            }
            _state.update { it.copy(discovering = false) }
        }
    }

    fun runBench(host: String, port: Int) {
        if (benchJob?.isActive == true) return
        benchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    mode = LanMode.MEASURING,
                    phase = LanBenchPhase.LATENCY,
                    result = null,
                    failed = false,
                    liveMbps = 0.0,
                )
            }
            val result = client.bench(
                host = host,
                port = port,
                onPhase = { phase -> _state.update { s -> s.copy(phase = phase) } },
                onLive = { mbps -> _state.update { s -> s.copy(liveMbps = mbps) } },
            )
            _state.update {
                it.copy(
                    mode = LanMode.IDLE,
                    phase = LanBenchPhase.IDLE,
                    result = result,
                    failed = result == null,
                )
            }
        }
    }

    fun stopBench() {
        benchJob?.cancel()
        _state.update { it.copy(mode = LanMode.IDLE, phase = LanBenchPhase.IDLE) }
    }

    override fun onCleared() {
        advertiser.stop()
        server.stop()
        super.onCleared()
    }

    /** The phone's own IPv4 on this Wi-Fi, shown so pairing can be checked by eye. */
    private fun localIp(): String {
        val raw = wifiManager.connectionInfo?.ipAddress ?: 0
        return if (raw == 0) "" else "%d.%d.%d.%d".format(
            raw and 0xFF, raw shr 8 and 0xFF, raw shr 16 and 0xFF, raw shr 24 and 0xFF,
        )
    }

    private companion object {
        const val ADVERTISED_NAME = "Sinyal Bench"
        const val SEEK_WINDOW_MS = 3_500L
    }
}

/**
 * iperf on phones: the honest number for "is the Wi-Fi itself fast here", with
 * no internet in the way. Latency under load is measured too — a link that
 * fills its own buffers reads fast and still drops calls.
 */
@Composable
fun LanSpeedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanSpeedViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var manualIp by remember { mutableStateOf("") }

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
            BackBar(title = stringResource(R.string.lanspeed_title), onBack = onBack)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lanspeed_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionCard(
                    title = stringResource(R.string.lanspeed_host_mode),
                    description = stringResource(R.string.lanspeed_host_mode_sub),
                    badge = if (state.mode == LanMode.HOSTING) {
                        stringResource(R.string.lanspeed_on)
                    } else {
                        ""
                    },
                    selected = state.mode == LanMode.HOSTING,
                    onClick = {
                        if (state.mode == LanMode.HOSTING) viewModel.stopHosting()
                        else viewModel.startHosting()
                    },
                    modifier = Modifier.weight(1f),
                )
                OptionCard(
                    title = stringResource(R.string.lanspeed_measure_mode),
                    description = stringResource(R.string.lanspeed_measure_mode_sub),
                    badge = "",
                    selected = state.mode == LanMode.MEASURING,
                    onClick = viewModel::discover,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.mode == LanMode.HOSTING) {
                Spacer(Modifier.height(14.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.lanspeed_hosting_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.lanspeed_hosting_address,
                            state.hostIp,
                            state.hostPort,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = Accent.Bright,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.lanspeed_hosting_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Secondary,
                    )
                }
            }

            if (state.mode == LanMode.MEASURING) {
                Spacer(Modifier.height(14.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            when (state.phase) {
                                LanBenchPhase.LATENCY -> R.string.lanspeed_phase_latency
                                LanBenchPhase.DOWNLOAD -> R.string.lanspeed_phase_download
                                LanBenchPhase.UPLOAD -> R.string.lanspeed_phase_upload
                                else -> R.string.lanspeed_phase_latency
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.unit_mbps_decimal, state.liveMbps),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextTone.Primary,
                    )
                }
            }

            state.result?.let { result ->
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.speed_download_label),
                        value = stringResource(R.string.unit_mbps_decimal, result.downloadMbps),
                        valueColor = SignalColor.Excellent,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.speed_upload_label),
                        value = stringResource(R.string.unit_mbps_decimal, result.uploadMbps),
                        valueColor = Accent.Bright,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.lanspeed_idle_latency),
                        value = "%.1f ms".format(result.latencyMs),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.lanspeed_loaded_latency),
                        value = result.loadedLatencyMs?.let { "%.1f ms".format(it) }
                            ?: stringResource(R.string.value_none),
                        modifier = Modifier.weight(1f),
                    )
                }
                result.loadedLatencyMs?.let { loaded ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            if (loaded - result.latencyMs < 20) {
                                R.string.lanspeed_verdict_good
                            } else {
                                R.string.lanspeed_verdict_loaded
                            },
                            (loaded - result.latencyMs).toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Secondary,
                    )
                }
            }

            if (state.failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.lanspeed_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalColor.Dead,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.lanspeed_peers_title),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(8.dp))

            if (state.discovered.isEmpty() && !state.discovering) {
                Text(
                    text = stringResource(R.string.lanspeed_no_peers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }
            state.discovered.forEach { host ->
                PeerRow(
                    host = host,
                    busy = state.mode == LanMode.MEASURING,
                    onClick = { viewModel.runBench(host.host, host.port) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    placeholder = {
                        Text(stringResource(R.string.lanspeed_manual_hint))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent.Base,
                        unfocusedBorderColor = Ink.Stroke,
                        focusedTextColor = TextTone.Primary,
                        unfocusedTextColor = TextTone.Primary,
                        cursorColor = Accent.Bright,
                    ),
                    modifier = Modifier.weight(1f),
                )
                GradientButton(
                    text = stringResource(R.string.lanspeed_connect),
                    onClick = {
                        // "10.0.0.5" or "10.0.0.5:5309" — hosts pick 5309 when free.
                        val parts = manualIp.trim().split(':')
                        val port = parts.getOrNull(1)?.toIntOrNull() ?: MANUAL_DEFAULT_PORT
                        viewModel.runBench(parts[0], port)
                    },
                    enabled = manualIp.contains('.') && state.mode != LanMode.MEASURING,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PeerRow(host: BenchHost, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Stroke, shape)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = host.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextTone.Primary,
            )
            Text(
                text = "${host.host}:${host.port}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
        }
        Text(
            text = stringResource(R.string.lanspeed_measure),
            style = MaterialTheme.typography.labelLarge,
            color = Accent.Bright,
        )
    }
}

private const val MANUAL_DEFAULT_PORT = 5_309
