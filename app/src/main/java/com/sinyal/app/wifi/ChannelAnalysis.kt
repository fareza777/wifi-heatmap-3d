package com.sinyal.app.wifi

import kotlin.math.abs
import kotlin.math.pow

/** How busy one channel is, and who is making it busy. */
data class ChannelLoad(
    val channel: Int,
    val band: Band,
    val networkCount: Int,
    val strongestDbm: Int?,
    val interferenceScore: Float,
    /** Networks that overlap this channel without sitting exactly on it. */
    val adjacentCount: Int = 0,
) {
    val totalOverlapping: Int get() = networkCount + adjacentCount
}

/**
 * What the user's own channel is competing with.
 *
 * The two counts are worth separating because the fixes differ. Co-channel
 * neighbours take turns with you — the link stays clean but slows down, and
 * moving channel helps. Adjacent ones bleed across without ever coordinating,
 * which shows up as retries and jitter rather than lower speed, and is the worse
 * of the two despite usually looking like the smaller number.
 */
data class CurrentChannelVerdict(
    val channel: Int,
    val band: Band,
    val coChannel: Int,
    val adjacent: Int,
    val strongestRivalDbm: Int?,
)

/** The airwaves around this phone, summarised. */
data class ChannelReport(
    val band24: List<ChannelLoad>,
    val band5: List<ChannelLoad>,
    val currentChannel: Int,
    val currentBand: Band,
    val recommended24: Int?,
    val recommended5: Int?,
    val totalNetworks: Int,
    val verdict: CurrentChannelVerdict? = null,
    val count24: Int = 0,
    val count5: Int = 0,
) {
    /** Channels of the active band, busiest first. */
    val rankedCurrentBand: List<ChannelLoad>
        get() = (if (currentBand == Band.GHZ_24) band24 else band5)
            .filter { it.totalOverlapping > 0 }
            .sortedByDescending { it.interferenceScore }

    /** Quietest channels of the active band, for the move-to suggestion. */
    val quietestCurrentBand: List<ChannelLoad>
        get() {
            val loads = if (currentBand == Band.GHZ_24) {
                band24.filter { it.channel in NON_OVERLAPPING }
            } else {
                band5
            }
            return loads.sortedBy { it.interferenceScore }.take(3)
        }

    /** Busiest score in the active band, so bars can be drawn relative to it. */
    val peakScore: Float
        get() = (if (currentBand == Band.GHZ_24) band24 else band5)
            .maxOfOrNull { it.interferenceScore } ?: 0f

    val isCurrentCrowded: Boolean
        get() {
            val loads = if (currentBand == Band.GHZ_24) band24 else band5
            val current = loads.firstOrNull { it.channel == currentChannel } ?: return false
            return current.networkCount > 1
        }
}

/**
 * Reads congestion out of a plain scan list.
 *
 * The 2.4 GHz band is the interesting case: a 20 MHz carrier spans about five
 * channel numbers, so a network on channel 8 degrades channels 6 through 10 as
 * well. Counting only exact matches would call a heavily-overlapped channel
 * empty, which is precisely the mistake that leaves people sitting on channel 9.
 *
 * Interference is summed in linear power rather than dBm, because decibels are
 * logarithmic and adding them would be meaningless arithmetic.
 */
/** Only these three fit side by side without overlapping in 2.4 GHz. */
internal val NON_OVERLAPPING = listOf(1, 6, 11)

object ChannelAnalysis {

    /** Channel spacing in the 2.4 GHz band at which carriers stop overlapping. */
    private const val OVERLAP_SPAN = 4

    /** Only these three fit side by side without overlapping in 2.4 GHz. */
    private val NON_OVERLAPPING_24 = NON_OVERLAPPING

    fun analyse(networks: List<NearbyAp>, current: WifiSnapshot): ChannelReport {
        val on24 = networks.filter { it.band == Band.GHZ_24 && it.channel in 1..14 }
        val on5 = networks.filter { it.band == Band.GHZ_5 && it.channel > 0 }

        return ChannelReport(
            band24 = (1..13).map { channel -> loadOf(channel, Band.GHZ_24, on24, OVERLAP_SPAN) },
            band5 = channelsPresent(on5).map { channel ->
                // 5 GHz carriers on standard 20 MHz spacing do not bleed into neighbours.
                loadOf(channel, Band.GHZ_5, on5, overlapSpan = 0)
            },
            currentChannel = current.channel,
            currentBand = current.band,
            recommended24 = bestOf(
                NON_OVERLAPPING_24.map { channel -> loadOf(channel, Band.GHZ_24, on24, OVERLAP_SPAN) },
            ),
            recommended5 = bestOf(channelsPresent(on5).map { loadOf(it, Band.GHZ_5, on5, 0) }),
            totalNetworks = networks.size,
            verdict = verdictFor(networks, current),
            count24 = on24.size,
            count5 = on5.size,
        )
    }

    /**
     * Splits the neighbours on the user's own channel from the ones bleeding in.
     *
     * The phone's own access point is excluded by BSSID rather than by SSID: a
     * mesh publishes the same name from several radios, and those really are
     * separate competitors for the air.
     */
    private fun verdictFor(
        networks: List<NearbyAp>,
        current: WifiSnapshot,
    ): CurrentChannelVerdict? {
        if (!current.connected || current.channel <= 0) return null

        val rivals = networks.filter { !it.isCurrent && it.band == current.band }
        val span = if (current.band == Band.GHZ_24) OVERLAP_SPAN else 0

        val co = rivals.filter { it.channel == current.channel }
        val adjacent = rivals.filter {
            it.channel != current.channel && abs(it.channel - current.channel) <= span
        }

        return CurrentChannelVerdict(
            channel = current.channel,
            band = current.band,
            coChannel = co.size,
            adjacent = adjacent.size,
            strongestRivalDbm = (co + adjacent).maxOfOrNull { it.rssiDbm },
        )
    }

    private fun channelsPresent(networks: List<NearbyAp>): List<Int> =
        networks.map { it.channel }.distinct().sorted()

    private fun loadOf(
        channel: Int,
        band: Band,
        networks: List<NearbyAp>,
        overlapSpan: Int,
    ): ChannelLoad {
        var power = 0.0
        var count = 0
        var adjacent = 0
        var strongest: Int? = null

        for (network in networks) {
            val distance = abs(network.channel - channel)
            if (distance > overlapSpan) continue

            // Neighbours count for less the further off-channel they sit.
            val weight = if (overlapSpan == 0) 1.0 else 1.0 - (distance.toDouble() / (overlapSpan + 1))
            power += weight * 10.0.pow(network.rssiDbm / 10.0)

            if (distance == 0) {
                count++
                if (strongest == null || network.rssiDbm > strongest!!) strongest = network.rssiDbm
            } else {
                adjacent++
            }
        }

        return ChannelLoad(
            channel = channel,
            band = band,
            networkCount = count,
            strongestDbm = strongest,
            interferenceScore = power.toFloat(),
            adjacentCount = adjacent,
        )
    }

    private fun bestOf(loads: List<ChannelLoad>): Int? =
        loads.minByOrNull { it.interferenceScore }?.channel
}
