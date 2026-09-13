package com.sinyal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GhostButton
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.OptionCard
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

/** How thoroughly the walk-through samples the home. */
enum class ScanMode {
    /** One reading per room. Fast, room-level resolution only. */
    QUICK,

    /** Continuous sampling while walking. Resolves weak corners within a room. */
    PRECISE,
}

/** Opt-in, because every reading it takes is a real download. */
@Composable
private fun SpeedToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) Accent.Glow else Ink.Raised)
            .border(1.dp, if (enabled) Accent.Base else Ink.Stroke, shape)
            .clickable { onChange(!enabled) }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (enabled) Accent.Base else Ink.Surface)
                    .border(
                        1.dp,
                        if (enabled) Accent.Base else Ink.StrokeStrong,
                        RoundedCornerShape(6.dp),
                    ),
            )
            Text(
                text = stringResource(R.string.scanmode_measure_speed),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Accent.Bright else TextTone.Primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = stringResource(R.string.scanmode_measure_speed_note),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun ScanModeScreen(
    onBack: () -> Unit,
    onStart: (ScanMode, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var mode by remember { mutableStateOf(ScanMode.PRECISE) }
    var measureSpeed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
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
        ) {
            BackBar(title = stringResource(R.string.scanmode_title), onBack = onBack)

            Spacer(Modifier.height(26.dp))
            Text(
                text = stringResource(R.string.scanmode_kicker),
                style = MaterialTheme.typography.labelSmall,
                color = Accent.Bright,
            )
            Text(
                text = stringResource(R.string.scanmode_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = TextTone.Primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.scanmode_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(Modifier.height(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard(
                    title = stringResource(R.string.scanmode_quick),
                    description = stringResource(R.string.scanmode_quick_desc),
                    badge = stringResource(R.string.scanmode_quick_badge),
                    selected = mode == ScanMode.QUICK,
                    onClick = { mode = ScanMode.QUICK },
                )
                OptionCard(
                    title = stringResource(R.string.scanmode_precise),
                    description = stringResource(R.string.scanmode_precise_desc),
                    badge = stringResource(R.string.scanmode_precise_badge),
                    selected = mode == ScanMode.PRECISE,
                    onClick = { mode = ScanMode.PRECISE },
                    footnote = stringResource(R.string.scanmode_footnote),
                )
            }

            Spacer(Modifier.height(28.dp))
            SpeedToggle(enabled = measureSpeed, onChange = { measureSpeed = it })

            Text(
                text = stringResource(R.string.arcore_privacy_notice),
                style = MaterialTheme.typography.bodySmall,
                color = TextTone.Secondary,
                modifier = Modifier.padding(top = 16.dp).clickable {
                    uriHandler.openUri("https://policies.google.com/privacy")
                },
            )

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(R.string.scanmode_start),
                onClick = { onStart(mode, measureSpeed) },
            )
            Spacer(Modifier.height(4.dp))
            GhostButton(text = stringResource(R.string.scanmode_later), onClick = onBack)
            Spacer(Modifier.height(28.dp))
        }
    }
}
