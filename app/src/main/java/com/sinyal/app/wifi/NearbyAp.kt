package com.sinyal.app.wifi

/** One access point seen in a scan. */
data class NearbyAp(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val isCurrent: Boolean,
    /** Carrier width in MHz — how far this network bleeds across the band. */
    val channelWidthMhz: Int,
    val security: SecurityType,
    val generation: WifiGeneration,
    /** True when the SSID is not broadcast; the platform reports it as blank. */
    val isHidden: Boolean,
    /** Answers ranging requests, so distance to it could be measured directly. */
    val supportsRanging: Boolean,
    /** WPS advertised in the beacon — a known brute-forceable entry point. */
    val supportsWps: Boolean,
) {
    val band: Band get() = Band.fromFrequency(frequencyMhz)
    val quality: SignalQuality get() = SignalQuality.of(rssiDbm)
    val channel: Int get() = channelFor(frequencyMhz)
}
