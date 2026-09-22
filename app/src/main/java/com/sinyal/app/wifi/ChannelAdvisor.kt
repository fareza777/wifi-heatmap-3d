package com.sinyal.app.wifi

import kotlin.math.abs
import kotlin.math.pow

/**
 * One channel scored for how free it is.
 *
 * [score] is the headline number: 10 means empty air, 0 means a neighbour is
 * already parked on the channel at full strength. [interferenceScore] stays in
 * the same linear-power units as [ChannelLoad.interferenceScore] so charts can
 * mix the two.
 */
data class RatedChannel(
    val channel: Int,
    val centreMhz: Int,
    /** Networks whose carrier sits exactly on this channel. */
    val onChannel: Int,
    /** Networks bleeding in from neighbouring carriers. */
    val overlapping: Int,
    val strongestDbm: Int?,
    val interferenceScore: Float,
    val score: Int,
    /** DFS (radar-shared) channel in 5 GHz — legal but vacated on radar events. */
    val isDfs: Boolean = false,
)

/** Every rated channel of a band plus the recommendation for the router. */
data class BandAdvice(
    val band: Band,
    val channels: List<RatedChannel>,
    val recommended: RatedChannel?,
    /** Next-best picks after [recommended], for the "or try" line. */
    val alternates: List<RatedChannel>,
    val count: Int,
) {
    val worst: RatedChannel? get() = channels.maxByOrNull { it.interferenceScore }
}

/**
 * Rates every channel of a band on the APs seen in one scan.
 *
 * Two ideas borrowed from classic Wi-Fi analysers, reimplemented here:
 *
 *  - **Score = AP count + rival strength**, not RSSI alone. Ten weak
 *    neighbours are a different problem than one strong one, so the number
 *    that drives the recommendation counts both.
 *  - **Width-aware overlap.** An 80 MHz carrier interferes with every channel
 *    it spans, not just the one it is centred on — a bar-per-centre-channel
 *    chart hides exactly that.
 *
 * Frequencies rather than channel numbers drive the overlap math, so wide 5
 * and 6 GHz carriers spread the way they do on a real spectrum analyser.
 */
object ChannelAdvisor {

    /** Half of a candidate channel's nominal width, plus the neighbour reach. */
    private const val GUARD_MHZ = 15.0

    /** Centre-frequency tolerance that still counts as "on this channel". */
    private const val ON_CHANNEL_MHZ = 2.5

    /** Every 20 MHz channel usable in 5 GHz, DFS included. */
    private val CHANNELS_5 = listOf(
        36, 40, 44, 48,
        52, 56, 60, 64,
        100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140,
        149, 153, 157, 161, 165,
    )

    /** DFS range in 5 GHz (U-NII-2A/2C), where radar forces a channel move. */
    private val DFS_5 = 52..140

    /** Preferred scanning channels of 6 GHz — where radios actually sit. */
    private val PSC_6 = listOf(15, 35, 55, 75, 95, 115, 135, 155, 175, 195, 215, 235)

    fun evaluate(networks: List<NearbyAp>, band: Band): BandAdvice {
        val inBand = networks.filter { it.band == band && it.channel > 0 }
        val candidates = candidateChannels(band, inBand)

        val rated = candidates.map { channel ->
            rate(channel, centreFor(band, channel), inBand, band == Band.GHZ_5 && channel in DFS_5)
        }

        // Candidates for the recommendation, not the rating table: in 2.4 GHz
        // only 1/6/11 are ever advised, because an in-between channel overlaps
        // more neighbours than it escapes.
        val eligible = rated.filter {
            band != Band.GHZ_24 || it.channel in NON_OVERLAPPING
        }

        val ranked = eligible.sortedWith(
            compareByDescending<RatedChannel> { it.score }
                .thenBy { it.onChannel + it.overlapping }
                .thenBy { it.strongestDbm ?: Int.MIN_VALUE },
        )

        return BandAdvice(
            band = band,
            channels = rated,
            recommended = ranked.firstOrNull(),
            alternates = ranked.drop(1).take(2),
            count = inBand.size,
        )
    }

    /** All channels of the band, so empty ones rate as free rather than missing. */
    private fun candidateChannels(band: Band, networks: List<NearbyAp>): List<Int> =
        when (band) {
            Band.GHZ_24 -> (1..13).toList()
            Band.GHZ_5 -> (CHANNELS_5 + networks.map { it.channel }).distinct().sorted()
            Band.GHZ_6 -> ((1..233 step 4) + PSC_6 + networks.map { it.channel })
                .distinct()
                .sorted()
            Band.UNKNOWN -> networks.map { it.channel }.distinct().sorted()
        }

    /**
     * Signal buckets, coarse on purpose: the difference between −52 and −56 dBm
     * changes nothing about which channel to pick, so the score only credits
     * steps a user can act on.
     */
    private fun strengthLevel(dbm: Int?): Int = when {
        dbm == null -> 0
        dbm >= -50 -> 4
        dbm >= -60 -> 3
        dbm >= -70 -> 2
        dbm >= -80 -> 1
        else -> 0
    }

    private fun rate(
        channel: Int,
        centreMhz: Int,
        networks: List<NearbyAp>,
        isDfs: Boolean,
    ): RatedChannel {
        var power = 0.0
        var on = 0
        var near = 0
        var strongest: Int? = null

        for (ap in networks) {
            // The AP's energy spans half its carrier width; anything inside
            // reach still adds noise, just less of it the further out it sits.
            val reach = ap.channelWidthMhz / 2.0 + GUARD_MHZ
            val delta = abs(ap.frequencyMhz - centreMhz)
            if (delta > reach) continue

            // An AP exactly at the reach boundary contributes no power and
            // must not count against the score either.
            val weight = 1.0 - delta / reach
            if (weight <= 0.0) continue

            power += weight * 10.0.pow(ap.rssiDbm / 10.0)

            if (delta <= ON_CHANNEL_MHZ) {
                on++
            } else {
                near++
            }
            if (strongest == null || ap.rssiDbm > strongest) strongest = ap.rssiDbm
        }

        val score = (10 - on - near - strengthLevel(strongest)).coerceIn(0, 10)

        return RatedChannel(
            channel = channel,
            centreMhz = centreMhz,
            onChannel = on,
            overlapping = near,
            strongestDbm = strongest,
            interferenceScore = power.toFloat(),
            score = score,
            isDfs = isDfs,
        )
    }

    /** Inverse of [channelFor]: channel number back to centre frequency. */
    fun centreFor(band: Band, channel: Int): Int = when (band) {
        Band.GHZ_24 -> if (channel == 14) 2484 else 2407 + channel * 5
        Band.GHZ_5 -> 5000 + channel * 5
        Band.GHZ_6 -> 5950 + channel * 5
        Band.UNKNOWN -> 0
    }
}
