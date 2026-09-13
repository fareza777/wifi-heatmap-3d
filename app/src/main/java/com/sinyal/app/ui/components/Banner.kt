package com.sinyal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.TextTone

/** Inline notice with an optional single action. */
@Composable
fun Banner(
    icon: ImageVector,
    title: String,
    message: String,
    tone: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.30f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextTone.Primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent.Bright,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = onAction),
                )
            }
        }
    }
}
