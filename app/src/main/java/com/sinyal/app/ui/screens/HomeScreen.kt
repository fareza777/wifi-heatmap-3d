package com.sinyal.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.ui.components.Banner
import com.sinyal.app.ui.components.BannerAd
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.SignalRing
import com.sinyal.app.ui.components.ToolTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.SignalQuality
import com.sinyal.app.wifi.WifiSnapshot

/**
 * One screen, no scrolling: the live reading, the scan, and six tools.
 *
 * It used to scroll through ten tiles and a block of four statistics, which is
 * what made it feel like a pile of features rather than an app. Everything that
 * belongs to a result now lives with that result — handovers and transmitters in
 * the scan, before-and-after in the history, the time graph in the spectrum —
 * and the link figures collapse into one line under the network name.
 *
 * The hero ring is sized from the height actually available, so the layout
 * holds together on a compact phone rather than being tuned for one and pushed
 * off the bottom of another.
 */
@Composable
fun HomeScreen(
    onStartScan: () -> Unit,
    onOpenNetworks: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSecurity: () -> Unit,
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
                .height(480.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Accent.Base.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            val ringSize = ringSizeFor(maxHeight, hasBanner = !state.wifiEnabled ||
                !state.hasLocationPermission)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(onOpenSettings = onOpenSettings)

                when {
                    !state.wifiEnabled -> {
                        Spacer(Modifier.height(10.dp))
                        Banner(
                            icon = Icons.Rounded.WifiOff,
                            title = stringResource(R.string.home_wifi_off_title),
                            message = stringResource(R.string.home_wifi_off_body),
                            tone = SignalColor.Dead,
                        )
                    }

                    !state.hasLocationPermission -> {
                        Spacer(Modifier.height(10.dp))
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
                }

                Spacer(Modifier.weight(1f))

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
                    diameter = ringSize,
                )

                Text(
                    text = state.snapshot.ssid ?: stringResource(R.string.value_none),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.snapshot.connected) {
                    Spacer(Modifier.height(10.dp))
                    LinkChips(state.snapshot)
                }

                Spacer(Modifier.weight(1f))

                GradientButton(
                    text = stringResource(R.string.home_start_scan),
                    onClick = onStartScan,
                )

                Spacer(Modifier.height(14.dp))
                ToolGrid(
                    onOpenNetworks = onOpenNetworks,
                    onOpenAnalysis = onOpenAnalysis,
                    onOpenSpeedTest = onOpenSpeedTest,
                    onOpenDevices = onOpenDevices,
                    onOpenSecurity = onOpenSecurity,
                    onOpenHistory = onOpenHistory,
                )

                Spacer(Modifier.height(12.dp))
                BannerAd(adsRemoved = adsRemoved)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Hero size from the height the screen actually has.
 *
 * Everything else on Home is a fixed height; the ring is the one element that
 * can give way, so it absorbs the difference between a tall phone and a short
 * one — and between a screen with a warning banner and one without.
 */
private fun ringSizeFor(available: Dp, hasBanner: Boolean): Dp {
    val reserved = if (hasBanner) RESERVED_WITH_BANNER else RESERVED
    return (available - reserved).coerceIn(MIN_RING, MAX_RING)
}

/** Top bar, name, chips, button, grid, ad and the gaps between them. */
private val RESERVED = 560.dp
private val RESERVED_WITH_BANNER = 660.dp
private val MIN_RING = 150.dp
private val MAX_RING = 240.dp

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

/**
 * The four link figures as one row of chips.
 *
 * They used to be four tiles in a two-by-two block, taking as much height as
 * the ring itself for numbers most people glance at once. The full detail is a
 * tap away on the connection screen.
 */
@Composable
private fun LinkChips(snapshot: WifiSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(snapshot.band.label)
        if (snapshot.channel > 0) {
            Chip(stringResource(R.string.home_chip_channel, snapshot.channel))
        }
        Chip(snapshot.generation.label)
        Chip(stringResource(R.string.unit_mbps, snapshot.linkSpeedMbps))
    }
}

@Composable
private fun Chip(text: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextTone.Secondary,
            maxLines = 1,
        )
    }
}

/** Six tools, two rows of three — the whole app reachable without scrolling. */
@Composable
private fun ToolGrid(
    onOpenNetworks: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                icon = Icons.Rounded.Wifi,
                label = stringResource(R.string.home_tile_networks),
                onClick = onOpenNetworks,
                modifier = Modifier.weight(1f),
            )
            ToolTile(
                icon = Icons.Rounded.GraphicEq,
                label = stringResource(R.string.home_tile_spectrum),
                onClick = onOpenAnalysis,
                modifier = Modifier.weight(1f),
            )
            ToolTile(
                icon = Icons.Rounded.Speed,
                label = stringResource(R.string.home_tile_speed),
                onClick = onOpenSpeedTest,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                icon = Icons.Rounded.Devices,
                label = stringResource(R.string.home_tile_devices),
                onClick = onOpenDevices,
                modifier = Modifier.weight(1f),
            )
            ToolTile(
                icon = Icons.Rounded.Shield,
                label = stringResource(R.string.home_tile_security),
                onClick = onOpenSecurity,
                modifier = Modifier.weight(1f),
            )
            ToolTile(
                icon = Icons.Rounded.CalendarMonth,
                label = stringResource(R.string.home_tile_history),
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
