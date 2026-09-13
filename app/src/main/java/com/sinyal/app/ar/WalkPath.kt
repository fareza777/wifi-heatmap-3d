package com.sinyal.app.ar

import kotlin.math.hypot

/**
 * One vertex of the route the user actually walked, on the floor plane.
 *
 * [startsRun] marks a vertex the walk did not reach on foot — the first point
 * after ARCore relocalised and the pose teleported. Joining across one of those
 * draws a corridor through rooms nobody entered, and the heatmap then paints
 * along it.
 */
data class PathPoint(
    val x: Float,
    val z: Float,
    val startsRun: Boolean = false,
)

/**
 * The route walked during a scan.
 *
 * Kept separately from the sample grid because it answers a different question:
 * the grid says where readings exist, the path says where the user physically
 * went. Interpolation is trusted near the path and discarded away from it, so
 * the heatmap takes the shape of the walk instead of spilling into a rectangle
 * covering rooms nobody entered.
 */
class WalkPath(val points: List<PathPoint>) {

    val isEmpty: Boolean get() = points.isEmpty()

    /** Shortest distance from a point to the walked route, in metres. */
    fun distanceTo(x: Float, z: Float): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) return hypot(x - points[0].x, z - points[0].z)

        var best = Float.MAX_VALUE
        for (index in 0 until points.lastIndex) {
            val next = points[index + 1]
            // A teleport is not a segment the user walked along; measure to the
            // landing point itself rather than to the imaginary line.
            if (next.startsRun) {
                val d = hypot(x - next.x, z - next.z)
                if (d < best) best = d
                continue
            }
            val d = distanceToSegment(x, z, points[index], next)
            if (d < best) best = d
        }
        return best
    }

    private fun distanceToSegment(
        px: Float,
        pz: Float,
        a: PathPoint,
        b: PathPoint,
    ): Float {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val lengthSquared = dx * dx + dz * dz
        if (lengthSquared == 0f) return hypot(px - a.x, pz - a.z)

        // Projection of P onto AB, clamped to the segment.
        val t = (((px - a.x) * dx + (pz - a.z) * dz) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (a.x + t * dx), pz - (a.z + t * dz))
    }

    companion object {
        val Empty = WalkPath(emptyList())

        /** Distance the user must cover before a new vertex is worth keeping. */
        const val VERTEX_SPACING_METERS = 0.35f
    }
}
