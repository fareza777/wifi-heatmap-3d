package com.sinyal.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SocialDistance
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.ui.components.Banner
import com.sinyal.app.ui.components.BannerAd
import com.sinyal.app.ui.components.CompactTile
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.SignalRing
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.SignalQuality
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.material.icons.rounded.Router

/**
 * The live reading, one action, and four places to go.
 *
 * Everything that used to be a list here has moved to the screen that owns it:
 * nearby networks to the networks page, saved scans to history, the settings
 * pile to settings. Repeating them made this a scroll rather than a dashboard.
 */
@Composable
fun HomeScreen(
    onStartScan: () -> Unit,
    onOpenNetworks: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenRtt: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenSettings: () -> Unit,
    adsRemoved: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onPermissionResult() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Accent.Base.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onOpenSettings = onOpenSettings)

            if (!state.wifiEnabled) {
                Spacer(Modifier.height(14.dp))
                Banner(
                    icon = Icons.Rounded.WifiOff,
                    title = stringResource(R.string.home_wifi_off_title),
                    message = stringResource(R.string.home_wifi_off_body),
                    tone = SignalColor.Dead,
                )
            }

            if (!state.hasLocationPermission) {
                Spacer(Modifier.height(14.dp))
                Banner(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(R.string.home_location_title),
                    message = stringResource(R.string.home_location_body),
                    tone = Accent.Bright,
                    actionLabel = stringResource(R.string.action_grant_permission),
                    onAction = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
            }

            Spacer(Modifier.height(4.dp))
            SignalRing(
                fraction = SignalQuality.normalize(state.snapshot.rssiDbm),
                rssiDbm = state.snapshot.rssiDbm,
                qualityLabel = stringResource(
                    if (state.snapshot.connected) {
                        state.snapshot.quality.label
                    } else {
                        R.string.state_not_connected
                    },
                ),
                connected = state.snapshot.connected,
            )

            Text(
                text = state.snapshot.ssid ?: stringResource(R.string.value_none),
                style = MaterialTheme.typography.headlineSmall,
                color = TextTone.Secondary,
            )

            Spacer(Modifier.height(18.dp))
            LinkStats(state)

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = stringResource(R.string.home_start_scan),
                onClick = onStartScan,
            )

            Spacer(Modifier.height(14.dp))
            MenuGrid(
                onOpenNetworks = onOpenNetworks,
                onOpenAnalysis = onOpenAnalysis,
                onOpenSpeedTest = onOpenSpeedTest,
                onOpenDevices = onOpenDevices,
                onOpenSecurity = onOpenSecurity,
                onOpenChannels = onOpenChannels,
                onOpenRtt = onOpenRtt,
                onOpenGraph = onOpenGraph,
                onOpenCoverage = onOpenCoverage,
                onOpenHistory = onOpenHistory,
                onOpenCompare = onOpenCompare,
            )

            Spacer(Modifier.height(14.dp))
            BannerAd(adsRemoved = adsRemoved)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name_short),
                style = MaterialTheme.typography.labelSmall,
                color = Accent.Bright,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.headlineSmall,
                color = TextTone.Primary,
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.Surface)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.home_settings),
                tint = TextTone.Secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LinkStats(state: HomeUiState) {
    val snapshot = state.snapshot
    val dash = stringResource(R.string.value_none)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.home_stat_band),
                value = if (snapshot.connected) snapshot.band.label else dash,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_stat_channel),
                value = if (snapshot.connected && snapshot.channel > 0) {
                    snapshot.channel.toString()
                } else {
                    dash
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.home_stat_link_speed),
                value = if (snapshot.connected) {
                    stringResource(R.string.unit_mbps, snapshot.linkSpeedMbps)
                } else {
                    dash
                },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_stat_standard),
                value = if (snapshot.connected) snapshot.generation.label else dash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Every destination in one grid, so nothing needs a second menu to reach. */
@Composable
private fun MenuGrid(
    onOpenNetworks: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenRtt: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCompare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactTile(
                icon = Icons.Rounded.Wifi,
                title = stringResource(R.string.home_tile_networks),
                subtitle = stringResource(R.string.home_tile_networks_sub),
                onClick = onOpenNetworks,
                modifier = Modifier.weight(1f),
            )
            CompactTile(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.home_tile_spectrum),
                subtitle = stringResource(R.string.home_tile_spectrum_sub),
                onClick = onOpenAnalysis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactTile(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_tile_channels),
                subtitle = stringResource(R.string.home_tile_channels_sub),
                onClick = onOpenChannels,
                modifier = Modifier.weight(1f),
            )
            CompactTile(
                icon = Icons.Rounded.SocialDistance,
                title = stringResource(R.string.home_tile_rtt),
                subtitle = stringResource(R.string.home_tile_rtt_sub),
                onClick = onOpenRtt,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactTile(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.home_tile_speed),
                subtitle = stringResource(R.string.home_tile_speed_sub),
                onClick = onOpenSpeedTest,
                modifier = Modifier.weight(1f),
            )
            CompactTile(
                icon = Icons.Rounded.Devices,
                title = stringResource(R.string.home_tile_devices),
                subtitle = stringResource(R.string.home_tile_devices_sub),
                onClick = onOpenDevices,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactTile(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.home_tile_security),
                subtitle = stringResource(R.string.home_tile_security_sub),
                onClick = onOpenSecurity,
                modifier = Modifier.weight(1f),
            )
            CompactTile(
                icon = Icons.Rounded.ShowChart,
                title = stringResource(R.string.home_tile_graph),
                subtitle = stringResource(R.string.home_tile_graph_sub),
                onClick = onOpenGraph,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactTile(
                icon = Icons.Rounded.Router,
                title = stringResource(R.string.home_tile_coverage),
                subtitle = stringResource(R.string.home_tile_coverage_sub),
                onClick = onOpenCoverage,
                modifier = Modifier.weight(1f),
            )
            CompactTile(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(R.string.home_tile_history),
                subtitle = stringResource(R.string.home_tile_history_sub),
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactTile(
                icon = Icons.Rounded.CompareArrows,
                title = stringResource(R.string.home_tile_compare),
                subtitle = stringResource(R.string.home_tile_compare_sub),
                onClick = onOpenCompare,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
