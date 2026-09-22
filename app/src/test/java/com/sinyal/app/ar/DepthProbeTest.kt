package com.sinyal.app.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthProbeTest {

    private val floorY = 0f
    private val wallHeight = 2.4f

    /** Depth points laid out as a vertical wall surface along the X axis. */
    private fun wallPoints(fromX: Float, toX: Float, z: Float, step: Float = 0.1f): List<DepthPoint> {
        val points = mutableListOf<DepthPoint>()
        var x = fromX
        while (x <= toX) {
            var y = floorY + 0.2f
            while (y < floorY + wallHeight * 0.8f) {
                points += DepthPoint(x, y, z)
                y += 0.4f
            }
            x += step
        }
        return points
    }

    @Test fun wallExtendsToDepthEvidence() {
        // AR planes saw a 2 m wall; depth sees it running 3.5 m.
        val wall = WallSegment(ax = 0f, az = 2f, bx = 2f, bz = 2f)
        val refined = DepthProbe.refineWalls(
            walls = listOf(wall),
            points = wallPoints(fromX = -1f, toX = 3.5f, z = 2f),
            floorY = floorY,
            wallHeight = wallHeight,
        )
        assertEquals(1, refined.size)
        assertEquals(-1f, refined[0].ax, 0.01f)
        assertEquals(3.5f, refined[0].bx, 0.01f)
    }

    @Test fun extensionIsCapped() {
        // Depth evidence 5 m past the end — beyond MAX_EXTEND_M it is another wall.
        val wall = WallSegment(ax = 0f, az = 2f, bx = 2f, bz = 2f)
        val refined = DepthProbe.refineWalls(
            walls = listOf(wall),
            points = wallPoints(fromX = 0f, toX = 10f, z = 2f),
            floorY = floorY,
            wallHeight = wallHeight,
        )
        assertEquals(3.6f, refined[0].bx, 0.01f)
    }

    @Test fun pointsOffTheLineAreIgnored() {
        // A point cloud 1 m behind the wall — outside snap range, no change.
        val wall = WallSegment(ax = 0f, az = 2f, bx = 2f, bz = 2f)
        val refined = DepthProbe.refineWalls(
            walls = listOf(wall),
            points = wallPoints(fromX = -5f, toX = 5f, z = 3f),
            floorY = floorY,
            wallHeight = wallHeight,
        )
        assertEquals(wall, refined[0])
    }

    @Test fun emptyInputsPassThrough() {
        val wall = WallSegment(ax = 0f, az = 2f, bx = 2f, bz = 2f)
        assertEquals(
            listOf(wall),
            DepthProbe.refineWalls(listOf(wall), emptyList(), floorY, wallHeight),
        )
        assertTrue(DepthProbe.refineWalls(emptyList(), wallPoints(0f, 2f, 2f), floorY, wallHeight).isEmpty())
    }
}
