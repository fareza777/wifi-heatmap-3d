package com.sinyal.app.ar

/** A wall as a horizontal line segment on the floor plane, drawn at uniform height. */
data class WallSegment(
    val ax: Float,
    val az: Float,
    val bx: Float,
    val bz: Float,
) {
    val centerX: Float get() = (ax + bx) / 2f
    val centerZ: Float get() = (az + bz) / 2f
    val length: Float get() = kotlin.math.hypot(bx - ax, bz - az)

    /** Rotation about Y, in degrees, for a box aligned to this segment. */
    val yawDegrees: Float
        get() = -Math.toDegrees(kotlin.math.atan2((bz - az).toDouble(), (bx - ax).toDouble()))
            .toFloat()
}

/** Axis-aligned footprint of the scanned space, in metres. */
data class FloorBounds(
    val minX: Float,
    val minZ: Float,
    val maxX: Float,
    val maxZ: Float,
) {
    val width: Float get() = maxX - minX
    val depth: Float get() = maxZ - minZ
    val centerX: Float get() = (minX + maxX) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
    val areaSqM: Float get() = width * depth

    /** Grows the box so a marker sitting exactly on the edge still has floor under it. */
    fun expanded(by: Float): FloorBounds =
        FloorBounds(minX - by, minZ - by, maxX + by, maxZ + by)

    /** Widens the box until it contains every endpoint of [walls]. */
    fun including(walls: List<WallSegment>): FloorBounds {
        if (walls.isEmpty()) return this
        val xs = walls.flatMap { listOf(it.ax, it.bx) }
        val zs = walls.flatMap { listOf(it.az, it.bz) }
        return FloorBounds(
            minX = minOf(minX, xs.min()),
            minZ = minOf(minZ, zs.min()),
            maxX = maxOf(maxX, xs.max()),
            maxZ = maxOf(maxZ, zs.max()),
        )
    }
}

/**
 * The reconstructed space.
 *
 * Walls are kept as segments rather than a mesh: the dollhouse renders each one
 * as a thin box, which both reads as a real wall and avoids polygon triangulation.
 */
data class RoomModel(
    val bounds: FloorBounds,
    val walls: List<WallSegment>,
    val floorY: Float,
    val wallHeight: Float,
    /** True when no vertical planes were seen and the perimeter was inferred. */
    val wallsAreEstimated: Boolean,
) {
    companion object {
        const val DEFAULT_WALL_HEIGHT = 2.5f

        /**
         * A box around the walked area, used when ARCore saw no vertical planes.
         *
         * Pointing the phone at the floor while walking is the normal way to
         * scan, and it yields no wall detections at all — leaving the result
         * with zero walls reads as a failure, when in fact the footprint is
         * perfectly well known. These are labelled as estimated in the UI.
         */
        fun perimeterOf(b: FloorBounds): List<WallSegment> = listOf(
            WallSegment(b.minX, b.minZ, b.maxX, b.minZ),
            WallSegment(b.maxX, b.minZ, b.maxX, b.maxZ),
            WallSegment(b.maxX, b.maxZ, b.minX, b.maxZ),
            WallSegment(b.minX, b.maxZ, b.minX, b.minZ),
        )

        /** Falls back to the sampled footprint when no planes were detected. */
        fun fromSamplesOnly(grid: SampleGrid): RoomModel? {
            val cells = grid.occupiedCells
            if (cells.isEmpty()) return null
            val bounds = FloorBounds(
                minX = cells.minOf { it.x },
                minZ = cells.minOf { it.z },
                maxX = cells.maxOf { it.x },
                maxZ = cells.maxOf { it.z },
            ).expanded(grid.cellSizeMeters)
            return RoomModel(
                bounds = bounds,
                walls = perimeterOf(bounds),
                floorY = cells.minOf { it.y } - 1.2f,
                wallHeight = DEFAULT_WALL_HEIGHT,
                wallsAreEstimated = true,
            )
        }
    }
}
