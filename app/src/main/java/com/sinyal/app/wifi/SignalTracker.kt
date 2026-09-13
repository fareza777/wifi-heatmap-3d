package com.sinyal.app.wifi

/** One access point plus everything it has read since tracking began. */
data class TrackedAp(
    val ssid: String,
    val bssid: String,
    val band: Band,
    val channel: Int,
    val security: SecurityType,
    val isCurrent: Boolean,
    val history: List<Int>,
) {
    val current: Int get() = history.lastOrNull() ?: MISSING
    val best: Int get() = history.maxOrNull() ?: MISSING
    val worst: Int get() = history.minOrNull() ?: MISSING

    /**
     * Averaged in linear power, not in dBm.
     *
     * Decibels are logarithmic, so the arithmetic mean of −40 and −80 is not
     * −60: the first reading carries ten thousand times the power of the second
     * and the honest average sits within a hair of −40.
     */
    val average: Int
        get() {
            if (history.isEmpty()) return MISSING
            val power = history.sumOf { Math.pow(10.0, it / 10.0) } / history.size
            return Math.round(10.0 * Math.log10(power)).toInt()
        }

    /** How far this reading swings — a steady link and a flapping one differ here. */
    val spreadDb: Int get() = if (history.size < 2) 0 else best - worst

    private companion object {
        const val MISSING = -127
    }
}

/**
 * Keeps a rolling RSSI history per access point across repeated scans.
 *
 * A single reading says almost nothing: Wi-Fi fluctuates several dB from second
 * to second while nothing moves. What a user needs to see is whether a network
 * is steady, drifting, or dropping out — and that only exists over time.
 *
 * Keyed by BSSID rather than SSID, so a mesh with three radios under one name
 * charts as three lines instead of one line jumping between them.
 */
class SignalTracker(private val capacity: Int = MAX_POINTS) {

    private val histories = LinkedHashMap<String, MutableList<Int>>()
    private val seen = LinkedHashMap<String, NearbyAp>()

    /** Folds one scan into the running history and returns the current picture. */
    fun record(networks: List<NearbyAp>): List<TrackedAp> {
        networks.forEach { ap ->
            seen[ap.bssid] = ap
            val series = histories.getOrPut(ap.bssid) { mutableListOf() }
            series += ap.rssiDbm
            if (series.size > capacity) series.removeAt(0)
        }

        return seen.values.map { ap ->
            TrackedAp(
                ssid = ap.ssid,
                bssid = ap.bssid,
                band = ap.band,
                channel = ap.channel,
                security = ap.security,
                isCurrent = ap.isCurrent,
                history = histories[ap.bssid].orEmpty().toList(),
            )
        }.sortedByDescending { it.current }
    }

    fun reset() {
        histories.clear()
        seen.clear()
    }

    private companion object {
        /** Two minutes of history at the screen's sampling rate. */
        const val MAX_POINTS = 60
    }
}
