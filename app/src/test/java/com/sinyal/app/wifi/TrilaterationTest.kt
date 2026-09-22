package com.sinyal.app.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class TrilaterationTest {

    private fun anchorsAt(x: Double, y: Double, spots: List<Pair<Double, Double>>) =
        spots.map { (sx, sy) ->
            RangeAnchor(
                x = sx,
                y = sy,
                distanceM = sqrt((x - sx) * (x - sx) + (y - sy) * (y - sy)),
            )
        }

    @Test fun exactCirclesRecoverTheTransmitter() {
        val result = Trilateration.solve(
            anchorsAt(4.0, 3.0, listOf(0.0 to 0.0, 8.0 to 0.0, 0.0 to 6.0)),
        )
        assertNotNull(result)
        assertEquals(4.0, result!!.x, 1e-6)
        assertEquals(3.0, result.y, 1e-6)
        assertTrue(result.rmseM < 1e-6)
    }

    @Test fun noisyRangesStillLandNearTheTransmitter() {
        val anchors = anchorsAt(
            5.0, 5.0,
            listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0, 10.0 to 10.0),
        ).mapIndexed { index, anchor ->
            anchor.copy(distanceM = anchor.distanceM * (1.0 + (index % 2) * 0.02))
        }
        val result = Trilateration.solve(anchors)
        assertNotNull(result)
        assertEquals(5.0, result!!.x, 0.5)
        assertEquals(5.0, result.y, 0.5)
        assertTrue(result.rmseM > 0.0)
    }

    @Test fun twoAnchorsCannotFixAPoint() {
        assertNull(
            Trilateration.solve(
                anchorsAt(4.0, 3.0, listOf(0.0 to 0.0, 8.0 to 0.0)),
            ),
        )
    }

    @Test fun collinearAnchorsAreDegenerate() {
        val collinear = listOf(
            RangeAnchor(0.0, 0.0, 5.0),
            RangeAnchor(1.0, 0.0, 4.0),
            RangeAnchor(2.0, 0.0, 3.0),
            RangeAnchor(3.0, 0.0, 2.83),
        )
        assertNull(Trilateration.solve(collinear))
    }

    @Test fun moreAnchorsShrinkTheErrorWhenOneLies() {
        val good = anchorsAt(
            4.0, 3.0,
            listOf(0.0 to 0.0, 8.0 to 0.0, 0.0 to 6.0, 8.0 to 6.0),
        )
        val withLie = good + RangeAnchor(4.0, 3.0, 0.2)

        val clean = Trilateration.solve(good)!!
        val polluted = Trilateration.solve(withLie)!!

        assertTrue(clean.rmseM < polluted.rmseM)
        // The estimate is pulled toward the liar but stays in the room.
        assertEquals(4.0, polluted.x, 1.5)
        assertEquals(3.0, polluted.y, 1.5)
    }
}
