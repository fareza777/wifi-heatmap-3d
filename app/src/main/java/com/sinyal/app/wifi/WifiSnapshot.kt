package com.sinyal.app.wifi

/** Radio band a connection sits on. */
enum class Band(val label: String) {
    GHZ_24("2.4 GHz"),
    GHZ_5("5 GHz"),
    GHZ_6("6 GHz"),
    UNKNOWN("—"),
    ;

    companion object {
        fun fromFrequency(mhz: Int): Band = when (mhz) {
            in 2401..2499 -> GHZ_24
            in 4900..5899 -> GHZ_5
            in 5925..7125 -> GHZ_6
            else -> UNKNOWN
        }
    }
}

/** IEEE 802.11 generation, as reported by the platform. */
enum class WifiGeneration(val label: String) {
    LEGACY("802.11 a/b/g"),
    N("Wi-Fi 4"),
    AC("Wi-Fi 5"),
    AX("Wi-Fi 6"),
    AD("802.11ad"),
    BE("Wi-Fi 7"),
    UNKNOWN("—"),
}

/**
 * One instant of the connected-AP link state. This is the sample the heatmap is
 * eventually built from, so every field the engine may need is captured here.
 */
data class WifiSnapshot(
    val connected: Boolean,
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val linkSpeedMbps: Int,
    val generation: WifiGeneration,
    val capturedAtMs: Long,
) {
    val band: Band get() = Band.fromFrequency(frequencyMhz)
    val quality: SignalQuality get() = SignalQuality.of(rssiDbm)

    val channel: Int get() = channelFor(frequencyMhz)

    companion object {
        val Disconnected = WifiSnapshot(
            connected = false,
            ssid = null,
            bssid = null,
            rssiDbm = -127,
            frequencyMhz = 0,
            linkSpeedMbps = 0,
            generation = WifiGeneration.UNKNOWN,
            capturedAtMs = 0L,
        )
    }
}

/** Channel number derived from centre frequency. 0 when it cannot be mapped. */
fun channelFor(frequencyMhz: Int): Int = when (frequencyMhz) {
    2484 -> 14
    in 2401..2483 -> (frequencyMhz - 2407) / 5
    in 4900..5899 -> (frequencyMhz - 5000) / 5
    in 5925..7125 -> (frequencyMhz - 5950) / 5
    else -> 0
}
