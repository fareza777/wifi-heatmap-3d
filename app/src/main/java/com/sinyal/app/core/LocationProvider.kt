package com.sinyal.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Where the scan happened, and how sure we are of it. */
data class GeoAnchor(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

/**
 * A single coarse fix for the whole scan.
 *
 * Deliberately not used for positioning inside the home: indoor GPS error runs
 * to 5–20 m, which exceeds most houses end to end. Its only job is to anchor the
 * scan on Earth and to supply magnetic declination, while ARCore keeps providing
 * the centimetre-grade geometry within the building.
 */
class LocationProvider(context: Context) : LocationListener {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    var anchor: GeoAnchor? = null
        private set

    private val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Guarded by hasPermission on every path.
    fun start() {
        if (!hasPermission) return

        // Seed from whatever the system already knows, then let updates refine it.
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { adopt(it) }

        runCatching {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                UPDATE_DISTANCE_M,
                this,
            )
        }
    }

    fun stop() {
        runCatching { manager.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) = adopt(location)

    /** Keeps the tightest fix seen, since a scan may start before GPS settles. */
    private fun adopt(location: Location) {
        val current = anchor
        if (current == null || location.accuracy < current.accuracyMeters) {
            anchor = GeoAnchor(location.latitude, location.longitude, location.accuracy)
        }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val UPDATE_DISTANCE_M = 2f
    }
}
