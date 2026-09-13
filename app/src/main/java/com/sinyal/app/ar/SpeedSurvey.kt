package com.sinyal.app.ar

/** Throughput measured at a point along the walk. */
data class SpeedReading(
    val x: Float,
    val z: Float,
    val mbps: Double,
    val atMs: Long,
)

/**
 * What each part of the home could actually carry, as opposed to how loud the
 * radio was there.
 *
 * Sparse by nature: each reading costs a real download, so they are taken every
 * few seconds rather than continuously, and a walk yields perhaps a dozen. That
 * is enough to rank rooms against each other, which is the question worth
 * asking, and not enough to draw a smooth surface — so the map only paints near
 * where a measurement actually happened.
 */
class SpeedSurvey(val readings: List<SpeedReading> = emptyList()) {

    val isEmpty: Boolean get() = readings.isEmpty()

    val fastest: Double? get() = readings.maxOfOrNull { it.mbps }
    val slowest: Double? get() = readings.minOfOrNull { it.mbps }
    val average: Double? get() = readings.takeIf { it.isNotEmpty() }?.map { it.mbps }?.average()
}
