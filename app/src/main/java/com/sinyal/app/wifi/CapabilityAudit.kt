package com.sinyal.app.wifi

import androidx.annotation.StringRes
import com.sinyal.app.R

/** Something the hardware could do that the current connection is not doing. */
data class Mismatch(
    @StringRes val title: Int,
    @StringRes val detail: Int,
    /** Filled into [detail] in order. */
    val args: List<Any> = emptyList(),
    val wasted: Boolean = true,
)

/**
 * Compares what the router advertises against what this phone can do.
 *
 * Every fact here is already on screen somewhere — the band, the channel width,
 * the security, the standard, the radio's own capability list. What is missing
 * is the comparison, and the comparison is where the actionable part lives: a
 * router capable of 80 MHz serving a phone capable of 80 MHz over a 20 MHz link
 * is losing three quarters of its throughput, and nothing in the phone's own
 * settings will ever mention it.
 *
 * Only gaps are reported. Matching capability is not news.
 */
object CapabilityAudit {

    fun run(
        connected: NearbyAp?,
        neighbours: List<NearbyAp>,
        capabilities: List<Capability>,
        rates: LinkRates?,
    ): List<Mismatch> {
        if (connected == null) return emptyList()

        val supports = capabilities.filter { it.supported == true }.map { it.name }.toSet()

        return buildList {
            bandSteering(connected, neighbours, supports)?.let(::add)
            channelWidth(connected)?.let(::add)
            standard(connected, supports)?.let(::add)
            security(connected, supports)?.let(::add)
            linkRate(rates)?.let(::add)
        }
    }

    /**
     * Sitting on 2.4 GHz while the same network is also on 5 GHz.
     *
     * The single most common avoidable loss in a home: the phone joined the
     * slower band once, usually from further away, and never moved back.
     */
    private fun bandSteering(
        connected: NearbyAp,
        neighbours: List<NearbyAp>,
        supports: Set<Int>,
    ): Mismatch? {
        if (connected.band != Band.GHZ_24) return null
        if (R.string.cap_5 !in supports) return null

        val faster = neighbours.firstOrNull {
            it.ssid == connected.ssid && it.band == Band.GHZ_5
        } ?: return null

        return Mismatch(
            title = R.string.audit_band_title,
            detail = R.string.audit_band_detail,
            args = listOf(faster.rssiDbm, connected.rssiDbm),
        )
    }

    /** A wide-capable carrier being used at its narrowest. */
    private fun channelWidth(connected: NearbyAp): Mismatch? {
        if (connected.channelWidthMhz >= WIDE_MHZ) return null
        if (connected.band == Band.GHZ_24) return null

        return Mismatch(
            title = R.string.audit_width_title,
            detail = R.string.audit_width_detail,
            args = listOf(connected.channelWidthMhz),
        )
    }

    /** The phone can speak a newer generation than the router offers. */
    private fun standard(connected: NearbyAp, supports: Set<Int>): Mismatch? {
        val phoneBest = when {
            R.string.cap_wifi7 in supports -> WifiGeneration.BE
            R.string.cap_wifi6 in supports -> WifiGeneration.AX
            R.string.cap_wifi5 in supports -> WifiGeneration.AC
            else -> return null
        }
        if (connected.generation.ordinal >= phoneBest.ordinal) return null
        if (connected.generation == WifiGeneration.UNKNOWN) return null

        return Mismatch(
            title = R.string.audit_standard_title,
            detail = R.string.audit_standard_detail,
            args = listOf(phoneBest.label, connected.generation.label),
        )
    }

    /** WPA3 available on both sides but not in use. */
    private fun security(connected: NearbyAp, supports: Set<Int>): Mismatch? {
        if (connected.security == SecurityType.WPA3) return null
        if (connected.security == SecurityType.ENTERPRISE) return null
        if (R.string.cap_wpa3_sae !in supports) return null

        return Mismatch(
            title = R.string.audit_wpa3_title,
            detail = R.string.audit_wpa3_detail,
            args = listOf(),
        )
    }

    /**
     * Negotiated far below what both ends agreed they could manage.
     *
     * Reported as a symptom rather than a cause: distance, interference and a
     * busy channel all produce it, and the map is what tells them apart.
     */
    private fun linkRate(rates: LinkRates?): Mismatch? {
        val current = rates?.txMbps ?: return null
        val ceiling = rates.maxSupportedTxMbps ?: return null
        if (ceiling <= 0 || current >= ceiling * RATE_FLOOR) return null

        return Mismatch(
            title = R.string.audit_rate_title,
            detail = R.string.audit_rate_detail,
            args = listOf(current, ceiling),
        )
    }

    /** Below 80 MHz on 5 GHz leaves most of the available throughput unused. */
    private const val WIDE_MHZ = 80

    /** Running under this share of the negotiated ceiling is worth flagging. */
    private const val RATE_FLOOR = 0.5f
}
