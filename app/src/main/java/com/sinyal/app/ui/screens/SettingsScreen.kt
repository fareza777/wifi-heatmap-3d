package com.sinyal.app.ui.screens

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sinyal.app.BuildConfig
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.MenuTile
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.AccentPalette
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.ui.theme.ThemeMode
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.ui.text.style.TextOverflow
import com.sinyal.app.core.AppLanguage

/**
 * Everything that used to clutter the home screen.
 *
 * Appearance sits at the top because it is the only thing here people change
 * more than once; the rest are visited a handful of times in the life of an
 * install and do not deserve permanent space on the main screen.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accent: AccentPalette,
    onAccentChange: (AccentPalette) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    adsRemoved: Boolean,
    removeAdsPrice: String?,
    onRemoveAds: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    billingMessage: String? = null,
    billingBusy: Boolean = false,
    onRestorePurchase: () -> Unit = {},
    privacyOptionsRequired: Boolean = false,
    privacyMessage: String? = null,
    onPrivacyOptions: () -> Unit = {},
) {
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
            BackBar(title = stringResource(R.string.settings_title), onBack = onBack)

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_section_appearance))
            Spacer(Modifier.height(10.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeCard(
                        mode = ThemeMode.LIGHT,
                        icon = Icons.Rounded.LightMode,
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeCard(
                        mode = ThemeMode.DARK,
                        icon = Icons.Rounded.DarkMode,
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeCard(
                        mode = ThemeMode.SYSTEM,
                        icon = Icons.Rounded.PhoneAndroid,
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPalette.entries.forEach { option ->
                        AccentSwatch(
                            option = option,
                            selected = option == accent,
                            onClick = { onAccentChange(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(accent.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.language_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { option ->
                        LanguagePill(
                            label = stringResource(option.label),
                            selected = option == language,
                            onClick = { onLanguageChange(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.settings_section_device))
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuTile(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = stringResource(R.string.settings_diagnostics_sub),
                    onClick = onOpenDiagnostics,
                )
                MenuTile(
                    icon = Icons.Rounded.School,
                    title = stringResource(R.string.settings_guide),
                    subtitle = stringResource(R.string.settings_guide_sub),
                    onClick = onOpenGuide,
                )
                MenuTile(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_sub),
                    onClick = onOpenAbout,
                )
                if (!adsRemoved) {
                    MenuTile(
                        icon = Icons.Rounded.WorkspacePremium,
                        title = stringResource(R.string.settings_remove_ads),
                        subtitle = removeAdsPrice?.let {
                            stringResource(R.string.settings_remove_ads_priced, it)
                        } ?: stringResource(R.string.billing_check_price),
                        onClick = { if (!billingBusy) onRemoveAds() },
                    )
                } else {
                    Text(stringResource(R.string.billing_removed), color = Accent.Bright,
                        style = MaterialTheme.typography.bodyMedium)
                }
                MenuTile(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.billing_restore),
                    subtitle = stringResource(if (billingBusy) R.string.billing_restoring else R.string.billing_restore_subtitle),
                    onClick = { if (!billingBusy) onRestorePurchase() },
                )
                billingMessage?.let {
                    Text(it, color = TextTone.Secondary, style = MaterialTheme.typography.bodySmall)
                }
                if (privacyOptionsRequired) {
                    MenuTile(
                        icon = Icons.Rounded.PrivacyTip,
                        title = stringResource(R.string.privacy_options),
                        subtitle = stringResource(R.string.privacy_options_subtitle),
                        onClick = onPrivacyOptions,
                    )
                }
                privacyMessage?.let {
                    Text(it, color = TextTone.Secondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.settings_version,
                    stringResource(R.string.app_name_short),
                    BuildConfig.VERSION_NAME,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * One language choice.
 *
 * Deliberately not a [ThemeCard]: a theme can be shown as a picture, a language
 * can only be shown as its own name — and the name has to stay readable, so
 * these are wide pills rather than square tiles.
 */
@Composable
private fun LanguagePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Accent.Glow else Ink.Raised)
            .border(1.dp, if (selected) Accent.Base else Ink.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Accent.Bright else TextTone.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTone.Tertiary,
    )
}

/** A card per mode, so the choice is a picture rather than a word. */
@Composable
private fun ThemeCard(
    mode: ThemeMode,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        targetValue = if (selected) Accent.Base else Ink.Stroke,
        animationSpec = tween(180),
        label = "themeCardBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "themeCardScale",
    )
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (selected) Accent.Glow else Ink.Raised)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Accent.Bright else TextTone.Tertiary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(mode.label),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Accent.Bright else TextTone.Secondary,
        )
    }
}

@Composable
private fun AccentSwatch(
    option: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "swatchScale",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(option.swatch)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) Ink.Base else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(option.label),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
