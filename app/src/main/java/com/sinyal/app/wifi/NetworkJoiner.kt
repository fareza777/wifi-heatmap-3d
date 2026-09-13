package com.sinyal.app.wifi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings

/** What the phone is able to offer for a given network. */
enum class JoinRoute {
    /** The system can be asked to save and join it outright. */
    SUGGEST,

    /** Only the system picker can do it — a password is needed, or Android is old. */
    PICKER,
}

/**
 * Hands a chosen network to the system to join.
 *
 * An app cannot silently connect a phone to a network, and should not be able
 * to. What it can do, from Android 11, is propose one: [Settings.ACTION_WIFI_ADD_NETWORKS]
 * puts up the system's own "save this network?" dialog, and if the user agrees
 * the phone joins for real, system-wide and permanently.
 *
 * The older `WifiNetworkSpecifier` route is deliberately not used. It binds the
 * network to this app's own traffic only, leaving the phone's actual connection
 * untouched — which would be a lie in an app whose whole subject is which
 * network the phone is on.
 */
class NetworkJoiner(context: Context) {

    private val appContext = context.applicationContext

    fun routeFor(ap: NearbyAp): JoinRoute = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> JoinRoute.PICKER
        ap.isHidden -> JoinRoute.PICKER
        ap.security.joinableWithoutPassword -> JoinRoute.SUGGEST
        else -> JoinRoute.PICKER
    }

    /**
     * Asks the system to add [ap]. Falls back to the picker whenever the
     * suggestion route is unavailable or the dialog cannot be opened.
     */
    fun join(ap: NearbyAp) {
        if (routeFor(ap) == JoinRoute.SUGGEST && suggest(ap)) return
        openPicker()
    }

    private fun suggest(ap: NearbyAp): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        return runCatching {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ap.ssid)
                .apply {
                    // OWE is passwordless but still encrypted; it has to be asked
                    // for explicitly or the system offers a plain open join.
                    if (ap.security == SecurityType.OWE) setIsEnhancedOpen(true)
                }
                .build()

            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(
                    Settings.EXTRA_WIFI_NETWORK_LIST,
                    arrayListOf(suggestion),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** The system Wi-Fi picker, where a password can be typed. */
    fun openPicker() {
        val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            appContext.startActivity(panel)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
