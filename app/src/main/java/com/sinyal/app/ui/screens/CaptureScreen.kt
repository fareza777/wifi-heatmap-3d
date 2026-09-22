package com.sinyal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.Config
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.components.StatusPill
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.SignalQuality
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.loaders.MaterialLoader
import com.google.android.filament.MaterialInstance
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.runtime.LaunchedEffect

/** Radius of a sample marker in metres — small enough to read as a point cloud. */
private const val MARKER_RADIUS = 0.07f

/** Enough lost-tracking events that the geometry is probably distorted. */
private const val JUMP_WARNING_THRESHOLD = 3

@Composable
fun CaptureScreen(
    mode: ScanMode,
    measureSpeed: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(measureSpeed) { viewModel.enableSpeedSurvey(measureSpeed) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            sessionConfiguration = { session, config ->
                // Depth starts off; the view model upgrades it to AUTOMATIC only
                // when the device has real depth hardware. Depth-from-motion on
                // a phone without a depth sensor is pure CPU work, competing for
                // exactly the feature tracking ARCore was starving for:
                // sessions were reporting INSUFFICIENT_FEATURES and never
                // recovering.
                config.depthMode = Config.DepthMode.DISABLED
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

                // Fixed focus, which is what ARCore recommends for tracking:
                // autofocus keeps changing the camera intrinsics and blurs the
                // image while it hunts, and both cost the feature matching this
                // scan depends on. Everything else is left at ARCore's defaults
                // — three settings were changed at once earlier, which made it
                // impossible to tell which one mattered.
                config.focusMode = Config.FocusMode.FIXED
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            },
            onSessionUpdated = { session, frame ->
                viewModel.onArFrame(session, frame)
            },
            onTrackingFailureChanged = { reason ->
                viewModel.onTrackingHint(reason?.let(::hintFor))
                viewModel.onTrackingFailure(reason)
            },
        ) {
            // One instance per quality bucket, not per marker: a fresh MaterialInstance
            // for every cell would allocate hundreds of them on a weak GPU.
            val markerMaterials = remember(materialLoader) { markerMaterials(materialLoader) }

            state.grid.occupiedCells.forEach { cell ->
                key(cell.ix, cell.iy, cell.iz) {
                    SphereNode(
                        radius = MARKER_RADIUS,
                        materialInstance = markerMaterials.getValue(cell.quality),
                        position = Float3(cell.x, cell.y, cell.z),
                    )
                }
            }
        }

        CaptureHud(
            state = state,
            mode = mode,
            onBack = onBack,
            onPhoto = viewModel::requestPhoto,
            onFinish = {
                viewModel.finish()
                onFinished()
            },
        )
    }
}

@Composable
private fun CaptureHud(
    state: CaptureUiState,
    mode: ScanMode,
    onBack: () -> Unit,
    onPhoto: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp),
    ) {
        BackBar(
            title = stringResource(
                if (mode == ScanMode.QUICK) {
                    R.string.capture_mode_quick
                } else {
                    R.string.capture_mode_precise
                },
            ),
            onBack = onBack,
        )

        Spacer(Modifier.height(10.dp))
        StatusPill(
            text = stringResource(
                when {
                    state.tracking -> R.string.capture_tracking_active
                    // A stale failure reason must not outrank the live state, and
                    // a cleared one must not be read as "all good" while the pose
                    // is still frozen.
                    state.trackingHint != null -> requireNotNull(state.trackingHint)
                    else -> R.string.capture_searching
                },
            ),
            tone = when {
                state.trackingHint != null -> SignalColor.Fair
                state.tracking -> SignalColor.Excellent
                else -> TextTone.Tertiary
            },
        )

        if (state.depthActive) {
            Spacer(Modifier.height(6.dp))
            StatusPill(
                text = stringResource(R.string.capture_depth_on, state.depthPoints),
                tone = Accent.Bright,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ShutterButton(
                count = state.photoCount,
                flashing = state.photoFlash,
                onClick = onPhoto,
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.capture_stat_signal),
                    value = if (state.link.connected) {
                        stringResource(R.string.unit_dbm, state.link.rssiDbm)
                    } else {
                        stringResource(R.string.value_none)
                    },
                    valueColor = if (state.link.connected) {
                        signalColorFor(state.link.rssiDbm)
                    } else {
                        TextTone.Tertiary
                    },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.capture_stat_points),
                    value = "${state.grid.cellCount}",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(
                        if (state.lastSpeedMbps != null) {
                            R.string.capture_stat_speed
                        } else {
                            R.string.capture_stat_area
                        },
                    ),
                    value = state.lastSpeedMbps?.let {
                        stringResource(R.string.capture_speed_value, it)
                    } ?: stringResource(
                        R.string.capture_area_value,
                        state.grid.coveredAreaSqM,
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.capture_stat_duration),
                    value = formatElapsed(state.elapsedMs),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                // Walls outrank the tracking warning. The jumpy message used to
                // win every time, so the one instruction that would have got
                // walls detected was never actually shown.
                text = when {
                    state.grid.cellCount == 0 ->
                        stringResource(R.string.capture_hint_start)

                    state.trackingJumps >= JUMP_WARNING_THRESHOLD ->
                        stringResource(R.string.capture_hint_jumpy)

                    else -> stringResource(
                        R.string.capture_range,
                        state.grid.weakestRssi ?: 0,
                        state.grid.strongestRssi ?: 0,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
        }

        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = stringResource(R.string.capture_finish),
            onClick = onFinish,
            enabled = state.grid.cellCount > 0,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Shutter for pinning a landmark.
 *
 * Deliberately a one-tap action with no naming prompt: stopping to type mid-walk
 * would break tracking and lengthen the scan. Photos are labelled afterwards on
 * the result screen, where the picture is visible while naming it.
 */
@Composable
private fun ShutterButton(count: Int, flashing: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(
        targetValue = if (flashing) SignalColor.Excellent else Color.White.copy(alpha = 0.75f),
        animationSpec = tween(180),
        label = "shutterRing",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(2.dp, ring, RoundedCornerShape(32.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = stringResource(R.string.capture_take_photo),
                tint = ring,
                modifier = Modifier.size(26.dp),
            )
        }
        if (count > 0) {
            Text(
                text = stringResource(R.string.capture_photo_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
/** Builds one reusable material per signal bucket, tinted by the shared ramp. */
private fun markerMaterials(loader: MaterialLoader): Map<SignalQuality, MaterialInstance> =
    SignalQuality.entries.associateWith { quality ->
        val tint: Color = signalColorFor(representativeRssi(quality))
        loader.createColorInstance(tint, 0f, 0.45f, 0.15f)
    }

/** A dBm value comfortably inside each bucket, used only to pick the marker colour. */
private fun representativeRssi(quality: SignalQuality): Int = when (quality) {
    SignalQuality.EXCELLENT -> -45
    SignalQuality.GOOD -> -55
    SignalQuality.FAIR -> -63
    SignalQuality.WEAK -> -71
    SignalQuality.DEAD -> -85
}

@StringRes
private fun hintFor(reason: TrackingFailureReason): Int = when (reason) {
    TrackingFailureReason.BAD_STATE -> R.string.capture_fail_bad_state
    TrackingFailureReason.INSUFFICIENT_LIGHT -> R.string.capture_fail_dark
    TrackingFailureReason.EXCESSIVE_MOTION -> R.string.capture_fail_motion
    TrackingFailureReason.INSUFFICIENT_FEATURES -> R.string.capture_fail_features
    TrackingFailureReason.CAMERA_UNAVAILABLE -> R.string.capture_fail_camera
    TrackingFailureReason.NONE -> R.string.capture_tracking_active
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
