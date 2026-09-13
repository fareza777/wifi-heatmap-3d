package com.sinyal.app.billing

import org.junit.Assert.*
import org.junit.Test

class EntitlementPolicyTest {
    @Test fun failedPlayQueryPreservesPaidEntitlement() {
        assertTrue(reconcileEntitlement(cached = true, querySucceeded = false, owned = false))
    }
    @Test fun failedPlayQueryCannotGrantEntitlement() {
        assertFalse(reconcileEntitlement(cached = false, querySucceeded = false, owned = true))
    }
    @Test fun successfulPlayQueryCanRevokeRefundedPurchase() {
        assertFalse(reconcileEntitlement(cached = true, querySucceeded = true, owned = false))
    }
    @Test fun successfulPlayQueryRestoresPurchase() {
        assertTrue(reconcileEntitlement(cached = false, querySucceeded = true, owned = true))
    }
}
