package com.sinyal.app.wifi

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Live link state of the currently connected access point.
 *
 * RSSI is read straight off [WifiManager] rather than through a scan, because
 * scans are throttled to 4 per 2 minutes since Android 9 while the connected-AP
 * RSSI can be polled freely. That is what makes a real-time heatmap possible.
 *
 * Note the permission split: RSSI is readable without location permission, SSID
 * is not. The snapshot degrades to a null [WifiSnapshot.ssid] rather than failing.
 */
class WifiMonitor(context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val isWifiEnabled: Boolean get() = wifiManager.isWifiEnabled

    /** Emits a fresh snapshot every [periodMs]. Cancellable via the collecting scope. */
    fun snapshots(periodMs: Long = DEFAULT_PERIOD_MS): Flow<WifiSnapshot> = flow {
        while (true) {
            emit(read())
            delay(periodMs)
        }
    }.conflate().flowOn(Dispatchers.Default)

    @Suppress("DEPRECATION") // Polling getConnectionInfo() is the only way to sample RSSI at rate.
    fun read(): WifiSnapshot {
        val info: WifiInfo = wifiManager.connectionInfo ?: return WifiSnapshot.Disconnected
        if (!isRealConnection(info)) return WifiSnapshot.Disconnected

        return WifiSnapshot(
            connected = true,
            ssid = cleanSsid(info.ssid),
            bssid = info.bssid,
            rssiDbm = info.rssi,
            frequencyMhz = info.frequency,
            linkSpeedMbps = info.linkSpeed,
            generation = generationOf(info),
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    private fun isRealConnection(info: WifiInfo): Boolean {
        val bssid = info.bssid
        return bssid != null && bssid != NULL_BSSID && info.rssi > NO_SIGNAL_DBM
    }

    /** The platform hands SSIDs back quoted, and redacts them without location permission. */
    private fun cleanSsid(raw: String?): String? {
        val trimmed = raw?.trim()?.removeSurrounding("\"")
        return if (trimmed.isNullOrBlank() || trimmed == UNKNOWN_SSID) null else trimmed
    }

    private fun generationOf(info: WifiInfo): WifiGeneration {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return WifiGeneration.UNKNOWN
        return when (info.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> WifiGeneration.LEGACY
            ScanResult.WIFI_STANDARD_11N -> WifiGeneration.N
            ScanResult.WIFI_STANDARD_11AC -> WifiGeneration.AC
            ScanResult.WIFI_STANDARD_11AX -> WifiGeneration.AX
            ScanResult.WIFI_STANDARD_11AD -> WifiGeneration.AD
            ScanResult.WIFI_STANDARD_11BE -> WifiGeneration.BE
            else -> WifiGeneration.UNKNOWN
        }
    }

    companion object {
        /** 4 Hz: fast enough to feel live while walking, cheap enough for a budget SoC. */
        const val DEFAULT_PERIOD_MS = 250L
        private const val NULL_BSSID = "02:00:00:00:00:00"
        private const val UNKNOWN_SSID = "<unknown ssid>"
        private const val NO_SIGNAL_DBM = -127
    }
}
