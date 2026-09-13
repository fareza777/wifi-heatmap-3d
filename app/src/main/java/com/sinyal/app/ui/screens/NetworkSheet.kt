package com.sinyal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sinyal.app.R
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.WifiSignalIcon
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.JoinRoute
import com.sinyal.app.wifi.NearbyAp
import com.sinyal.app.wifi.SecurityType
import com.sinyal.app.wifi.SignalQuality
import com.sinyal.app.wifi.WifiGeneration
import com.sinyal.app.wifi.estimatedDistanceMeters

/**
 * Everything one access point is broadcasting, plus the way in.
 *
 * The list has to stay scannable, so it carries only what separates one network
 * from another. This is where the rest lives: the frequency arithmetic, the
 * exact capabilities, and the honest limits of each number.
 */
@Composable
fun NetworkSheet(
    ap: NearbyAp,
    vendor: String?,
    route: JoinRoute,
    onJoin: () -> Unit,
    onOpenPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Ink.Surface)
                .border(1.dp, Ink.Stroke, RoundedCornerShape(26.dp))
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
        ) {
            Header(ap, vendor)

            Spacer(Modifier.height(18.dp))
            SheetSection(stringResource(R.string.sheet_section_radio))
            SheetRow(stringResource(R.string.details_band), ap.band.label)
            SheetRow(stringResource(R.string.details_channel), "${ap.channel}")
            SheetRow(
                stringResource(R.string.details_frequency),
                stringResource(R.string.sheet_frequency, ap.frequencyMhz),
            )
            SheetRow(
                stringResource(R.string.details_width),
                stringResource(R.string.networks_width, ap.channelWidthMhz),
            )
            SheetRow(
                stringResource(R.string.sheet_span),
                stringResource(
                    R.string.sheet_span_value,
                    ap.frequencyMhz - ap.channelWidthMhz / 2,
                    ap.frequencyMhz + ap.channelWidthMhz / 2,
                ),
            )
            SheetRow(
                stringResource(R.string.details_standard),
                if (ap.generation == WifiGeneration.UNKNOWN) {
                    stringResource(R.string.sheet_standard_unknown)
                } else {
                    ap.generation.label
                },
            )

            Spacer(Modifier.height(16.dp))
            SheetSection(stringResource(R.string.sheet_section_signal))
            SheetRow(
                label = stringResource(R.string.details_signal),
                value = stringResource(R.string.unit_dbm, ap.rssiDbm),
                valueColor = signalColorFor(ap.rssiDbm),
            )
            SheetRow(
                stringResource(R.string.details_quality),
                stringResource(ap.quality.label),
            )
            SheetRow(
                stringResource(R.string.sheet_strength_percent),
                stringResource(
                    R.string.sheet_percent,
                    (SignalQuality.normalize(ap.rssiDbm) * 100).toInt(),
                ),
            )
            SheetRow(
                stringResource(R.string.sheet_distance),
                stringResource(R.string.sheet_distance_value, estimatedDistanceMeters(ap.rssiDbm)),
            )
            Note(stringResource(R.string.sheet_distance_note))

            Spacer(Modifier.height(16.dp))
            SheetSection(stringResource(R.string.sheet_section_security))
            SheetRow(
                label = stringResource(R.string.details_security),
                value = stringResource(ap.security.label),
                valueColor = when {
                    ap.security.isUnencrypted -> SignalColor.Weak
                    ap.security.joinableWithoutPassword -> SignalColor.Fair
                    else -> TextTone.Primary
                },
            )
            SheetRow(
                stringResource(R.string.sheet_password_needed),
                stringResource(
                    if (ap.security.joinableWithoutPassword) {
                        R.string.details_no
                    } else {
                        R.string.details_yes
                    },
                ),
            )
            SheetRow(
                stringResource(R.string.sheet_encrypted),
                stringResource(
                    if (ap.security.isUnencrypted) R.string.details_no else R.string.details_yes,
                ),
            )
            SheetRow(
                label = stringResource(R.string.sheet_wps),
                value = stringResource(
                    if (ap.supportsWps) R.string.details_yes else R.string.details_no,
                ),
                valueColor = if (ap.supportsWps) SignalColor.Fair else TextTone.Primary,
            )
            SheetRow(
                stringResource(R.string.sheet_hidden),
                stringResource(if (ap.isHidden) R.string.details_yes else R.string.details_no),
            )
            SheetRow(
                stringResource(R.string.sheet_ranging),
                stringResource(
                    if (ap.supportsRanging) R.string.details_yes else R.string.details_no,
                ),
            )

            if (ap.security.joinableWithoutPassword) {
                Spacer(Modifier.height(14.dp))
                Note(
                    text = stringResource(
                        if (ap.security == SecurityType.OWE) {
                            R.string.networks_open_encrypted
                        } else {
                            R.string.networks_open_unencrypted
                        },
                    ),
                    tint = SignalColor.Fair,
                )
            }

            Spacer(Modifier.height(20.dp))
            if (ap.isCurrent) {
                ConnectedBadge()
            } else {
                GradientButton(
                    text = stringResource(
                        if (route == JoinRoute.SUGGEST) {
                            R.string.sheet_join
                        } else {
                            R.string.sheet_open_picker
                        },
                    ),
                    onClick = if (route == JoinRoute.SUGGEST) onJoin else onOpenPicker,
                )
                Spacer(Modifier.height(8.dp))
                Note(
                    stringResource(
                        if (route == JoinRoute.SUGGEST) {
                            R.string.sheet_join_note
                        } else {
                            R.string.sheet_picker_note
                        },
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))
            CloseRow(onDismiss)
        }
    }
}

@Composable
private fun Header(ap: NearbyAp, vendor: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WifiSignalIcon(rssiDbm = ap.rssiDbm, size = 38.dp)
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = ap.ssid,
                style = MaterialTheme.typography.headlineSmall,
                color = TextTone.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = vendor?.let { stringResource(R.string.networks_vendor_line, it, ap.bssid) }
                    ?: ap.bssid,
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
        }
    }
}

@Composable
private fun ConnectedBadge() {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Accent.Glow)
            .border(1.dp, Accent.Base, shape)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sheet_connected),
            style = MaterialTheme.typography.labelLarge,
            color = Accent.Bright,
        )
    }
}

@Composable
private fun CloseRow(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sheet_close),
            style = MaterialTheme.typography.labelLarge,
            color = TextTone.Secondary,
        )
    }
}

@Composable
private fun SheetSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTone.Tertiary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SheetRow(
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
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Note(text: String, tint: Color = TextTone.Tertiary) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = tint,
        modifier = Modifier.padding(top = 8.dp),
    )
}
