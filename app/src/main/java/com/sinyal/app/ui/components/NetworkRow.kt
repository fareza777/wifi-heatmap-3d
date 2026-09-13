package com.sinyal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.NearbyAp
import com.sinyal.app.wifi.SignalQuality
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

/** One neighbouring access point. */
@Composable
fun NetworkRow(ap: NearbyAp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrengthBars(
            fraction = SignalQuality.normalize(ap.rssiDbm),
            modifier = Modifier.size(width = 18.dp, height = 18.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ap.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ap.isCurrent) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Accent.Glow)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.row_connected),
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent.Bright,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.row_band_channel, ap.band.label, ap.channelLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
        }
        Text(
            text = "${ap.rssiDbm}",
            style = MaterialTheme.typography.titleMedium,
            color = signalColorFor(ap.rssiDbm),
        )
    }
}

private fun NearbyAp.channelLabel(): String =
    if (channel > 0) channel.toString() else "—"

/** Four ascending bars, filled proportionally to signal strength. */
@Composable
private fun StrengthBars(fraction: Float, modifier: Modifier = Modifier) {
    val filled = (fraction * 4f).toInt().coerceIn(0, 4)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((6 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < filled) signalColorAt(fraction) else Ink.StrokeStrong,
                    ),
            )
        }
    }
}
