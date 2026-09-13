package com.sinyal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

/** Top row with a tappable back affordance and a quiet screen title. */
@Composable
fun BackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.Surface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = TextTone.Secondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextTone.Secondary,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
