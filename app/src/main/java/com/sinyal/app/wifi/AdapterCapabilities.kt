package com.sinyal.app.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.ChecksSdkIntAtLeast
import com.sinyal.app.R

/** One capability of the radio, and whether this phone has it. */
data class Capability(
    @StringRes val name: Int,
    val supported: Boolean?,
    /** Why the answer is unknown, when it is. */
    val note: String? = null,
) {
    val known: Boolean get() = supported != null
}

/**
 * What this phone's Wi-Fi radio can actually do.
 *
 * Every answer comes from [WifiManager] asking the driver, never from the model
 * name — two phones with the same marketing name can ship different radios, and
 * a table of guesses would be worse than no table.
 *
 * Where an API only exists from a later Android than this app's minimum, the
 * answer is null rather than false: "we cannot ask" and "the radio says no" are
 * different facts, and collapsing them would misreport perfectly capable
 * hardware as lacking a feature.
 */
class AdapterCapabilities(context: Context) {

    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val packages = appContext.packageManager

    fun read(): List<Capability> = buildList {
        add(Capability(R.string.cap_24, true))
        add(cap(R.string.cap_5, Build.VERSION_CODES.LOLLIPOP) { wifi.is5GHzBandSupported })
        add(cap(R.string.cap_6, Build.VERSION_CODES.R) { wifi.is6GHzBandSupported })
        add(cap(R.string.cap_60, Build.VERSION_CODES.S) { wifi.is60GHzBandSupported })

        add(standard(R.string.cap_wifi4, ScanResult.WIFI_STANDARD_11N))
        add(standard(R.string.cap_wifi5, ScanResult.WIFI_STANDARD_11AC))
        add(standard(R.string.cap_wifi6, ScanResult.WIFI_STANDARD_11AX))
        add(standard(R.string.cap_wifi7, ScanResult.WIFI_STANDARD_11BE))

        add(cap(R.string.cap_wpa3_sae, Build.VERSION_CODES.Q) { wifi.isWpa3SaeSupported })
        add(cap(R.string.cap_wpa3_suiteb, Build.VERSION_CODES.Q) {
            wifi.isWpa3SuiteBSupported
        })
        add(cap(R.string.cap_owe, Build.VERSION_CODES.Q) { wifi.isEnhancedOpenSupported })
        add(cap(R.string.cap_dpp, Build.VERSION_CODES.Q) { wifi.isEasyConnectSupported })

        add(cap(R.string.cap_rtt, Build.VERSION_CODES.P) {
            packages.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
        })
        add(
            Capability(
                R.string.cap_aware,
                packages.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE),
            ),
        )
        add(
            Capability(
                R.string.cap_direct,
                packages.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT),
            ),
        )
        add(
            Capability(
                R.string.cap_passpoint,
                packages.hasSystemFeature(PackageManager.FEATURE_WIFI_PASSPOINT),
            ),
        )
        add(cap(R.string.cap_tdls, Build.VERSION_CODES.LOLLIPOP) {
            wifi.isTdlsSupported
        })
        add(cap(R.string.cap_sta_ap, Build.VERSION_CODES.R) {
            wifi.isStaApConcurrencySupported
        })
        add(cap(R.string.cap_dual_sta, Build.VERSION_CODES.S) {
            wifi.isStaConcurrencyForLocalOnlyConnectionsSupported
        })
        add(cap(R.string.cap_scan_throttle, Build.VERSION_CODES.R) {
            wifi.isScanThrottleEnabled
        })
    }

    /** Reads a flag only when the platform is new enough to have been asked. */
    @ChecksSdkIntAtLeast(parameter = 1, lambda = 2)
    private inline fun cap(@StringRes name: Int, since: Int, read: () -> Boolean): Capability =
        if (Build.VERSION.SDK_INT >= since) {
            Capability(name, runCatching(read).getOrNull())
        } else {
            Capability(name, null, ANDROID_TOO_OLD)
        }

    private fun standard(@StringRes name: Int, standard: Int): Capability =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Capability(name, runCatching { wifi.isWifiStandardSupported(standard) }.getOrNull())
        } else {
            Capability(name, null, ANDROID_TOO_OLD)
        }

    private companion object {
        const val ANDROID_TOO_OLD = "android_too_old"
    }
}
