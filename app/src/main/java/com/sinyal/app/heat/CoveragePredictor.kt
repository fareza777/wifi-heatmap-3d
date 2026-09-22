package com.sinyal.app.heat

import com.sinyal.app.ar.FloorBounds
import com.sinyal.app.ar.WallSegment
import com.sinyal.app.wifi.Band
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** One predicted floor tile. */
data class PredictedCell(
    val x: Float,
    val z: Float,
    val rssiDbm: Int,
    /** How many walls sit between the router and this tile. */
    val wallCrossings: Int,
)

data class CoveragePrediction(
    val cells: List<PredictedCell>,
    /** Cell size the grid was stepped at, in metres — needed to draw it. */
    val cellSizeMeters: Float,
    val bounds: FloorBounds,
    /** Tiles too faint for reliable streaming (< [WEAK_DBM]). */
    val weakAreaSqM: Float,
    val totalAreaSqM: Float,
    val bestDbm: Int,
    val worstDbm: Int,
) {
    val weakFraction: Float get() = if (totalAreaSqM <= 0f) 0f else weakAreaSqM / totalAreaSqM
}

/**
 * A pocket ray-tracer, honest about what it is.
 *
 * Research-grade engines like Sionna trace multipath reflections through a
 * material-aware scene; this does the much smaller thing a floor plan can
 * support: one direct path per tile, log-distance path loss, and a fixed
 * attenuation each time the path crosses a detected wall segment. That is
 * still enough to answer the question people actually ask — "if I move the
 * router over there, does the back bedroom stop dying?" — because wall count
 * dominates the answer once the router is somewhere sane.
 *
 * Wall losses are order-of-magnitude honest rather than material-true: a
 * plasterboard wall at 2.4 GHz really does eat roughly 3–4 dB, and 5 GHz is
 * meaningfully worse through the same wall — the model does not pretend to
 * know brick from gypsum.
 */
object CoveragePredictor {

    fun predict(
        walls: List<WallSegment>,
        bounds: FloorBounds,
        routerX: Float,
        routerZ: Float,
        band: Band,
        cellSizeMeters: Float = CELL_SIZE_M,
    ): CoveragePrediction {
        val expanded = bounds.expanded(CELL_SIZE_M)
        val cols = max(1, ceil(expanded.width / cellSizeMeters).toInt())
        val rows = max(1, ceil(expanded.depth / cellSizeMeters).toInt())

        val cells = ArrayList<PredictedCell>(cols * rows)
        var weak = 0
        var best = Int.MIN_VALUE
        var worst = Int.MAX_VALUE

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = expanded.minX + (col + 0.5f) * cellSizeMeters
                val z = expanded.minZ + (row + 0.5f) * cellSizeMeters
                val distance = max(0.3f, hypot(x - routerX, z - routerZ))
                val crossings = walls.count { wall ->
                    segmentsCross(routerX, routerZ, x, z, wall.ax, wall.az, wall.bx, wall.bz)
                }
                val rssi = rssiAt(distance, crossings, band)
                if (rssi < WEAK_DBM) weak++
                best = max(best, rssi)
                worst = min(worst, rssi)
                cells += PredictedCell(x, z, rssi, crossings)
            }
        }

        val area = expanded.width * expanded.depth
        return CoveragePrediction(
            cells = cells,
            cellSizeMeters = cellSizeMeters,
            bounds = expanded,
            weakAreaSqM = weak * cellSizeMeters * cellSizeMeters,
            totalAreaSqM = area,
            bestDbm = best,
            worstDbm = worst,
        )
    }

    /**
     * Free-space reference at one metre minus log-distance loss, minus a flat
     * penalty per wall on the way — the three terms a building actually
     * contributes.
     */
    internal fun rssiAt(distanceMeters: Float, wallCrossings: Int, band: Band): Int {
        val (refDbm, wallDb) = when (band) {
            Band.GHZ_24 -> REF_DBM_24 to WALL_LOSS_DB_24
            Band.GHZ_5 -> REF_DBM_5 to WALL_LOSS_DB_5
            Band.GHZ_6 -> REF_DBM_5 to WALL_LOSS_DB_5
            Band.UNKNOWN -> REF_DBM_24 to WALL_LOSS_DB_24
        }
        val freeSpace = refDbm - 10.0 * PATH_LOSS_EXPONENT * log10(distanceMeters.toDouble())
        return (freeSpace - wallCrossings * wallDb)
            .toInt()
            .coerceIn(FLOOR_DBM, CEIL_DBM)
    }

    /**
     * 2D segment intersection: whether the ray A→B crosses C→D.
     * Standard orientation test — the router tile and wall endpoints are all
     * floor-plane coordinates, so 3D reduces to this.
     */
    internal fun segmentsCross(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Boolean {
        fun orient(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float): Float =
            (qx - px) * (ry - py) - (qy - py) * (rx - px)

        val d1 = orient(cx, cy, dx, dy, ax, ay)
        val d2 = orient(cx, cy, dx, dy, bx, by)
        val d3 = orient(ax, ay, bx, by, cx, cy)
        val d4 = orient(ax, ay, bx, by, dx, dy)

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** RSSI seen 1 m from a typical home router — the anchor everything decays from. */
    private const val REF_DBM_24 = -38.0
    private const val REF_DBM_5 = -42.0

    /** Typical indoor exponent; 2.0 is outdoors, walls are charged separately. */
    private const val PATH_LOSS_EXPONENT = 2.0

    /** Per-crossing penalty: plasterboard-ish, honest on both bands. */
    private const val WALL_LOSS_DB_24 = 4.0
    private const val WALL_LOSS_DB_5 = 7.0

    private const val CELL_SIZE_M = 0.45f
    private const val WEAK_DBM = -70
    private const val FLOOR_DBM = -95
    private const val CEIL_DBM = -25
}
