package com.sinyal.app.core

import androidx.annotation.StringRes
import com.sinyal.app.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Where a point sits relative to the spot the scan began. */
data class Bearing(
    val distanceMeters: Float,
    val degreesFromNorth: Float,
) {
    /** Eight-point compass name, resolved by whoever is showing it. */
    @get:StringRes
    val compassLabel: Int
        get() {
            val index = (((degreesFromNorth + HALF_SECTOR) % 360f) / SECTOR).toInt()
            return POINTS[index.coerceIn(0, POINTS.lastIndex)]
        }

    private companion object {
        val POINTS = listOf(
            R.string.compass_n, R.string.compass_ne, R.string.compass_e, R.string.compass_se,
            R.string.compass_s, R.string.compass_sw, R.string.compass_w, R.string.compass_nw,
        )

        /** Eight points, so each covers 45 degrees centred on its own name. */
        const val SECTOR = 45f
        const val HALF_SECTOR = SECTOR / 2f
    }
}

/**
 * Converts ARCore's local frame into real-world bearings and coordinates.
 *
 * ARCore's world is gravity-aligned but its yaw is arbitrary — it inherits
 * whichever way the phone faced at session start. Given the compass azimuth from
 * that same moment, every local offset becomes a true bearing.
 */
object GeoMath {

    private const val METERS_PER_DEGREE_LAT = 111_320.0

    /**
     * Bearing of a local offset, where ARCore -Z is the direction the phone
     * faced when the session started.
     */
    fun bearingOf(x: Float, z: Float, startAzimuthDegrees: Float): Bearing {
        val localDegrees = Math.toDegrees(atan2(x.toDouble(), (-z).toDouble())).toFloat()
        val absolute = (startAzimuthDegrees + localDegrees + 360f) % 360f
        return Bearing(distanceMeters = hypot(x, z), degreesFromNorth = absolute)
    }

    /**
     * Projects a bearing from an anchor onto latitude/longitude.
     *
     * A flat-earth approximation, which over the tens of metres a home spans is
     * far below the anchor's own GPS error.
     */
    fun offsetLatLon(
        latitude: Double,
        longitude: Double,
        bearing: Bearing,
    ): Pair<Double, Double> {
        val radians = Math.toRadians(bearing.degreesFromNorth.toDouble())
        val north = bearing.distanceMeters * cos(radians)
        val east = bearing.distanceMeters * sin(radians)
        val metersPerDegreeLon = METERS_PER_DEGREE_LAT * cos(Math.toRadians(latitude))
        return Pair(
            latitude + north / METERS_PER_DEGREE_LAT,
            longitude + east / metersPerDegreeLon.coerceAtLeast(1.0),
        )
    }

    /** Formats a coordinate pair at a precision honest about metre-scale error. */
    fun formatLatLon(latitude: Double, longitude: Double): String =
        "%.6f, %.6f".format(latitude, longitude)

    /** True north from magnetic north; the caller supplies declination. */
    fun applyDeclination(azimuthDegrees: Float, declinationDegrees: Float): Float =
        (azimuthDegrees + declinationDegrees + 360f) % 360f

    /** Whether two bearings point in meaningfully different directions. */
    fun differBy(a: Float, b: Float): Float {
        val delta = abs(a - b) % 360f
        return if (delta > 180f) 360f - delta else delta
    }
}
