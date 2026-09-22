package com.sinyal.app.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentDiffTest {

    private fun ap(bssid: String, rssi: Int, freq: Int = 2437): NearbyAp = NearbyAp(
        ssid = "net-$bssid",
        bssid = bssid,
        rssiDbm = rssi,
        frequencyMhz = freq,
        isCurrent = false,
        channelWidthMhz = 20,
        security = SecurityType.WPA2,
        generation = WifiGeneration.AC,
        isHidden = false,
        supportsRanging = false,
        supportsWps = false,
    )

    private fun snapshot(vararg aps: NearbyAp) = EnvironmentSnapshot(atMs = 0L, aps = aps.toList())

    @Test fun identicalAirspaceReadsSame() {
        val before = snapshot(ap("aa", -50), ap("bb", -60))
        val result = EnvironmentDiff.diff(before, snapshot(ap("aa", -50), ap("bb", -60)))
        assertEquals(EnvironmentVerdict.SAME, result.verdict)
        assertTrue(result.newAps.isEmpty())
        assertTrue(result.departedAps.isEmpty())
        assertEquals(0.0, result.powerDeltaDb, 0.01)
    }

    @Test fun manyNewApsReadsBusier() {
        val before = snapshot(ap("aa", -50))
        val after = snapshot(ap("aa", -50), ap("bb", -55), ap("cc", -60), ap("dd", -65))
        val result = EnvironmentDiff.diff(before, after)
        assertEquals(EnvironmentVerdict.BUSIER, result.verdict)
        assertEquals(3, result.newAps.size)
    }

    @Test fun quietingReadsQuieter() {
        val before = snapshot(ap("aa", -40), ap("bb", -45), ap("cc", -50))
        val after = snapshot(ap("aa", -85))
        val result = EnvironmentDiff.diff(before, after)
        assertEquals(EnvironmentVerdict.QUIETER, result.verdict)
        assertTrue(result.powerDeltaDb <= -3.0)
    }

    @Test fun loudEnoughSwingFlagsTheAp() {
        val before = snapshot(ap("aa", -70))
        val after = snapshot(ap("aa", -45))
        val result = EnvironmentDiff.diff(before, after)
        assertEquals(1, result.louderAps.size)
        assertEquals("aa", result.louderAps.first().bssid)
    }

    @Test fun smallFadesAreIgnored() {
        val before = snapshot(ap("aa", -50))
        val after = snapshot(ap("aa", -55))
        val result = EnvironmentDiff.diff(before, after)
        assertTrue(result.louderAps.isEmpty())
        assertTrue(result.quieterAps.isEmpty())
    }

    @Test fun channelPowerMoveIsListed() {
        // Two APs leave channel 6 entirely; the channel's summed power collapses.
        val before = snapshot(ap("aa", -40, 2437), ap("bb", -42, 2437))
        val after = snapshot(ap("cc", -60, 5180))
        val result = EnvironmentDiff.diff(before, after)
        assertTrue(result.channelShifts.isNotEmpty())
        val ch6 = result.channelShifts.first { it.channel == 6 }
        assertEquals(Band.GHZ_24, ch6.band)
    }
}
