package com.sinyal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.TextTone

/** Small status chip with a coloured dot, legible over the camera feed. */
@Composable
fun StatusPill(text: String, tone: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, tone.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tone),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextTone.Primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
