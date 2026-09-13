package com.sinyal.app.ads

import androidx.compose.runtime.staticCompositionLocalOf
import com.sinyal.app.BuildConfig

/** Deny by default, including previews and any screen outside the activity gate. */
val LocalAdsEnabled = staticCompositionLocalOf { false }

object AdConfig {
    val bannerUnitId: String get() = BuildConfig.ADMOB_BANNER_ID
    val interstitialUnitId: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID
}
