package com.sinyal.app.wifi

import androidx.annotation.StringRes
import com.sinyal.app.R

/**
 * RSSI buckets. Thresholds follow the usual field convention: -50 dBm and above
 * is as good as it practically gets indoors, below -75 dBm most things stall.
 */
enum class SignalQuality(@StringRes val label: Int) {
    EXCELLENT(R.string.quality_excellent),
    GOOD(R.string.quality_good),
    FAIR(R.string.quality_fair),
    WEAK(R.string.quality_weak),
    DEAD(R.string.quality_dead),
    ;

    companion object {
        const val FLOOR_DBM = -100
        const val CEILING_DBM = -30

        fun of(rssiDbm: Int): SignalQuality = when {
            rssiDbm >= -50 -> EXCELLENT
            rssiDbm >= -60 -> GOOD
            rssiDbm >= -67 -> FAIR
            rssiDbm >= -75 -> WEAK
            else -> DEAD
        }

        /** Maps dBm onto 0f..1f for gauges and colour ramps. */
        fun normalize(rssiDbm: Int): Float {
            val span = (CEILING_DBM - FLOOR_DBM).toFloat()
            return ((rssiDbm - FLOOR_DBM) / span).coerceIn(0f, 1f)
        }
    }
}
