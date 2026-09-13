package com.sinyal.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.sinyal.app.ads.AdConfig
import com.sinyal.app.ads.LocalAdsEnabled

/** SDK initialization, consent and paid entitlement are all enforced by the activity gate. */
@Composable
fun BannerAd(adsRemoved: Boolean, modifier: Modifier = Modifier) {
    if (adsRemoved || !LocalAdsEnabled.current) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
      key(widthDp) {
        val adView = remember(context, widthDp) {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                adUnitId = AdConfig.bannerUnitId
            }
        }
        DisposableEffect(adView, lifecycle) {
            adView.loadAd(AdRequest.Builder().build())
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) adView.pause()
            onDispose {
                lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }
        AndroidView(factory = { adView })
      }
    }
}
