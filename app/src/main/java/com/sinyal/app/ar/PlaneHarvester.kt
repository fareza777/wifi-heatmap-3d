package com.sinyal.app.ar

import com.google.ar.core.Plane
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Turns ARCore's detected planes into a room we can draw.
 *
 * ARCore reports many overlapping planes that grow and merge as the scan runs,
 * so subsumed planes are dropped and only the largest surviving ones are kept.
 * Wall angles are snapped to the room's dominant axis when they are already
 * close to it — real rooms are square, and a couple of degrees of drift is what
 * makes a reconstruction look sloppy rather than deliberate.
 */
object PlaneHarvester {

    /**
     * Builds the room from geometry captured while the planes were still being
     * tracked.
     *
     * Reading the planes at the end of the walk does not work: ARCore reports an
     * extent of zero for any plane that has gone PAUSED, and a plane pauses as
     * soon as you walk away from it. Measured on a real scan — the same wall read
     * 0.93 m wide while TRACKING and 0.00 m a few seconds later — which is why
     * walls appeared during a scan and were always gone from the result.
     */
    fun build(segments: List<WallSegment>, floorY: Float?, grid: SampleGrid): RoomModel? {
        if (segments.isEmpty() && grid.occupiedCells.isEmpty()) return null

        val sampled = sampledBounds(grid)
        val merged = mergeCollinear(segments)
        val snapped = snapToDominantAxis(merged)
            .filter { it.length >= MIN_WALL_LENGTH }
            .sortedByDescending { it.length }
            .take(MAX_WALLS)

        val bounds = sampled.including(snapped)

        return RoomModel(
            bounds = bounds,
            walls = snapped.ifEmpty { RoomModel.perimeterOf(bounds) },
            floorY = floorY
                ?: (grid.occupiedCells.minOfOrNull { it.y }?.minus(1.2f) ?: 0f),
            wallHeight = RoomModel.DEFAULT_WALL_HEIGHT,
            wallsAreEstimated = snapped.isEmpty(),
        )
    }

    /**
     * The floor-level segment of a wall patch, or null when the patch carries no
     * usable size — which is what a paused plane reports.
     */
    fun segmentOf(plane: Plane): WallSegment? {
        if (plane.horizontalExtent() < MIN_PATCH_LENGTH) return null

        // The floor-running axis has to actually run along the floor. A sloped
        // surface — the back of a chair, a stacked box — is reported as VERTICAL
        // by ARCore with both in-plane axes tilted about 45 degrees, and taking
        // its "width" produces a wall that is not one.
        val axis = plane.horizontalAxis()
        if (kotlin.math.abs(axis[1]) > MAX_AXIS_TILT) return null

        return plane.toSegment()
    }

    /**
     * Which of a vertical plane's two in-plane axes runs along the floor.
     *
     * ARCore does not promise that local X is the horizontal one — for a wall it
     * may just as well point at the ceiling. Picking by the smaller vertical
     * component is the only reliable way to tell them apart.
     */
    private fun Plane.horizontalAxis(): FloatArray {
        val x = centerPose.xAxis
        val z = centerPose.zAxis
        return if (abs(x[1]) <= abs(z[1])) x else z
    }

    /** Length of the wall along the floor, whichever local axis that turns out to be. */
    private fun Plane.horizontalExtent(): Float {
        val x = centerPose.xAxis
        val z = centerPose.zAxis
        return if (abs(x[1]) <= abs(z[1])) extentX else extentZ
    }

    private fun Plane.toSegment(): WallSegment {
        val pose = centerPose
        val axis = horizontalAxis()
        val half = horizontalExtent() / 2f
        return WallSegment(
            ax = pose.tx() - axis[0] * half,
            az = pose.tz() - axis[2] * half,
            bx = pose.tx() + axis[0] * half,
            bz = pose.tz() + axis[2] * half,
        )
    }

    /**
     * Welds the fragments of one wall back into one segment.
     *
     * ARCore rarely hands over a wall whole. Sweeping a camera along a hallway
     * produces a chain of half-metre patches at slightly different angles, and
     * treating each as its own wall both looked like debris and meant none of
     * them survived a sensible minimum length.
     *
     * Two patches are the same wall when they point the same way (within
     * [MERGE_ANGLE_DEGREES], counting a 180° flip as the same line) and lie on
     * the same line (within [MERGE_OFFSET_METERS] perpendicular). Merging is
     * repeated until nothing more combines, so a chain joins end to end.
     */
    private fun mergeCollinear(walls: List<WallSegment>): List<WallSegment> {
        if (walls.size < 2) return walls

        var current = walls
        repeat(MERGE_PASSES) {
            val merged = mergeOnce(current)
            if (merged.size == current.size) return merged
            current = merged
        }
        return current
    }

    private fun mergeOnce(walls: List<WallSegment>): List<WallSegment> {
        val used = BooleanArray(walls.size)
        val out = mutableListOf<WallSegment>()

        for (i in walls.indices) {
            if (used[i]) continue
            var wall = walls[i]
            used[i] = true

            for (j in i + 1 until walls.size) {
                if (used[j]) continue
                if (!sameLine(wall, walls[j])) continue
                wall = union(wall, walls[j])
                used[j] = true
            }
            out += wall
        }
        return out
    }

    private fun sameLine(a: WallSegment, b: WallSegment): Boolean {
        val angle = angleBetween(a.yawDegrees, b.yawDegrees)
        if (angle > MERGE_ANGLE_DEGREES) return false

        // Perpendicular distance from b's centre to the infinite line through a.
        val radians = Math.toRadians(-a.yawDegrees.toDouble())
        val dirX = cos(radians).toFloat()
        val dirZ = sin(radians).toFloat()
        val offX = b.centerX - a.centerX
        val offZ = b.centerZ - a.centerZ
        val perpendicular = abs(offX * dirZ - offZ * dirX)
        if (perpendicular > MERGE_OFFSET_METERS) return false

        // And they must actually be near each other along the line, or two walls
        // of a long corridor would weld into one impossible slab.
        val along = abs(offX * dirX + offZ * dirZ)
        return along <= (a.length + b.length) / 2f + MERGE_GAP_METERS
    }

    /** Smallest angle between two headings, treating opposite directions as equal. */
    private fun angleBetween(a: Float, b: Float): Float {
        var diff = abs(a - b) % 180f
        if (diff > 90f) diff = 180f - diff
        return diff
    }

    /** The segment spanning both inputs, along the first one's direction. */
    private fun union(a: WallSegment, b: WallSegment): WallSegment {
        val radians = Math.toRadians(-a.yawDegrees.toDouble())
        val dirX = cos(radians).toFloat()
        val dirZ = sin(radians).toFloat()
        val originX = a.centerX
        val originZ = a.centerZ

        fun project(x: Float, z: Float) = (x - originX) * dirX + (z - originZ) * dirZ

        val points = listOf(
            project(a.ax, a.az), project(a.bx, a.bz),
            project(b.ax, b.az), project(b.bx, b.bz),
        )
        val low = points.min()
        val high = points.max()

        return WallSegment(
            ax = originX + dirX * low,
            az = originZ + dirZ * low,
            bx = originX + dirX * high,
            bz = originZ + dirZ * high,
        )
    }

    /** Rounds near-axial walls onto the modal orientation; leaves genuine angles alone. */
    private fun snapToDominantAxis(walls: List<WallSegment>): List<WallSegment> {
        if (walls.isEmpty()) return walls
        val base = walls.maxByOrNull { it.length }?.yawDegrees ?: return walls

        return walls.map { wall ->
            val relative = wall.yawDegrees - base
            val nearestRightAngle = (relative / 90f).roundToInt() * 90f
            if (abs(relative - nearestRightAngle) > SNAP_TOLERANCE_DEGREES) return@map wall

            val target = Math.toRadians((base + nearestRightAngle).toDouble())
            val halfLength = wall.length / 2f
            val dx = cos(target).toFloat() * halfLength
            val dz = -sin(target).toFloat() * halfLength
            WallSegment(
                ax = wall.centerX - dx,
                az = wall.centerZ - dz,
                bx = wall.centerX + dx,
                bz = wall.centerZ + dz,
            )
        }
    }

    /**
     * The footprint is taken from where readings actually exist, never from
     * ARCore's plane extents.
     *
     * Detected floor planes keep growing outward and routinely span far more
     * than the home itself; using them produced a 23 m box for a normal room,
     * which in turn forced the heatmap tiles up to 5 m across.
     */
    private fun sampledBounds(grid: SampleGrid): FloorBounds {
        val cells = grid.occupiedCells
        if (cells.isEmpty()) return FloorBounds(-2f, -2f, 2f, 2f)
        return FloorBounds(
            minX = cells.minOf { it.x },
            minZ = cells.minOf { it.z },
            maxX = cells.maxOf { it.x },
            maxZ = cells.maxOf { it.z },
        ).expanded(grid.cellSizeMeters)
    }

    /**
     * Smallest patch worth keeping as raw input.
     *
     * ARCore seeds a vertical plane at roughly a hand's width and grows it, so
     * anything above this is a real surface rather than noise — and several of
     * them in a row merge into a wall.
     */
    private const val MIN_PATCH_LENGTH = 0.25f

    /**
     * How far the along-the-floor axis may lean and still count.
     *
     * A true wall reports very close to zero here; a measured tilted surface
     * came back at 0.66, which this rejects.
     */
    private const val MAX_AXIS_TILT = 0.35f

    /** Shortest merged run that counts as a wall rather than a piece of furniture. */
    private const val MIN_WALL_LENGTH = 0.5f

    private const val MAX_WALLS = 24
    private const val SNAP_TOLERANCE_DEGREES = 12f

    /** Patches of one wall arrive a few degrees apart as the camera sweeps. */
    private const val MERGE_ANGLE_DEGREES = 14f

    /** Plaster is not flat to the millimetre, and neither is the tracking. */
    private const val MERGE_OFFSET_METERS = 0.30f

    /** A doorway or a poster can leave this much unseen mid-wall. */
    private const val MERGE_GAP_METERS = 0.9f

    /** Chains of patches need a few rounds to weld end to end. */
    private const val MERGE_PASSES = 4
}
