package com.sinyal.app.wifi

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One place the phone stood, and how far it measured from the access point.
 *
 * Coordinates are whatever frame the caller uses — metres on a room sketch, or
 * ARCore world units — as long as positions and distances share the unit.
 */
data class RangeAnchor(
    val x: Double,
    val y: Double,
    val distanceM: Double,
)

/**
 * The solved access-point position.
 *
 * [rmseM] is how far the estimate would have to move to agree with every
 * circle: small means the measurements corroborate each other, large means at
 * least one range lied (a wall, a reflection, or a tapped spot that was not
 * where the phone actually stood).
 */
data class Trilaterated(
    val x: Double,
    val y: Double,
    val rmseM: Double,
    val anchors: Int,
)

/**
 * Recovers a transmitter's position from ranges measured at known spots.
 *
 * The raw equations — each anchor says the AP sits on a circle — are
 * quadratic. Subtracting the first anchor's equation from every other removes
 * the squared unknowns and leaves a linear system, which is what every
 * multilateration engine does to avoid an iterative solver. Two unknowns, one
 * row per extra anchor, solved by the normal equations in closed form.
 */
object Trilateration {

    /** Determinant below this the anchors are effectively collinear. */
    private const val MIN_DETERMINANT = 1e-6

    /** Null when the anchors cannot fix a point — too few, or nearly collinear. */
    fun solve(anchors: List<RangeAnchor>): Trilaterated? {
        if (anchors.size < 3) return null

        val base = anchors.first()
        var ata00 = 0.0
        var ata01 = 0.0
        var ata11 = 0.0
        var atb0 = 0.0
        var atb1 = 0.0

        for (anchor in anchors.drop(1)) {
            val a0 = 2.0 * (anchor.x - base.x)
            val a1 = 2.0 * (anchor.y - base.y)
            val b = (base.distanceM * base.distanceM - anchor.distanceM * anchor.distanceM) -
                (base.x * base.x + base.y * base.y) +
                (anchor.x * anchor.x + anchor.y * anchor.y)

            ata00 += a0 * a0
            ata01 += a0 * a1
            ata11 += a1 * a1
            atb0 += a0 * b
            atb1 += a1 * b
        }

        val det = ata00 * ata11 - ata01 * ata01
        if (abs(det) < MIN_DETERMINANT) return null

        val x = (atb0 * ata11 - atb1 * ata01) / det
        val y = (ata00 * atb1 - ata01 * atb0) / det

        val residual = anchors.sumOf { anchor ->
            val predicted = sqrt((x - anchor.x).sq() + (y - anchor.y).sq())
            (predicted - anchor.distanceM).sq()
        }
        return Trilaterated(
            x = x,
            y = y,
            rmseM = sqrt(residual / anchors.size),
            anchors = anchors.size,
        )
    }

    private fun Double.sq(): Double = this * this
}
