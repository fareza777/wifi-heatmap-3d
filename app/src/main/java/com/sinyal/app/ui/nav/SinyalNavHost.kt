package com.sinyal.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sinyal.app.ui.screens.CaptureScreen
import com.sinyal.app.ui.screens.AnalysisScreen
import com.sinyal.app.ui.screens.ChannelsScreen
import com.sinyal.app.ui.screens.RttScreen
import com.sinyal.app.ui.screens.DiagnosticsScreen
import com.sinyal.app.ui.screens.AboutScreen
import com.sinyal.app.ui.screens.CompareScreen
import com.sinyal.app.ui.screens.CoverageScreen
import com.sinyal.app.ui.screens.DetailsScreen
import com.sinyal.app.ui.screens.PingScreen
import com.sinyal.app.ui.screens.HistoryScreen
import com.sinyal.app.ui.theme.ThemeMode
import com.sinyal.app.ui.screens.HomeScreen
import com.sinyal.app.ui.screens.NetworksScreen
import com.sinyal.app.ui.screens.LanScanScreen
import com.sinyal.app.ui.screens.SecurityScreen
import com.sinyal.app.ui.screens.SettingsScreen
import com.sinyal.app.ui.screens.SignalGraphScreen
import com.sinyal.app.ui.screens.SpeedTestScreen
import com.sinyal.app.ui.theme.AccentPalette
import com.sinyal.app.ui.screens.RoomScreen
import com.sinyal.app.ui.screens.ScanMode
import com.sinyal.app.ui.screens.ScanModeScreen
import com.sinyal.app.ui.screens.onboarding.OnboardingScreen
import com.sinyal.app.core.AppLanguage

private object Route {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCAN_MODE = "scanMode"
    const val DIAGNOSTICS = "diagnostics"
    const val ANALYSIS = "analysis"
    const val NETWORKS = "networks"
    const val COMPARE = "compare"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val SPEED = "speed"
    const val DEVICES = "devices"
    const val SECURITY = "security"
    const val DETAILS = "details"
    const val COVERAGE = "coverage"
    const val PING = "ping"
    const val GRAPH = "graph"
    const val CHANNELS = "channels"
    const val RTT = "rtt"
    const val HISTORY = "history"
    const val GUIDE = "guide"
    const val ROOM = "room"
    const val CAPTURE = "capture"
    const val CAPTURE_ARG_MODE = "mode"
    const val CAPTURE_ARG_SPEED = "speed"
    const val CAPTURE_PATTERN = "$CAPTURE/{$CAPTURE_ARG_MODE}/{$CAPTURE_ARG_SPEED}"

    fun capture(mode: ScanMode, measureSpeed: Boolean) =
        "$CAPTURE/${mode.name}/$measureSpeed"
}

@Composable
fun SinyalNavHost(
    startWithOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accent: AccentPalette,
    onAccentChange: (AccentPalette) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    adsRemoved: Boolean,
    removeAdsPrice: String?,
    onRemoveAds: () -> Unit,
    onScanCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    billingMessage: String? = null,
    billingBusy: Boolean = false,
    onRestorePurchase: () -> Unit = {},
    privacyOptionsRequired: Boolean = false,
    privacyMessage: String? = null,
    onPrivacyOptions: () -> Unit = {},
    onResultsClosed: (Boolean) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) Route.ONBOARDING else Route.HOME,
        modifier = modifier,
    ) {
        composable(Route.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingComplete()
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.HOME) {
            HomeScreen(
                onStartScan = { navController.navigate(Route.SCAN_MODE) },
                onOpenNetworks = { navController.navigate(Route.NETWORKS) },
                onOpenAnalysis = { navController.navigate(Route.ANALYSIS) },
                onOpenHistory = { navController.navigate(Route.HISTORY) },
                onOpenCompare = { navController.navigate(Route.COMPARE) },
                onOpenSpeedTest = { navController.navigate(Route.SPEED) },
                onOpenDevices = { navController.navigate(Route.DEVICES) },
                onOpenSecurity = { navController.navigate(Route.SECURITY) },
                onOpenChannels = { navController.navigate(Route.CHANNELS) },
                onOpenRtt = { navController.navigate(Route.RTT) },
                onOpenGraph = { navController.navigate(Route.GRAPH) },
                onOpenCoverage = { navController.navigate(Route.COVERAGE) },
                onOpenSettings = { navController.navigate(Route.SETTINGS) },
                adsRemoved = adsRemoved,
            )
        }

        composable(Route.SPEED) {
            SpeedTestScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.DEVICES) {
            LanScanScreen(
                onBack = { navController.popBackStack() },
                onOpenPing = { navController.navigate(Route.PING) },
            )
        }

        composable(Route.SECURITY) {
            SecurityScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.CHANNELS) {
            ChannelsScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.RTT) {
            RttScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.DETAILS) {
            DetailsScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.COVERAGE) {
            CoverageScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.PING) {
            PingScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.GRAPH) {
            SignalGraphScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.SETTINGS) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                accent = accent,
                onAccentChange = onAccentChange,
                language = language,
                onLanguageChange = onLanguageChange,
                adsRemoved = adsRemoved,
                removeAdsPrice = removeAdsPrice,
                onRemoveAds = onRemoveAds,
                billingMessage = billingMessage,
                billingBusy = billingBusy,
                onRestorePurchase = onRestorePurchase,
                privacyOptionsRequired = privacyOptionsRequired,
                privacyMessage = privacyMessage,
                onPrivacyOptions = onPrivacyOptions,
                onOpenDiagnostics = { navController.navigate(Route.DIAGNOSTICS) },
                onOpenGuide = { navController.navigate(Route.GUIDE) },
                onOpenAbout = { navController.navigate(Route.ABOUT) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.SCAN_MODE) {
            ScanModeScreen(
                onBack = { navController.popBackStack() },
                onStart = { mode, speed ->
                    navController.navigate(Route.capture(mode, speed))
                },
            )
        }

        composable(Route.GUIDE) {
            OnboardingScreen(onFinished = { navController.popBackStack() })
        }

        composable(Route.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenScan = { navController.navigate(Route.ROOM) },
            )
        }

        composable(Route.COMPARE) {
            CompareScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.NETWORKS) {
            NetworksScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = { navController.navigate(Route.DETAILS) },
            )
        }

        composable(Route.ANALYSIS) {
            AnalysisScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Route.CAPTURE_PATTERN,
            arguments = listOf(
                navArgument(Route.CAPTURE_ARG_MODE) { type = NavType.StringType },
                navArgument(Route.CAPTURE_ARG_SPEED) { type = NavType.BoolType },
            ),
        ) { entry ->
            val raw = entry.arguments?.getString(Route.CAPTURE_ARG_MODE)
            val mode = ScanMode.entries.firstOrNull { it.name == raw } ?: ScanMode.PRECISE
            CaptureScreen(
                mode = mode,
                measureSpeed = entry.arguments
                    ?.getBoolean(Route.CAPTURE_ARG_SPEED) == true,
                onBack = { navController.popBackStack() },
                // The session is done with; land on the result and leave Home as the
                // only thing behind it, so Back does not re-enter a finished scan.
                onFinished = {
                    // Record completion only. Results are always immediately accessible.
                    onScanCompleted()
                    navController.navigate(Route.ROOM) {
                        popUpTo(Route.HOME)
                    }
                },
            )
        }

        composable(Route.ROOM) {
            val leaveResults = {
                navController.popBackStack()
                onResultsClosed(true)
            }
            BackHandler(onBack = leaveResults)
            RoomScreen(
                adsRemoved = adsRemoved,
                onBack = leaveResults,
                onRescan = {
                    onResultsClosed(false)
                    navController.navigate(Route.SCAN_MODE) {
                        popUpTo(Route.HOME)
                    }
                },
            )
        }
    }
}
