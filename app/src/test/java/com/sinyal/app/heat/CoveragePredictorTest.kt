package com.sinyal.app.heat

import com.sinyal.app.ar.FloorBounds
import com.sinyal.app.ar.WallSegment
import com.sinyal.app.wifi.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoveragePredictorTest {

    private val bounds = FloorBounds(minX = 0f, minZ = 0f, maxX = 6f, maxZ = 6f)

    @Test fun signalDropsWithDistance() {
        val near = CoveragePredictor.rssiAt(2f, 0, Band.GHZ_24)
        val far = CoveragePredictor.rssiAt(10f, 0, Band.GHZ_24)
        assertTrue(far < near)
    }

    @Test fun aWallCostsMoreAt5Ghz() {
        val drop24 = CoveragePredictor.rssiAt(3f, 0, Band.GHZ_24) -
            CoveragePredictor.rssiAt(3f, 1, Band.GHZ_24)
        val drop5 = CoveragePredictor.rssiAt(3f, 0, Band.GHZ_5) -
            CoveragePredictor.rssiAt(3f, 1, Band.GHZ_5)
        assertEquals(4, drop24)
        assertEquals(7, drop5)
    }

    @Test fun predictionCoversEveryTile() {
        val prediction = CoveragePredictor.predict(
            walls = emptyList(),
            bounds = bounds,
            routerX = 3f,
            routerZ = 3f,
            band = Band.GHZ_24,
        )
        assertFalse(prediction.cells.isEmpty())
        assertTrue(prediction.bestDbm > prediction.worstDbm)
        assertEquals(prediction.totalAreaSqM > 0f, true)
    }

    @Test fun wallBetweenRouterAndTileCountsOnce() {
        // Vertical wall at x=3 splitting the floor in half.
        val wall = WallSegment(ax = 3f, az = 0f, bx = 3f, bz = 6f)
        val prediction = CoveragePredictor.predict(
            walls = listOf(wall),
            bounds = bounds,
            routerX = 1.5f,
            routerZ = 3f,
            band = Band.GHZ_24,
        )
        val westTile = prediction.cells.minByOrNull { kotlin.math.abs(it.x - 1.5f) + kotlin.math.abs(it.z - 3f) }!!
        val eastTile = prediction.cells.minByOrNull { kotlin.math.abs(it.x - 4.5f) + kotlin.math.abs(it.z - 3f) }!!
        assertEquals(0, westTile.wallCrossings)
        assertEquals(1, eastTile.wallCrossings)
        assertTrue(eastTile.rssiDbm < westTile.rssiDbm)
    }

    @Test fun segmentsCrossIsExact() {
        assertTrue(CoveragePredictor.segmentsCross(0f, 0f, 4f, 0f, 2f, -1f, 2f, 1f))
        assertFalse(CoveragePredictor.segmentsCross(0f, 0f, 4f, 0f, 5f, -1f, 5f, 1f))
        // Parallel lines never cross.
        assertFalse(CoveragePredictor.segmentsCross(0f, 0f, 4f, 0f, 0f, 1f, 4f, 1f))
    }
}
