package com.sinyal.app.ui.screens.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.components.GhostButton
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R

private const val PAGE_COUNT = 4

/**
 * First-run explanation.
 *
 * Three pages, in the order a newcomer's questions actually arrive: what this
 * thing does, what it needs from me and why, and how do I not do it badly. The
 * permission page asks for the grants itself rather than deferring to a naked
 * system dialog later, so the reason is on screen at the moment of the ask.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Bumped after a grant so the checklist below recomposes with the new state.
    var permissionEpoch by remember { mutableIntStateOf(0) }

    fun granted(permission: String): Boolean =
        permissionEpoch.let {
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionEpoch++
        scope.launch { pagerState.animateScrollToPage(2) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Accent.Base.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name_short),
                style = MaterialTheme.typography.labelSmall,
                color = Accent.Bright,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> IntroPage()
                    1 -> PermissionPage(
                        locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                        cameraGranted = granted(Manifest.permission.CAMERA),
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.CAMERA,
                                ),
                            )
                        },
                    )
                    2 -> TipsPage()
                    else -> ResultPreviewPage()
                }
            }

            PageDots(current = pagerState.currentPage)

            Spacer(Modifier.height(18.dp))
            if (pagerState.currentPage == PAGE_COUNT - 1) {
                GradientButton(
                    text = stringResource(R.string.onboarding_start),
                    onClick = onFinished,
                )
            } else {
                GradientButton(
                    text = stringResource(R.string.onboarding_next),
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                )
            }
            GhostButton(text = stringResource(R.string.onboarding_skip), onClick = onFinished)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun IntroPage() {
    PageScaffold(
        art = { RadiatingArt() },
        title = stringResource(R.string.onboarding_1_title),
        body = stringResource(R.string.onboarding_1_body),
    )
}

@Composable
private fun PermissionPage(
    locationGranted: Boolean,
    cameraGranted: Boolean,
    onGrant: () -> Unit,
) {
    val allGranted = locationGranted && cameraGranted
    PageScaffold(
        art = { PermissionArt() },
        title = stringResource(R.string.onboarding_2_title),
        body = stringResource(R.string.onboarding_2_body),
        action = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PermissionStatus(
                    stringResource(R.string.onboarding_perm_location),
                    locationGranted,
                )
                PermissionStatus(
                    stringResource(R.string.onboarding_perm_camera),
                    cameraGranted,
                )
                Spacer(Modifier.height(8.dp))
                if (!allGranted) {
                    GhostButton(
                        text = stringResource(R.string.onboarding_grant_now),
                        onClick = onGrant,
                    )
                }
            }
        },
    )
}

/** Shows what has actually been granted, rather than asking twice. */
@Composable
private fun PermissionStatus(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (granted) SignalColor.Excellent else Ink.StrokeStrong),
        )
        Text(
            text = stringResource(
                if (granted) {
                    R.string.onboarding_perm_granted
                } else {
                    R.string.onboarding_perm_missing
                },
                label,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) TextTone.Secondary else TextTone.Tertiary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Sets expectations for the result, so the 3D view is not a surprise. */
@Composable
private fun ResultPreviewPage() {
    PageScaffold(
        art = { ResultPreviewArt() },
        title = stringResource(R.string.onboarding_3_title),
        body = stringResource(R.string.onboarding_3_body),
    )
}

@Composable
private fun TipsPage() {
    PageScaffold(
        art = { WalkPathArt() },
        title = stringResource(R.string.onboarding_4_title),
        body = stringResource(R.string.onboarding_4_body),
    )
}

@Composable
private fun PageScaffold(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        art()
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextTone.Primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = TextTone.Secondary,
            textAlign = TextAlign.Start,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Width-morphing dots: the active page reads as a bar, not a slightly bigger dot. */
@Composable
private fun PageDots(current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "dotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (active) Accent.Base else Ink.StrokeStrong,
                animationSpec = tween(220),
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
