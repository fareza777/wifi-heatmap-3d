package com.sinyal.app.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import com.sinyal.app.R

/** What the currently joined network actually gives you. */
data class InternetStatus(
    val hasInternetCapability: Boolean,
    val isValidated: Boolean,
    val needsSignIn: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
) {
    /** Plain-language verdict, in the order a user would ask about it. */
    @get:StringRes
    val summary: Int
        get() = when {
            needsSignIn -> R.string.internet_needs_signin
            isValidated -> R.string.internet_validated
            hasInternetCapability -> R.string.internet_unconfirmed
            else -> R.string.internet_no_access
        }

    companion object {
        val Unknown = InternetStatus(false, false, false, 0, 0)
    }
}

/**
 * Reads the captive-portal and validation flags for the active connection.
 *
 * This only ever describes the network already joined. Android exposes nothing
 * equivalent for networks merely seen in a scan, so whether a nearby open
 * network will demand a login page cannot be known until it is joined — a limit
 * worth stating rather than guessing around.
 */
class InternetStatusReader(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun read(): InternetStatus {
        val network = manager.activeNetwork ?: return InternetStatus.Unknown
        val caps = manager.getNetworkCapabilities(network) ?: return InternetStatus.Unknown
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return InternetStatus.Unknown

        return InternetStatus(
            hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            needsSignIn = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            downstreamKbps = caps.linkDownstreamBandwidthKbps,
            upstreamKbps = caps.linkUpstreamBandwidthKbps,
        )
    }
}

/**
 * Very rough distance to an access point, from signal strength alone.
 *
 * Inverts the same log-distance model the router planner fits, but with generic
 * constants rather than ones measured in this building — so it is a sense of
 * scale, not a measurement. Walls alone can double the answer.
 */
fun estimatedDistanceMeters(rssiDbm: Int): Float {
    val referenceDbm = -40.0
    val exponent = 2.7
    return Math.pow(10.0, (referenceDbm - rssiDbm) / (10.0 * exponent)).toFloat()
}
