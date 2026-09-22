package com.sinyal.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone

/**
 * A menu entry with room to say what it does.
 *
 * Replaces a stack of bare text buttons: a label alone forces the reader to
 * guess what "Analisa Spektrum" will show them, while an icon plus one line of
 * description answers it before the tap. The press state moves the whole tile
 * rather than flashing a ripple, so the target feels physical.
 */
@Composable
fun MenuTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "tileScale",
    )
    val border by animateColorAsState(
        targetValue = if (pressed) Accent.Base else Ink.Stroke,
        animationSpec = tween(160),
        label = "tileBorder",
    )

    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Ink.SurfaceHigh, Ink.Surface)))
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Accent.Glow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Accent.Bright,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextTone.Primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
        }

        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = TextTone.Tertiary,
        )
    }
}

/**
 * One tool in the home grid: an icon and a single word.
 *
 * No subtitle. Six of these have to sit on one screen together with the live
 * reading and the scan button, and a second line of grey text under every one
 * of them is what turned the home screen into something to scroll through.
 * The word has to be enough; if it is not, the tool is named wrong.
 */
@Composable
fun ToolTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "toolScale",
    )
    val border by animateColorAsState(
        targetValue = if (pressed) Accent.Base else Ink.Stroke,
        animationSpec = tween(160),
        label = "toolBorder",
    )

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Ink.SurfaceHigh, Ink.Surface)))
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Accent.Glow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Accent.Bright,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextTone.Primary,
            maxLines = 1,
        )
    }
}
