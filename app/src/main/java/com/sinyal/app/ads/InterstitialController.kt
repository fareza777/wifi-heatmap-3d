package com.sinyal.app.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** Main-thread controller. Loading never schedules presentation or blocks navigation. */
class InterstitialController(context: Context) {
    private val appContext = context.applicationContext
    private val policy = AdBreakPolicy(SystemClock.elapsedRealtime())
    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var enabled = false
    private var released = false
    private var generation = 0

    fun setEnabled(value: Boolean) {
        enabled = value && !released
        if (!enabled) {
            generation++
            ad?.fullScreenContentCallback = null
            ad = null
            loading = false
        } else preload()
    }

    fun preload() {
        if (!enabled || released || loading) return
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt < MAX_AD_AGE_MS) return
        ad = null
        loading = true
        val requestGeneration = generation
        InterstitialAd.load(appContext, AdConfig.interstitialUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    if (requestGeneration != generation || released || !enabled) return
                    ad = loaded
                    loadedAt = SystemClock.elapsedRealtime()
                    loading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (requestGeneration != generation || released) return
                    ad = null
                    loading = false
                }
            },
        )
    }

    fun onScanCompleted() { policy.scanCompleted() }

    /** Only invoked after leaving a fresh result. Rescan consumes the break without an ad. */
    fun onResultsClosed(activity: Activity, allowAd: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val ready = ad?.takeIf { now - loadedAt < MAX_AD_AGE_MS }
        val show = policy.resultsClosed(now,
            enabled && !released && !activity.isFinishing && !activity.isDestroyed,
            ready != null, allowAd)
        if (!show || ready == null) {
            preload()
            return
        }
        ad = null
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { preload() }
        }
        ready.show(activity)
    }

    fun release() {
        released = true
        setEnabled(false)
    }

    private companion object { const val MAX_AD_AGE_MS = 55 * 60 * 1000L }
}
