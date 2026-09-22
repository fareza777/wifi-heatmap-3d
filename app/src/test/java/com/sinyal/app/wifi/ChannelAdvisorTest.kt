package com.sinyal.app.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelAdvisorTest {

    private fun ap(channel: Int, band: Band, rssiDbm: Int, widthMhz: Int = 20) = NearbyAp(
        ssid = "ap-$channel",
        bssid = "aa:bb:cc:dd:ee:%02x".format(channel),
        rssiDbm = rssiDbm,
        frequencyMhz = ChannelAdvisor.centreFor(band, channel),
        isCurrent = false,
        channelWidthMhz = widthMhz,
        security = SecurityType.WPA2,
        generation = WifiGeneration.AC,
        isHidden = false,
        supportsRanging = false,
        supportsWps = false,
    )

    @Test fun emptyBandRatesEveryChannelTen() {
        val advice = ChannelAdvisor.evaluate(emptyList(), Band.GHZ_24)
        assertTrue(advice.channels.all { it.score == 10 })
        assertEquals(1, advice.recommended?.channel)
    }

    @Test fun aCrowdedChannelScoresLowerThanAFreeOne() {
        val networks = listOf(
            ap(6, Band.GHZ_24, -45),
            ap(6, Band.GHZ_24, -60),
            ap(7, Band.GHZ_24, -70),
        )
        val advice = ChannelAdvisor.evaluate(networks, Band.GHZ_24)
        val six = advice.channels.first { it.channel == 6 }
        val one = advice.channels.first { it.channel == 1 }

        assertTrue(six.score < one.score)
        assertEquals(2, six.onChannel)
        // Channel 7 is adjacent to 6, so it counts as overlapping there too.
        assertEquals(1, six.overlapping)
    }

    @Test fun recommendationOnlyPicksNonOverlapping24Channels() {
        val networks = listOf(ap(1, Band.GHZ_24, -50), ap(11, Band.GHZ_24, -80))
        val advice = ChannelAdvisor.evaluate(networks, Band.GHZ_24)
        assertEquals(6, advice.recommended?.channel)
    }

    @Test fun wideCarrierCountsAgainstEveryChannelItSpans() {
        // An 80 MHz AP centred on 5210 MHz overlaps every 20 MHz channel it spans.
        val networks = listOf(
            NearbyAp(
                ssid = "wide",
                bssid = "aa:bb:cc:00:00:01",
                rssiDbm = -55,
                frequencyMhz = 5210,
                isCurrent = false,
                channelWidthMhz = 80,
                security = SecurityType.WPA2,
                generation = WifiGeneration.AC,
                isHidden = false,
                supportsRanging = false,
                supportsWps = false,
            ),
        )
        val advice = ChannelAdvisor.evaluate(networks, Band.GHZ_5)
        val ch36 = advice.channels.first { it.channel == 36 }
        val ch40 = advice.channels.first { it.channel == 40 }
        val ch149 = advice.channels.first { it.channel == 149 }

        assertTrue(ch40.interferenceScore > 0f)   // inside the carrier's span
        assertEquals(0f, ch149.interferenceScore) // far outside it
        assertTrue(ch36.score < 10)
        assertTrue(ch40.score < 10)
        assertEquals(10, ch149.score)
    }

    @Test fun sixGigahertzChannelsAreRated() {
        val advice = ChannelAdvisor.evaluate(
            listOf(ap(35, Band.GHZ_6, -65)),
            Band.GHZ_6,
        )
        val ch35 = advice.channels.firstOrNull { it.channel == 35 }
        assertNotNull(ch35)
        assertTrue(ch35!!.score < 10)
        assertTrue(advice.channels.any { it.score == 10 })
    }

    @Test fun dfsChannelsAreFlagged() {
        val advice = ChannelAdvisor.evaluate(
            listOf(ap(52, Band.GHZ_5, -60)),
            Band.GHZ_5,
        )
        assertTrue(advice.channels.first { it.channel == 52 }.isDfs)
        assertFalse(advice.channels.first { it.channel == 36 }.isDfs)
    }

    @Test fun quietestChannelWinsWhenEverythingElseIsBusy() {
        val networks = buildList {
            (1..11).forEach { channel -> add(ap(channel, Band.GHZ_24, -55)) }
            add(ap(11, Band.GHZ_24, -50))
        }
        val advice = ChannelAdvisor.evaluate(networks, Band.GHZ_24)
        val recommended = advice.recommended
        assertNotNull(recommended)
        // 1, 6 and 11 are all occupied; the least crowded of the three wins.
        assertTrue(recommended!!.channel in NON_OVERLAPPING)
    }
}
