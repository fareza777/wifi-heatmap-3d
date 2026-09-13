package com.sinyal.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.ar.ArSupport
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

/** Whether a probed capability helps, hurts, or merely informs. */
private enum class Verdict { GOOD, WARN, BAD, NEUTRAL }

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

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
            BackBar(title = stringResource(R.string.diag_title), onBack = onBack)

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.diag_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = TextTone.Primary,
            )
            Text(
                text = stringResource(R.string.diag_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))

            val device = state.device
            val ar = state.ar

            if (device == null || ar == null) {
                LoadingCard(running = state.running)
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.diag_section_device))
                    DiagRow(stringResource(R.string.diag_model), device.model, Verdict.NEUTRAL)
                    DiagRow(
                        stringResource(R.string.diag_android),
                        stringResource(
                            R.string.diag_android_value,
                            device.androidRelease,
                            device.apiLevel,
                        ),
                        Verdict.NEUTRAL,
                    )
                    DiagRow(stringResource(R.string.diag_chipset), device.soc, Verdict.NEUTRAL)
                    DiagRow(
                        stringResource(R.string.diag_ram),
                        stringResource(R.string.diag_ram_value, device.totalRamMb),
                        Verdict.NEUTRAL,
                    )
                }

                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.diag_section_tracking))
                    DiagRow(
                        label = stringResource(R.string.diag_arcore_support),
                        value = stringResource(ar.support.label),
                        verdict = when (ar.support) {
                            ArSupport.SUPPORTED -> Verdict.GOOD
                            ArSupport.NEEDS_INSTALL, ArSupport.NEEDS_UPDATE -> Verdict.WARN
                            ArSupport.UNSUPPORTED -> Verdict.BAD
                            else -> Verdict.NEUTRAL
                        },
                    )
                    DiagRow(
                        label = stringResource(R.string.diag_arcore_version),
                        value = ar.arcoreVersionName
                            ?: stringResource(R.string.diag_not_installed),
                        verdict = if (ar.arcoreVersionName != null) Verdict.GOOD else Verdict.WARN,
                    )
                    DiagRow(
                        label = stringResource(R.string.diag_depth_api),
                        value = when (ar.depthSupported) {
                            true -> stringResource(R.string.diag_supported)
                            false -> stringResource(R.string.diag_unsupported)
                            null -> stringResource(R.string.diag_untested)
                        },
                        verdict = when (ar.depthSupported) {
                            true -> Verdict.GOOD
                            false -> Verdict.WARN
                            null -> Verdict.NEUTRAL
                        },
                    )
                    DiagRow(
                        label = stringResource(R.string.diag_gyro),
                        value = if (device.hasGyroscope) {
                            stringResource(R.string.diag_gyro_present, device.gyroMaxRateHz)
                        } else {
                            stringResource(R.string.diag_gyro_absent)
                        },
                        verdict = if (device.hasGyroscope) Verdict.GOOD else Verdict.BAD,
                    )
                    DiagRow(
                        label = stringResource(R.string.diag_camera_level),
                        value = device.cameraHardwareLevel,
                        verdict = when (device.cameraHardwareLevel) {
                            "FULL", "LEVEL_3" -> Verdict.GOOD
                            "LIMITED" -> Verdict.WARN
                            "LEGACY" -> Verdict.BAD
                            else -> Verdict.NEUTRAL
                        },
                    )
                    if (ar.probeError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = ar.probeError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SignalColor.Fair,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.diag_section_radio))
                    DiagRow(
                        label = stringResource(R.string.diag_band_5),
                        value = stringResource(
                            if (device.supports5GHz) R.string.diag_supported else R.string.diag_no,
                        ),
                        verdict = if (device.supports5GHz) Verdict.GOOD else Verdict.WARN,
                    )
                    DiagRow(
                        label = stringResource(R.string.diag_band_6),
                        value = stringResource(
                            if (device.supports6GHz) R.string.diag_supported else R.string.diag_no,
                        ),
                        verdict = if (device.supports6GHz) Verdict.GOOD else Verdict.NEUTRAL,
                    )
                    DiagRow(
                        label = stringResource(R.string.diag_rtt),
                        value = stringResource(
                            if (device.supportsWifiRtt) R.string.diag_supported else R.string.diag_no,
                        ),
                        verdict = if (device.supportsWifiRtt) Verdict.GOOD else Verdict.NEUTRAL,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.diag_rtt_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Tertiary,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            if (ar?.depthSupported == null && ar?.probeError != null) {
                GradientButton(
                    text = stringResource(R.string.diag_grant_camera),
                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                )
            } else {
                GradientButton(
                    text = stringResource(R.string.diag_rerun),
                    onClick = viewModel::refresh,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LoadingCard(running: Boolean) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (running) R.string.diag_running else R.string.diag_no_result,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTone.Tertiary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DiagRow(label: String, value: String, verdict: Verdict) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            VerdictIcon(verdict)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = TextTone.Primary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun VerdictIcon(verdict: Verdict) {
    val (icon, tint) = when (verdict) {
        Verdict.GOOD -> Icons.Rounded.CheckCircle to SignalColor.Excellent
        Verdict.WARN -> Icons.Rounded.Warning to SignalColor.Fair
        Verdict.BAD -> Icons.Rounded.Error to SignalColor.Dead
        Verdict.NEUTRAL -> Icons.Rounded.RemoveCircle to TextTone.Tertiary
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
}
