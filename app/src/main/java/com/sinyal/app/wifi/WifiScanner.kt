package com.sinyal.app.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Build
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.sinyal.app.R

/**
 * Neighbour AP enumeration.
 *
 * Since Android 9 a foreground app may only call [WifiManager.startScan] four
 * times per two minutes, so this reads the platform's cached results and treats
 * an explicit scan as a best-effort refresh rather than the primary source.
 */
class WifiScanner(private val context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Fires a refresh. Returns false when throttled or unpermitted. */
    @Suppress("DEPRECATION") // No replacement exists for on-demand scans.
    fun requestScan(): Boolean =
        if (hasLocationPermission) runCatching { wifiManager.startScan() }.getOrDefault(false)
        else false

    /** Generation as advertised in the beacon; unavailable before Android 11. */
    private fun generationOf(result: ScanResult): WifiGeneration {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return WifiGeneration.UNKNOWN
        return when (result.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> WifiGeneration.LEGACY
            ScanResult.WIFI_STANDARD_11N -> WifiGeneration.N
            ScanResult.WIFI_STANDARD_11AC -> WifiGeneration.AC
            ScanResult.WIFI_STANDARD_11AX -> WifiGeneration.AX
            ScanResult.WIFI_STANDARD_11AD -> WifiGeneration.AD
            ScanResult.WIFI_STANDARD_11BE -> WifiGeneration.BE
            else -> WifiGeneration.UNKNOWN
        }
    }

    /** Translates the platform's width enum into plain megahertz. */
    private fun widthOf(result: ScanResult): Int = when (result.channelWidth) {
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        else -> 20
    }

    /** Cached scan results, strongest first. Empty without location permission. */
    fun cachedResults(currentBssid: String?): List<NearbyAp> {
        if (!hasLocationPermission) return emptyList()
        val results = try {
            wifiManager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the platform call.
            emptyList()
        } catch (_: RuntimeException) {
            // Some vendor Wi-Fi services become unavailable while toggling the radio.
            emptyList()
        }
        return results.map { result ->
                NearbyAp(
                    ssid = result.SSID.ifBlank { appContext.getString(R.string.ssid_hidden) },
                    bssid = result.BSSID,
                    rssiDbm = result.level,
                    frequencyMhz = result.frequency,
                    isCurrent = result.BSSID.equals(currentBssid, ignoreCase = true),
                    channelWidthMhz = widthOf(result),
                    security = SecurityType.parse(result.capabilities),
                    generation = generationOf(result),
                    isHidden = result.SSID.isBlank(),
                    supportsRanging = runCatching { result.is80211mcResponder }
                        .getOrDefault(false),
                    supportsWps = result.capabilities.orEmpty().contains("WPS"),
                )
        }.sortedByDescending { it.rssiDbm }
    }

    /**
     * Raw scan entries that answer 802.11mc ranging, kept as [ScanResult]
     * because [android.net.wifi.rtt.RangingRequest] needs the platform object,
     * not the app's own [NearbyAp] view of it.
     */
    fun rangingCapableResults(): List<ScanResult> {
        if (!hasLocationPermission) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return try {
            wifiManager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }.filter { runCatching { it.is80211mcResponder }.getOrDefault(false) }
            .sortedByDescending { it.level }
    }
}
