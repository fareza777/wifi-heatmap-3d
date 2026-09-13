package com.sinyal.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone

/**
 * Selectable option row. Selection is carried by border, fill and a filled radio
 * at once — colour alone would not survive a bright room or a colour-blind user.
 */
@Composable
fun OptionCard(
    title: String,
    description: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val borderColor by animateColorAsState(
        targetValue = if (selected) Accent.Base else Ink.Stroke,
        animationSpec = tween(220),
        label = "optionBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.6.dp else 1.dp,
        animationSpec = tween(220),
        label = "optionBorderWidth",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "optionScale",
    )
    val fill = if (selected) {
        Brush.verticalGradient(listOf(Color(0xFF1D1A3A), Color(0xFF14131F)))
    } else {
        Brush.verticalGradient(listOf(Ink.Surface, Ink.Raised))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(fill)
            .border(borderWidth, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            RadioDot(selected = selected)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextTone.Primary,
                    )
                    Badge(text = badge, highlighted = selected)
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (footnote != null) {
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accent.Bright,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) Accent.Base else Ink.StrokeStrong,
        animationSpec = tween(220),
        label = "radioRing",
    )
    val innerScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "radioInner",
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(2.dp, ringColor, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .scale(innerScale)
                .clip(RoundedCornerShape(6.dp))
                .background(Accent.Base),
        )
    }
}

@Composable
private fun Badge(text: String, highlighted: Boolean) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (highlighted) Accent.Glow else Ink.SurfaceHigh)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) Accent.Bright else TextTone.Secondary,
        )
    }
}
