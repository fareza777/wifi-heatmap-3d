package com.sinyal.app.billing

internal fun reconcileEntitlement(cached: Boolean, querySucceeded: Boolean, owned: Boolean): Boolean =
    if (querySucceeded) owned else cached
