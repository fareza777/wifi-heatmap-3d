package com.sinyal.app.ads

/** Records completed work separately from the navigation moment that can show an ad. */
internal class AdBreakPolicy(private val startedAt: Long) {
    private var scans = 0
    private var freshResult = false
    private var lastShownAt = startedAt
    fun scanCompleted() {
        scans++
        freshResult = true
    }
    fun resultsClosed(now: Long, eligible: Boolean, adReady: Boolean, allowAd: Boolean = true): Boolean {
        val completedWork = freshResult
        freshResult = false
        if (!completedWork || !eligible || !adReady || !allowAd || scans < 3 || now - lastShownAt < 180_000L) return false
        scans = 0
        lastShownAt = now
        return true
    }
}
