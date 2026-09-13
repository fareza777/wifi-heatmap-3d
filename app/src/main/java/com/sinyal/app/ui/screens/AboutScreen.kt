package com.sinyal.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import com.sinyal.app.BuildConfig
import com.sinyal.app.R
import com.sinyal.app.core.ReviewLauncher
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.MenuTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import androidx.compose.ui.res.stringResource

/**
 * What the app is, who made it, and what it does with your data.
 *
 * A short data-use explanation sits beside the complete privacy policy.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

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
            BackBar(title = stringResource(R.string.about_title), onBack = onBack)

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Accent.Glow),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_art),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = stringResource(R.string.app_name_short),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextTone.Primary,
                    )
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Tertiary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.about_pitch),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTone.Secondary,
                )
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.about_privacy_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Secondary,
                )
            }

            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuTile(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.privacy_policy),
                    subtitle = stringResource(R.string.privacy_policy_subtitle),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://fareza777.github.io/wifi-heatmap-3d/privacy-policy.html")))
                    },
                )
                MenuTile(
                    icon = Icons.Rounded.StarRate,
                    title = stringResource(R.string.about_rate),
                    subtitle = stringResource(R.string.about_rate_sub),
                    onClick = { activity?.let(ReviewLauncher::request) },
                )
                MenuTile(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.about_share),
                    subtitle = stringResource(R.string.about_share_sub),
                    onClick = { activity?.let(ReviewLauncher::shareApp) },
                )
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.about_credits_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Credit("ARCore", stringResource(R.string.about_credit_arcore))
                Credit("Filament & SceneView", stringResource(R.string.about_credit_filament))
                Credit("Jetpack Compose", stringResource(R.string.about_credit_compose))
                Credit("Plus Jakarta Sans", stringResource(R.string.about_credit_font))
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Credit(name: String, role: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
    }
}
