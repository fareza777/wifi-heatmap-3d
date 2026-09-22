package com.sinyal.app.ui.screens

import android.app.Application
import android.net.wifi.ScanResult
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.Banner
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.RangeAnchor
import com.sinyal.app.wifi.RttMeasurement
import com.sinyal.app.wifi.RttRanger
import com.sinyal.app.wifi.Trilaterated
import com.sinyal.app.wifi.Trilateration
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import com.sinyal.app.wifi.channelFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An RTT-capable AP kept with its raw ScanResult for ranging requests. */
data class RttPeer(
    val scanResult: ScanResult,
) {
    val bssid: String get() = scanResult.BSSID
    val ssid: String get() = scanResult.SSID.ifBlank { "—" }
    val rssiDbm: Int get() = scanResult.level
    val channel: Int get() = channelFor(scanResult.frequency)
}

data class RttUiState(
    val supported: Boolean = true,
    val available: Boolean = true,
    val wifiEnabled: Boolean = true,
    val hasPermission: Boolean = true,
    val peers: List<RttPeer> = emptyList(),
    /** Per-BSSID latest measurement, null while its first burst is running. */
    val results: Map<String, RttMeasurement> = emptyMap(),
    val measuring: Set<String> = emptySet(),
    val targetBssid: String? = null,
    val anchors: List<RangeAnchor> = emptyList(),
    val estimate: Trilaterated? = null,
    val locating: Boolean = false,
    val lastError: Boolean = false,
    /** Metres per grid cell on the sketch canvas. */
    val metresPerCell: Float = 1f,
)

class RttViewModel(app: Application) : AndroidViewModel(app) {

    // The RTT API only exists on Android 9+; below it this stays null and the
    // screen falls back to the not-supported state instead of linking the class.
    private val sdkOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private val ranger = if (sdkOk) RttRanger(app) else null
    private val scanner = WifiScanner(app)
    private val monitor = WifiMonitor(app)

    private val _state = MutableStateFlow(RttUiState())
    val state: StateFlow<RttUiState> = _state.asStateFlow()

    private var measureJob: Job? = null

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val supported = sdkOk && ranger!!.hardwareSupported
        val wifiEnabled = monitor.isWifiEnabled
        val available = sdkOk && ranger!!.available
        val hasPermission = scanner.hasLocationPermission
        withContext(Dispatchers.Default) { scanner.requestScan() }
        val peers = withContext(Dispatchers.Default) {
            scanner.rangingCapableResults().map(::RttPeer)
        }
        _state.update {
            it.copy(
                supported = supported,
                wifiEnabled = wifiEnabled,
                available = available,
                hasPermission = hasPermission,
                peers = peers,
                targetBssid = it.targetBssid
                    ?.takeIf { target -> peers.any { p -> p.bssid == target } }
                    ?: peers.firstOrNull()?.bssid,
            )
        }
    }

    fun selectTarget(bssid: String) =
        _state.update { it.copy(targetBssid = bssid) }

    /** Plain distance read for one peer row. */
    fun measure(bssid: String) {
        val peer = _state.value.peers.firstOrNull { it.bssid == bssid } ?: return
        viewModelScope.launch {
            _state.update { it.copy(measuring = it.measuring + bssid) }
            val result = if (sdkOk) {
                ranger!!.measure(peer.scanResult)
            } else {
                RttMeasurement(
                    distanceM = null, stddevM = null, rssiDbm = null, samples = 0, failures = 1,
                )
            }
            _state.update {
                it.copy(
                    measuring = it.measuring - bssid,
                    results = it.results + (bssid to result),
                )
            }
        }
    }

    fun setMetresPerCell(value: Float) =
        _state.update { it.copy(metresPerCell = value.coerceIn(0.25f, 8f)) }

    /**
     * A tap on the sketch is a promise that the phone stood at that spot;
     * the anchor is only recorded once ranging returns a distance for it.
     */
    fun addAnchor(xM: Double, yM: Double) {
        if (_state.value.locating) return
        val peer = _state.value.peers.firstOrNull { it.bssid == _state.value.targetBssid }
            ?: return

        measureJob?.cancel()
        measureJob = viewModelScope.launch {
            _state.update { it.copy(locating = true, lastError = false) }
            val result = if (sdkOk) {
                ranger!!.measure(peer.scanResult, bursts = 3)
            } else {
                RttMeasurement(
                    distanceM = null, stddevM = null, rssiDbm = null, samples = 0, failures = 1,
                )
            }
            _state.update { current ->
                if (result.distanceM == null) {
                    current.copy(locating = false, lastError = true)
                } else {
                    val anchors = current.anchors +
                        RangeAnchor(xM, yM, result.distanceM)
                    current.copy(
                        locating = false,
                        anchors = anchors,
                        estimate = if (anchors.size >= 3) Trilateration.solve(anchors) else null,
                    )
                }
            }
        }
    }

    fun undoAnchor() = _state.update {
        val anchors = it.anchors.dropLast(1)
        it.copy(
            anchors = anchors,
            estimate = if (anchors.size >= 3) Trilateration.solve(anchors) else null,
        )
    }

    fun resetAnchors() = _state.update {
        it.copy(anchors = emptyList(), estimate = null, lastError = false)
    }
}

/**
 * 802.11mc ranging: measured distance to APs, plus walk-and-tap locating.
 *
 * Two tools in one because they share the same ranging engine — the list
 * answers "how far", the sketch answers "where".
 */
@Composable
fun RttScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RttViewModel = viewModel(),
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
            BackBar(title = stringResource(R.string.rtt_title), onBack = onBack)

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.rtt_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = TextTone.Primary,
            )
            Text(
                text = stringResource(R.string.rtt_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(14.dp))
            Blockers(state)

            if (state.peers.isNotEmpty() && state.available && state.hasPermission) {
                Spacer(Modifier.height(14.dp))
                PeerList(state, viewModel)

                Spacer(Modifier.height(14.dp))
                LocateCard(state, viewModel)
            }

            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = stringResource(R.string.analysis_rescan),
                onClick = viewModel::refresh,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Every reason ranging can't run, stated before the empty list confuses. */
@Composable
private fun Blockers(state: RttUiState) {
    when {
        !state.supported -> Banner(
            icon = Icons.Rounded.Wifi,
            title = stringResource(R.string.rtt_no_hardware_title),
            message = stringResource(R.string.rtt_no_hardware_body),
            tone = SignalColor.Dead,
        )
        !state.wifiEnabled -> Banner(
            icon = Icons.Rounded.Wifi,
            title = stringResource(R.string.rtt_wifi_off_title),
            message = stringResource(R.string.rtt_wifi_off_body),
            tone = SignalColor.Fair,
        )
        !state.hasPermission -> Banner(
            icon = Icons.Rounded.MyLocation,
            title = stringResource(R.string.rtt_permission_title),
            message = stringResource(R.string.rtt_permission_body),
            tone = Accent.Bright,
        )
        !state.available -> Banner(
            icon = Icons.Rounded.Wifi,
            title = stringResource(R.string.rtt_unavailable_title),
            message = stringResource(R.string.rtt_unavailable_body),
            tone = SignalColor.Fair,
        )
        state.peers.isEmpty() -> Banner(
            icon = Icons.Rounded.Router,
            title = stringResource(R.string.rtt_no_peers_title),
            message = stringResource(R.string.rtt_no_peers_body),
            tone = SignalColor.Fair,
        )
    }
}

@Composable
private fun PeerList(state: RttUiState, viewModel: RttViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.rtt_peers_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(6.dp))
        state.peers.forEach { peer ->
            PeerRow(
                peer = peer,
                result = state.results[peer.bssid],
                measuring = peer.bssid in state.measuring,
                selected = peer.bssid == state.targetBssid,
                onSelect = { viewModel.selectTarget(peer.bssid) },
                onMeasure = { viewModel.measure(peer.bssid) },
            )
        }
    }
}

@Composable
private fun PeerRow(
    peer: RttPeer,
    result: RttMeasurement?,
    measuring: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onMeasure: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Radio-dot selector: this peer is what the locate sketch ranges.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Accent.Base else Ink.Stroke)
                    .clickable(onClick = onSelect),
            )
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            ) {
                Text(
                    text = peer.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.rtt_peer_meta,
                        peer.bssid,
                        peer.channel,
                        peer.rssiDbm,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
            Text(
                text = stringResource(
                    if (measuring) R.string.rtt_measuring else R.string.rtt_measure,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (measuring) TextTone.Tertiary else Accent.Bright,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !measuring, onClick = onMeasure)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        result?.let { measurement ->
            Spacer(Modifier.height(6.dp))
            if (measurement.succeeded) {
                Text(
                    text = stringResource(
                        R.string.rtt_distance_value,
                        measurement.distanceM ?: 0.0,
                        measurement.stddevM ?: 0.0,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = SignalColor.Excellent,
                )
                Text(
                    text = stringResource(
                        R.string.rtt_samples,
                        measurement.samples,
                        measurement.failures,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            } else {
                Text(
                    text = stringResource(R.string.rtt_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalColor.Weak,
                )
            }
        }
    }
}

/**
 * Walk-and-tap locating.
 *
 * The sketch grid is the floor plan: a tap says "I stood here", ranging says
 * how far "here" is from the router, and three or more spots fix the router's
 * position by least squares — the same loop RTT trilateration tools use.
 */
@Composable
private fun LocateCard(state: RttUiState, viewModel: RttViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.rtt_locate_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.rtt_locate_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )

        Spacer(Modifier.height(10.dp))
        ScaleRow(state.metresPerCell, viewModel::setMetresPerCell)

        Spacer(Modifier.height(10.dp))
        SketchCanvas(state, viewModel)

        Spacer(Modifier.height(10.dp))
        AnchorList(state)

        state.estimate?.let { estimate ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.rtt_estimate,
                    estimate.x,
                    estimate.y,
                    estimate.rmseM,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = SignalColor.Excellent,
            )
            Text(
                text = stringResource(R.string.rtt_estimate_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
        }

        if (state.locating) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.rtt_measuring),
                style = MaterialTheme.typography.bodyMedium,
                color = Accent.Bright,
            )
        }
        if (state.lastError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.rtt_anchor_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = SignalColor.Weak,
            )
        }

        if (state.anchors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.rtt_undo),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextTone.Secondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::undoAnchor)
                        .padding(8.dp),
                )
                Text(
                    text = stringResource(R.string.rtt_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = SignalColor.Weak,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::resetAnchors)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ScaleRow(metresPerCell: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.rtt_scale),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
            modifier = Modifier.weight(1f),
        )
        ScaleStepper(label = "−") { onChange(metresPerCell / 2f) }
        Text(
            text = stringResource(R.string.rtt_scale_value, metresPerCell),
            style = MaterialTheme.typography.labelLarge,
            color = TextTone.Primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        ScaleStepper(label = "+") { onChange(metresPerCell * 2f) }
    }
}

@Composable
private fun ScaleStepper(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Raised)
            .border(1.dp, Ink.Stroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = TextTone.Primary)
    }
}

@Composable
private fun SketchCanvas(state: RttUiState, viewModel: RttViewModel) {
    val metresPerCell = state.metresPerCell
    val gridColor = Ink.Stroke
    val anchorColor = TextTone.Secondary
    val estimateColor = Accent.Bright
    val ringColor = Accent.Base
    val locating = state.locating

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Raised)
            .border(1.dp, Ink.Stroke, RoundedCornerShape(16.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(metresPerCell, locating) {
                    detectTapGestures { offset ->
                        val cellPxPx = CELL_PX * density
                        viewModel.addAnchor(
                            (offset.x / cellPxPx * metresPerCell).toDouble(),
                            (offset.y / cellPxPx * metresPerCell).toDouble(),
                        )
                    }
                },
        ) {
            val cellPx = CELL_PX * density
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                x += cellPx
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                y += cellPx
            }

            state.anchors.forEach { anchor ->
                val px = (anchor.x / metresPerCell * cellPx).toFloat()
                val py = (anchor.y / metresPerCell * cellPx).toFloat()
                drawCircle(anchorColor, 8f, Offset(px, py))
                drawCircle(
                    anchorColor.copy(alpha = 0.25f),
                    (anchor.distanceM / metresPerCell * cellPx).toFloat()
                        .coerceAtMost(size.maxDimension),
                    Offset(px, py),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    ),
                )
            }

            state.estimate?.let { estimate ->
                val ex = (estimate.x / metresPerCell * cellPx).toFloat()
                val ey = (estimate.y / metresPerCell * cellPx).toFloat()
                drawCircle(estimateColor, 12f, Offset(ex, ey))
                drawCircle(
                    ringColor.copy(alpha = 0.5f),
                    24f,
                    Offset(ex, ey),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                )
            }
        }
    }
}

/** Grid pitch in dp — a finger-friendly cell at any density. */
private const val CELL_PX = 40f

@Composable
private fun AnchorList(state: RttUiState) {
    if (state.anchors.isEmpty()) {
        Text(
            text = stringResource(R.string.rtt_anchors_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    state.anchors.forEachIndexed { index, anchor ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.rtt_anchor_point,
                    index + 1,
                    anchor.x,
                    anchor.y,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
            Text(
                text = stringResource(R.string.rtt_anchor_distance, anchor.distanceM),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Primary,
            )
        }
    }
}
