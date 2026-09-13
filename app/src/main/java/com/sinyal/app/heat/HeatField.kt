package com.sinyal.app.heat

import com.sinyal.app.ar.GridCell
import com.sinyal.app.ar.WallSegment
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One tile of the baked heatmap.
 *
 * [heightMeters] lifts the tile into a relief: colour alone has to be decoded
 * against a legend, while height is read instantly and survives being viewed at
 * an angle, in sunlight, or by someone who cannot separate red from green.
 *
 * [confidence] runs 0..1 and says how much measurement is actually behind the
 * tile, so the renderer can stop a lone reading from looking as certain as a
 * corner that was walked four times.
 */
data class HeatTile(
    val x: Float,
    val z: Float,
    val size: Float,
    val rssiDbm: Int,
    val heightMeters: Float,
    val confidence: Float = 1f,
)

/**
 * Inverse-distance-weighted interpolation over the measured cells.
 *
 * Readings are sparse and unevenly spaced — a walk-through visits a hallway far
 * more than a corner — so a plain nearest-neighbour fill would show blocky
 * artefacts along the path. IDW smooths between samples while still honouring
 * each measurement exactly at its own location.
 *
 * Two corrections make the result physical rather than merely smooth:
 *
 * Walls block. A plain IDW pulls a value straight through a partition wall, so
 * a dead back bedroom inherits the strong hallway readings two metres away and
 * paints green over the exact problem the user opened the app to find. Every
 * sample's line of sight is tested against the detected walls and penalised per
 * crossing — not discarded, because a penalised sample still beats a hole.
 *
 * Well-sampled cells count for more. A cell averaged from thirty readings has a
 * standard error a fraction of a cell built from one, and inverse-variance
 * weighting says that is worth sqrt(n) — capped, so one heavily-walked spot
 * cannot flatten a whole room.
 *
 * Points further than [CUTOFF_METERS] from every sample return null rather than
 * being extrapolated: showing invented signal for a room nobody walked into
 * would be worse than showing a hole.
 */
class HeatField(
    private val cells: List<GridCell>,
    private val walls: List<WallSegment> = emptyList(),
) {

    /** Interpolated reading, or null where nothing was measured close enough. */
    fun valueAt(x: Float, z: Float): Int? = estimateAt(x, z)?.rssiDbm

    /** Reading plus how much measurement stands behind it. */
    fun estimateAt(x: Float, z: Float): Estimate? {
        if (cells.isEmpty()) return null

        var weightSum = 0.0
        var valueSum = 0.0
        var samplesNearby = 0
        var closest = Float.MAX_VALUE

        for (cell in cells) {
            val distance = hypot(x - cell.x, z - cell.z)
            if (distance > CUTOFF_METERS) continue

            val reliability = sqrt(min(cell.sampleCount, SAMPLE_CAP).toDouble())

            // Sitting on a sample: return it verbatim instead of dividing by zero.
            if (distance < EPSILON) {
                return Estimate(cell.rssiDbm, confidenceFor(cell.sampleCount, 0f))
            }

            val blocked = wallsBetween(x, z, cell.x, cell.z)
            val penalty = BLOCKED_WEIGHT.pow(blocked)

            val weight = reliability * penalty / distance.toDouble().pow(POWER)
            weightSum += weight
            valueSum += weight * cell.rssiDbm
            samplesNearby += cell.sampleCount
            if (distance < closest) closest = distance
        }

        if (weightSum == 0.0) return null
        return Estimate(
            rssiDbm = (valueSum / weightSum).toInt(),
            confidence = confidenceFor(samplesNearby, closest),
        )
    }

    /**
     * How far to trust a tile: plenty of nearby readings and a close one among
     * them is the confident case; a single distant sample is the doubtful one.
     */
    private fun confidenceFor(samplesNearby: Int, closestMeters: Float): Float {
        val density = min(samplesNearby, SAMPLE_CAP).toFloat() / SAMPLE_CAP
        val proximity = 1f - (closestMeters / CUTOFF_METERS).coerceIn(0f, 1f)
        return (0.35f + 0.4f * density + 0.25f * proximity).coerceIn(0f, 1f)
    }

    /** Number of wall segments the straight line from query to sample crosses. */
    private fun wallsBetween(ax: Float, az: Float, bx: Float, bz: Float): Int {
        if (walls.isEmpty()) return 0
        var crossings = 0
        for (wall in walls) {
            if (segmentsCross(ax, az, bx, bz, wall.ax, wall.az, wall.bx, wall.bz)) {
                crossings++
                if (crossings >= MAX_COUNTED_WALLS) return crossings
            }
        }
        return crossings
    }

    data class Estimate(val rssiDbm: Int, val confidence: Float)

    private companion object {
        const val CUTOFF_METERS = 1.8f
        const val POWER = 2.4
        const val EPSILON = 0.01f

        /**
         * Weight left to a sample on the far side of one wall.
         *
         * An interior wall costs roughly 3–15 dB depending on what it is made
         * of, which is most of the difference between "fine" and "unusable".
         * The figure is a weighting, not a dB correction — it lets nearer,
         * unobstructed samples win, without pretending to know the material.
         */
        const val BLOCKED_WEIGHT = 0.12

        /** Past this, more readings stop meaningfully reducing the error. */
        const val SAMPLE_CAP = 16

        /** Two walls already means "not really measured here". */
        const val MAX_COUNTED_WALLS = 2
    }
}

/**
 * Whether two line segments properly cross.
 *
 * Orientation test rather than solving for the intersection point: it needs no
 * division, so it cannot blow up on the parallel and shared-endpoint cases that
 * a floor plan is full of.
 */
private fun segmentsCross(
    ax: Float, az: Float, bx: Float, bz: Float,
    cx: Float, cz: Float, dx: Float, dz: Float,
): Boolean {
    val d1 = cross(cx, cz, dx, dz, ax, az)
    val d2 = cross(cx, cz, dx, dz, bx, bz)
    val d3 = cross(ax, az, bx, bz, cx, cz)
    val d4 = cross(ax, az, bx, bz, dx, dz)

    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
        ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

/** Sign of the cross product: which side of line pq the point r falls on. */
private fun cross(
    px: Float, pz: Float, qx: Float, qz: Float, rx: Float, rz: Float,
): Float = (qx - px) * (rz - pz) - (qz - pz) * (rx - px)
