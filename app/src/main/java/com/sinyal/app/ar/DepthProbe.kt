package com.sinyal.app.ar

import android.media.Image
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import kotlin.math.abs
import kotlin.math.hypot

/** A point on a real surface, in the scan's corrected world frame. */
data class DepthPoint(val x: Float, val y: Float, val z: Float)

/**
 * ARCore depth, used to finish the room the planes started.
 *
 * What the SDK gives this app is not the rendered depth cloud the AR demos
 * show, but a sparse set of world-space surface points — enough to answer the
 * one question the plane harvester gets wrong: where a wall *ends*. ARCore
 * planes under-cover: a wall half-hidden behind furniture yields a short
 * segment, and the floor plan then draws a gap where a wall clearly continues.
 *
 * Two deliberate constraints, learned the hard way in this codebase:
 *
 *  - Depth is only enabled when the device has real depth hardware
 *    ([Config.DepthMode.AUTOMATIC] supported). Depth-from-motion on a phone
 *    without a depth sensor is CPU work that starves feature tracking — that
 *    is the failure the `DISABLED` setting in the capture screen is there for.
 *  - Frames are sampled, not consumed: every [SAMPLE_EVERY_FRAMES]th frame
 *    contributes a strided subset of its depth image, so the point budget
 *    stays small and the AR thread is never held.
 *
 * Images are closed in the same frame — ARCore recycles the buffers.
 */
class DepthProbe {

    /** True once a session reported real depth hardware and the mode was set. */
    var active = false
        private set
    private var supportChecked = false

    private val points = ArrayDeque<DepthPoint>()

    val count: Int get() = points.size

    fun snapshot(): List<DepthPoint> = points.toList()

    /**
     * Turns depth on if — and only if — this device computes it in hardware.
     * Called on the render thread next to the plane-finding config check.
     */
    fun ensureDepth(session: Session) {
        if (supportChecked) return
        supportChecked = true
        val supported = runCatching {
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        }.getOrDefault(false)
        if (!supported) return

        runCatching {
            val config = session.config
            if (config.depthMode != Config.DepthMode.AUTOMATIC) {
                config.depthMode = Config.DepthMode.AUTOMATIC
                session.configure(config)
            }
            active = true
        }
    }

    /**
     * Unprojects a strided subset of the raw depth image into world space.
     *
     * [offsetX]/[offsetY]/[offsetZ] are the relocalisation correction the
     * capture already maintains — depth points must land in the same frame as
     * the walls they refine, or they would extend yesterday's coordinate
     * system.
     */
    fun onFrame(frame: Frame, offsetX: Float, offsetY: Float, offsetZ: Float) {
        if (!active) return

        val depth = runCatching { frame.acquireRawDepthImage16Bits() }.getOrNull()
            ?: return
        val confidence = runCatching { frame.acquireRawDepthConfidenceImage() }
            .getOrNull()

        try {
            val camera = frame.camera
            if (camera.trackingState != com.google.ar.core.TrackingState.TRACKING) return

            // ImageIntrinsics describe the CPU frame, which is not the depth
            // image's resolution — the pinhole terms are rescaled onto it.
            val intrinsics = camera.imageIntrinsics
            val dims = intrinsics.imageDimensions
            val focal = intrinsics.focalLength
            val principal = intrinsics.principalPoint
            val scaleX = depth.width / dims[0].toFloat()
            val scaleY = depth.height / dims[1].toFloat()
            val fx = focal[0] * scaleX
            val fy = focal[1] * scaleY
            val cx = principal[0] * scaleX
            val cy = principal[1] * scaleY

            val depthPlane = depth.planes[0]
            val depthBuffer = depthPlane.buffer
            val depthStride = depthPlane.rowStride / 2 // shorts per row
            val confBuffer = confidence?.planes?.get(0)?.buffer
            val confStride = confidence?.planes?.get(0)?.rowStride ?: 0

            // u,v index the depth image itself; intrinsics were already
            // rescaled onto it above.
            val width = depth.width
            val height = depth.height

            var v = STRIDE / 2
            while (v < height) {
                var u = STRIDE / 2
                while (u < width) {
                    // ByteBuffer indices are bytes; the plane is shorts.
                    val raw = depthBuffer.getShort((v * depthStride + u) * 2)
                    // Top 3 bits are metadata in DEPTH16; the rest is mm.
                    val depthMm = raw.toInt() and 0x1FFF
                    if (depthMm in MIN_DEPTH_MM..MAX_DEPTH_MM) {
                        val passesConfidence = confBuffer == null || confStride <= 0 ||
                            (confBuffer.get(v * confStride + u).toInt() and 0xFF) >= MIN_CONFIDENCE
                        if (passesConfidence) {
                            val meters = depthMm / 1000f
                            val point = camera.pose.transformPoint(
                                floatArrayOf(
                                    (u - cx) * meters / fx,
                                    (v - cy) * meters / fy,
                                    -meters,
                                ),
                            )
                            if (points.size >= MAX_POINTS) points.removeFirst()
                            points.addLast(
                                DepthPoint(
                                    x = point[0] - offsetX,
                                    y = point[1] - offsetY,
                                    z = point[2] - offsetZ,
                                ),
                            )
                        }
                    }
                    u += STRIDE
                }
                v += STRIDE
            }
        } finally {
            runCatching { confidence?.close() }
            runCatching { depth.close() }
        }
    }

    /**
     * Returns [room] with each detected wall extended along its axis wherever
     * the depth cloud saw wall surface past the segment ARCore reported.
     *
     * A point supports an extension when it sits within [SNAP_M] of the wall's
     * line (perpendicular) and inside the wall's height band — the floor, the
     * ceiling and the room's contents are all filtered by those two tests.
     * Estimated perimeters (no real planes seen) are left alone: extending a
     * guess just makes a confident guess.
     */
    fun refine(room: RoomModel): RoomModel {
        if (room.wallsAreEstimated || room.walls.isEmpty() || points.isEmpty()) return room
        val refined = refineWalls(
            walls = room.walls,
            points = points.toList(),
            floorY = room.floorY,
            wallHeight = room.wallHeight,
        )
        return room.copy(walls = refined)
    }

    companion object {
        /** Pure math, kept static so the JVM tests never touch an ARCore class. */
        fun refineWalls(
            walls: List<WallSegment>,
            points: List<DepthPoint>,
            floorY: Float,
            wallHeight: Float,
        ): List<WallSegment> {
            if (walls.isEmpty() || points.isEmpty()) return walls

            val yMin = floorY + 0.05f
            val yMax = floorY + wallHeight * 0.9f
            val usable = points.filter { it.y in yMin..yMax }
            if (usable.isEmpty()) return walls

            return walls.map { wall ->
                val dx = wall.bx - wall.ax
                val dz = wall.bz - wall.az
                val length = hypot(dx, dz)
                if (length < 0.05f) return@map wall

                val ux = dx / length
                val uz = dz / length
                // Normal: perpendicular in the floor plane.
                val nx = -uz
                val nz = ux

                var minT = 0f
                var maxT = length
                for (p in usable) {
                    val px = p.x - wall.ax
                    val pz = p.z - wall.az
                    val perpendicular = abs(px * nx + pz * nz)
                    if (perpendicular > SNAP_M) continue
                    val t = px * ux + pz * uz
                    if (t < minT) minT = t
                    if (t > maxT) maxT = t
                }

                minT = maxOf(minT, -MAX_EXTEND_M)
                maxT = minOf(maxT, length + MAX_EXTEND_M)
                if (minT == 0f && maxT == length) wall else WallSegment(
                    ax = wall.ax + ux * minT,
                    az = wall.az + uz * minT,
                    bx = wall.ax + ux * maxT,
                    bz = wall.az + uz * maxT,
                )
            }
        }

        /** How far off a wall's line a point may sit and still count as its surface. */
        private const val SNAP_M = 0.40f

        /** Depth evidence further than this past an endpoint is a different wall. */
        private const val MAX_EXTEND_M = 1.6f

        /** Every nth pixel of each axis — ~480 candidates per raw depth frame. */
        private const val STRIDE = 8

        /** Ring bound; a scan never needs more surface than this. */
        private const val MAX_POINTS = 12_000

        /** Sensor range kept: closer is the hand, further is unreliable. */
        private const val MIN_DEPTH_MM = 300
        private const val MAX_DEPTH_MM = 8_000

        /** Confidence byte floor (0–255); below this the depth is guessed. */
        private const val MIN_CONFIDENCE = 64
    }
}
