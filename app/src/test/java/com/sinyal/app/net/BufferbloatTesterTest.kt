package com.sinyal.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferbloatTesterTest {

    @Test fun gradeBoundaries() {
        assertEquals(BloatGrade.A, BufferbloatTester.gradeFor(0.0))
        assertEquals(BloatGrade.A, BufferbloatTester.gradeFor(14.9))
        assertEquals(BloatGrade.B, BufferbloatTester.gradeFor(15.0))
        assertEquals(BloatGrade.B, BufferbloatTester.gradeFor(49.9))
        assertEquals(BloatGrade.C, BufferbloatTester.gradeFor(50.0))
        assertEquals(BloatGrade.D, BufferbloatTester.gradeFor(150.0))
        assertEquals(BloatGrade.F, BufferbloatTester.gradeFor(400.0))
        assertEquals(BloatGrade.F, BufferbloatTester.gradeFor(2000.0))
        // Latency improving under load is still an A — no negative grades.
        assertEquals(BloatGrade.A, BufferbloatTester.gradeFor(-20.0))
    }

    @Test fun reportComputesMediansAndIncrease() {
        val report = BufferbloatTester.buildReport(
            idle = listOf(10.0, 12.0, 11.0, 20.0),
            loaded = listOf(40.0, 60.0, 50.0, 55.0, 400.0),
        )!!
        // idle sorted [10,11,12,20] → index 2 = 12.0
        assertEquals(12.0, report.idleMedianMs, 0.001)
        // loaded sorted [40,50,55,60,400] → index 2 = 55.0
        assertEquals(55.0, report.loadedMedianMs, 0.001)
        assertEquals(400.0, report.loadedMaxMs, 0.001)
        assertEquals(43.0, report.increaseMs, 0.001)
        assertEquals(BloatGrade.B, report.grade)
    }

    @Test fun emptyHalvesYieldNoReport() {
        assertNull(BufferbloatTester.buildReport(emptyList(), listOf(10.0)))
        assertNull(BufferbloatTester.buildReport(listOf(10.0), emptyList()))
    }

    @Test fun samplesKeepTheirPhase() {
        val report = BufferbloatTester.buildReport(listOf(10.0), listOf(20.0))!!
        assertEquals(2, report.samples.size)
        assertTrue(report.samples.first().ms == 10.0 && !report.samples.first().loaded)
        assertTrue(report.samples.last().loaded)
    }
}
