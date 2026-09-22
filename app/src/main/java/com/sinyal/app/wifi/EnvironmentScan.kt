package com.sinyal.app.wifi

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** What the airspace looked like at one moment. */
data class EnvironmentSnapshot(
    val atMs: Long,
    val aps: List<NearbyAp>,
) {
    /** RSSI → linear power (mW-ish scale); dBm cannot be averaged directly. */
    val totalPowerMw: Double get() = aps.sumOf { dbmToMw(it.rssiDbm) }

    companion object {
        fun dbmToMw(dbm: Int): Double = 10.0.pow(dbm / 10.0)
        fun mwToDbm(mw: Double): Double = if (mw <= 0) Double.NEGATIVE_INFINITY else 10 * log10(mw)
    }
}

enum class EnvironmentVerdict { BUSIER, QUIETER, SAME }

/** One channel's share of the airspace, before and after. */
data class ChannelShift(
    val band: Band,
    val channel: Int,
    val beforeDbm: Double?,
    val afterDbm: Double?,
    /** Change in summed linear power, expressed in dB. */
    val deltaDb: Double,
)

/** One access point that appeared, or got meaningfully louder/quieter. */
data class ApShift(
    val bssid: String,
    val ssid: String,
    val beforeDbm: Int?,
    val afterDbm: Int?,
)

data class EnvironmentComparison(
    val verdict: EnvironmentVerdict,
    /** Change in total linear power across every AP, in dB. */
    val powerDeltaDb: Double,
    val newAps: List<ApShift>,
    val departedAps: List<ApShift>,
    val louderAps: List<ApShift>,
    val quieterAps: List<ApShift>,
    /** Channels whose summed power changed most, noisiest first. */
    val channelShifts: List<ChannelShift>,
)

/**
 * The interferenceScanner idea: the radio environment is a moving thing, and
 * "is it worse now?" is only answerable against a baseline the user took when
 * it mattered.
 *
 * Compares two snapshots on three axes:
 *  - which BSSIDs appeared or disappeared entirely,
 *  - which survivors moved by more than a normal fade, and
 *  - how summed linear power moved per channel — the number that actually
 *    tracks contention, since dBm averages hide it.
 */
object EnvironmentDiff {

    fun diff(before: EnvironmentSnapshot, after: EnvironmentSnapshot): EnvironmentComparison {
        val beforeByBssid = before.aps.associateBy { it.bssid }
        val afterByBssid = after.aps.associateBy { it.bssid }

        val newAps = after.aps
            .filter { it.bssid !in beforeByBssid && it.rssiDbm >= MIN_LOUD_DBM }
            .map { ApShift(it.bssid, it.ssid, null, it.rssiDbm) }
            .sortedByDescending { it.afterDbm }

        val departedAps = before.aps
            .filter { it.bssid !in afterByBssid && it.rssiDbm >= MIN_LOUD_DBM }
            .map { ApShift(it.bssid, it.ssid, it.rssiDbm, null) }
            .sortedByDescending { it.beforeDbm }

        val louder = mutableListOf<ApShift>()
        val quieter = mutableListOf<ApShift>()
        for ((bssid, now) in afterByBssid) {
            val then = beforeByBssid[bssid] ?: continue
            val delta = now.rssiDbm - then.rssiDbm
            if (delta >= SWING_DB) {
                louder += ApShift(bssid, now.ssid, then.rssiDbm, now.rssiDbm)
            } else if (delta <= -SWING_DB) {
                quieter += ApShift(bssid, now.ssid, then.rssiDbm, now.rssiDbm)
            }
        }

        val channelShifts = channelShifts(before, after)
        val deltaDb = EnvironmentSnapshot.mwToDbm(after.totalPowerMw) -
            EnvironmentSnapshot.mwToDbm(before.totalPowerMw)

        val verdict = when {
            deltaDb >= VERDICT_DB || newAps.size >= 3 -> EnvironmentVerdict.BUSIER
            deltaDb <= -VERDICT_DB -> EnvironmentVerdict.QUIETER
            else -> EnvironmentVerdict.SAME
        }

        return EnvironmentComparison(
            verdict = verdict,
            powerDeltaDb = deltaDb,
            newAps = newAps,
            departedAps = departedAps,
            louderAps = louder.sortedByDescending { it.afterDbm },
            quieterAps = quieter.sortedBy { it.afterDbm },
            channelShifts = channelShifts,
        )
    }

    /**
     * Per-channel power delta, bands kept apart so "2.4 ch6" and "5 ch36" never
     * collide on the raw number. Only channels whose power moved by a real
     * margin are kept — a one-AP dB or two is ordinary wobble.
     */
    private fun channelShifts(
        before: EnvironmentSnapshot,
        after: EnvironmentSnapshot,
    ): List<ChannelShift> {
        fun byChannel(aps: List<NearbyAp>): Map<Pair<Band, Int>, Double> =
            aps.groupBy { it.band to it.channel }
                .mapValues { (_, group) -> group.sumOf { dbmToMw(it.rssiDbm) } }

        val beforeMap = byChannel(before.aps)
        val afterMap = byChannel(after.aps)
        val keys = beforeMap.keys + afterMap.keys

        return keys.mapNotNull { key ->
            val (band, channel) = key
            val was = beforeMap[key] ?: 0.0
            val now = afterMap[key] ?: 0.0
            val delta = when {
                was <= 0.0 -> Double.POSITIVE_INFINITY
                now <= 0.0 -> Double.NEGATIVE_INFINITY
                else -> 10 * log10(now / was)
            }
            if (abs(delta) < MIN_CHANNEL_SHIFT_DB) null else ChannelShift(
                band = band,
                channel = channel,
                beforeDbm = beforeMap[key]?.let(EnvironmentSnapshot::mwToDbm),
                afterDbm = afterMap[key]?.let(EnvironmentSnapshot::mwToDbm),
                deltaDb = delta,
            )
        }.sortedByDescending { abs(it.deltaDb).takeIf { it.isFinite() } ?: Double.MAX_VALUE }
    }

    /** An AP below this is too faint to matter when it appears or vanishes. */
    private const val MIN_LOUD_DBM = -80

    /** An AP's own RSSI must move this much to count as "got louder". */
    private const val SWING_DB = 8

    /** Total-power swing that flips the overall verdict. */
    private const val VERDICT_DB = 3.0

    /** A channel's summed power must move this much (dB) to be listed. */
    private const val MIN_CHANNEL_SHIFT_DB = 4.0
}

private fun dbmToMw(dbm: Int): Double = EnvironmentSnapshot.dbmToMw(dbm)
