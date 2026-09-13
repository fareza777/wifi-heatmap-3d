package com.sinyal.app.ads

import org.junit.Assert.*
import org.junit.Test

class AdBreakPolicyTest {
    @Test fun launchAndHistoryNeverShowInterstitial() {
        assertFalse(AdBreakPolicy(0).resultsClosed(900_000, true, true))
    }
    @Test fun twoScansDoNotShowAnAdEvenAfterCooldown() {
        val policy = AdBreakPolicy(0)
        repeat(2) { policy.scanCompleted() }
        assertFalse(policy.resultsClosed(900_000, true, true))
    }
    @Test fun threeScansWaitForThreeMinuteCooldown() {
        val policy = AdBreakPolicy(0)
        repeat(3) { policy.scanCompleted() }
        assertFalse(policy.resultsClosed(179_999, true, true))
        policy.scanCompleted()
        assertTrue(policy.resultsClosed(180_000, true, true))
    }
    @Test fun adRequiresEligibilityAndLoadedCreativeAtNaturalBreak() {
        for ((eligible, ready, allow) in listOf(Triple(false, true, true), Triple(true, false, true), Triple(true, true, false))) {
            val policy = AdBreakPolicy(0)
            repeat(3) { policy.scanCompleted() }
            assertFalse(policy.resultsClosed(900_000, eligible, ready, allow))
        }
    }
    @Test fun shownAdResetsScansAndCooldownAndConsumesTheResult() {
        val policy = AdBreakPolicy(0)
        repeat(3) { policy.scanCompleted() }
        assertTrue(policy.resultsClosed(180_000, true, true))
        assertFalse(policy.resultsClosed(999_000, true, true))
        repeat(3) { policy.scanCompleted() }
        assertFalse(policy.resultsClosed(359_999, true, true))
        policy.scanCompleted()
        assertTrue(policy.resultsClosed(360_000, true, true))
    }
    @Test fun missingAdNeverAppearsLaterWhileReadingOrScanning() {
        val policy = AdBreakPolicy(0)
        repeat(3) { policy.scanCompleted() }
        assertFalse(policy.resultsClosed(180_000, true, false))
        assertFalse(policy.resultsClosed(181_000, true, true))
        policy.scanCompleted()
        assertTrue(policy.resultsClosed(182_000, true, true))
    }
}
