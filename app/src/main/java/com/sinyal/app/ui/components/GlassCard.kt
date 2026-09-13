package com.sinyal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Ink

/**
 * The app's one surface primitive: a raised panel with a hairline edge and a
 * top-down sheen, so stacked cards stay separable on a near-black ground.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Ink.SurfaceHigh, Ink.Surface),
                )
            )
            .border(1.dp, Ink.Stroke, shape)
            .padding(contentPadding),
        content = content,
    )
}
