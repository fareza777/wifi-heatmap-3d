package com.sinyal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.MobileAds
import com.sinyal.app.ads.InterstitialController
import com.sinyal.app.billing.BillingManager
import com.sinyal.app.core.AppPrefs
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect as ComposeDisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.sinyal.app.ui.nav.SinyalNavHost
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.sinyal.app.ui.theme.DarkPalette
import com.sinyal.app.ui.theme.LightPalette
import com.sinyal.app.ui.theme.LocalPalette
import com.sinyal.app.ui.theme.AccentPalette
import com.sinyal.app.ui.theme.ThemeMode
import com.sinyal.app.ui.theme.SinyalTheme
import android.content.Context
import com.sinyal.app.core.AppLanguage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.sinyal.app.ads.ConsentManager
import com.sinyal.app.ads.LocalAdsEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SPLASH_EXIT_MS = 340L
private const val SPLASH_ICON_SCALE = 1.25f

class MainActivity : ComponentActivity() {

    /**
     * The one hook that runs before any resource is resolved.
     *
     * A language chosen in Settings has to be in place here; applying it later
     * would leave the first composition speaking the phone's language and then
     * visibly swap.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase, AppPrefs(newBase).language))
    }

    private lateinit var billing: BillingManager
    private lateinit var interstitials: InterstitialController
    private lateinit var consent: ConsentManager
    private val adsEnabled = MutableStateFlow(false)
    private var adsInitializationStarted = false
    private var adsInitialized = false

    override fun onResume() {
        super.onResume()
        if (::billing.isInitialized) billing.connect()
    }

    override fun onDestroy() {
        adsEnabled.value = false
        if (::consent.isInitialized) consent.release()
        if (::interstitials.isInitialized) interstitials.release()
        if (::billing.isInitialized) billing.release()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash otherwise cuts to the app in a single frame; easing the
        // icon out makes the handover read as one motion rather than a flicker.
        installSplashScreen().setOnExitAnimationListener { provider ->
            provider.iconView.animate()
                .alpha(0f)
                .scaleX(SPLASH_ICON_SCALE)
                .scaleY(SPLASH_ICON_SCALE)
                .setDuration(SPLASH_EXIT_MS)
                .withEndAction { provider.remove() }
                .start()
        }

        // The app is dark-only, so pin the bars to the dark treatment rather than
        // letting the system theme flip icon colours against our background.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = AppPrefs(this)

        interstitials = InterstitialController(this)
        consent = ConsentManager(this)
        billing = BillingManager(this, cachedAdsRemoved = prefs.adsRemoved) { owned ->
            prefs.adsRemoved = owned
            if (owned) {
                adsEnabled.value = false
                interstitials.setEnabled(false)
            }
        }
        lifecycleScope.launch {
            combine(billing.state, consent.state) { purchase, privacy ->
                !purchase.adsRemoved && privacy.canRequestAds
            }.distinctUntilChanged().collect { eligible ->
                adsEnabled.value = eligible && adsInitialized
                interstitials.setEnabled(adsEnabled.value)
                if (eligible && !adsInitializationStarted) {
                    adsInitializationStarted = true
                    lifecycleScope.launch(Dispatchers.IO) {
                        MobileAds.initialize(applicationContext) {
                            lifecycleScope.launch {
                                if (!isDestroyed) {
                                    adsInitialized = true
                                    val permitted = !billing.state.value.adsRemoved && consent.state.value.canRequestAds
                                    adsEnabled.value = permitted
                                    interstitials.setEnabled(permitted)
                                }
                            }
                        }
                    }
                }
            }
        }
        consent.gather(this)
        setContent {
            var mode by remember { mutableStateOf(prefs.themeMode) }
            var accent by remember { mutableStateOf(prefs.accent) }
            val billingState by billing.state.collectAsState()
            val consentState by consent.state.collectAsState()
            val canShowAds by adsEnabled.collectAsState()
            val adsRemoved = billingState.adsRemoved
            val systemDark = isSystemInDarkTheme()
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            // Status and navigation bar icons have to flip with the theme, and
            // enableEdgeToEdge is the only thing that controls them.
            DisposableEffect(dark) {
                val ground = (if (dark) DarkPalette else LightPalette).base.toArgb()
                val style = if (dark) {
                    SystemBarStyle.dark(ground)
                } else {
                    SystemBarStyle.light(ground, ground)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            SinyalTheme(mode = mode, accent = accent) {
              CompositionLocalProvider(LocalAdsEnabled provides canShowAds) {
                SinyalNavHost(
                    startWithOnboarding = !prefs.hasSeenOnboarding,
                    onOnboardingComplete = { prefs.hasSeenOnboarding = true },
                    themeMode = mode,
                    accent = accent,
                    onAccentChange = { chosen ->
                        prefs.accent = chosen
                        accent = chosen
                    },
                    language = prefs.language,
                    // Locales are bound to the Activity's resources, so the only
                    // honest way to change one is to build the Activity again.
                    onLanguageChange = { chosen ->
                        if (chosen != prefs.language) {
                            prefs.language = chosen
                            recreate()
                        }
                    },
                    adsRemoved = adsRemoved,
                    removeAdsPrice = billingState.price,
                    onRemoveAds = { billing.purchase(this@MainActivity) },
                    billingMessage = billingState.message,
                    billingBusy = billingState.busy || billingState.restoring,
                    onRestorePurchase = { billing.restore() },
                    privacyOptionsRequired = consentState.privacyOptionsRequired,
                    privacyMessage = consentState.error,
                    onPrivacyOptions = { consent.showPrivacyOptions(this@MainActivity) },
                    onScanCompleted = {
                        interstitials.onScanCompleted()
                    },
                    onResultsClosed = { allowAd ->
                        interstitials.onResultsClosed(this@MainActivity, allowAd)
                    },
                    onThemeModeChange = { chosen ->
                        prefs.themeMode = chosen
                        mode = chosen
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPalette.current.base),
                )
              }
            }
        }
    }
}
