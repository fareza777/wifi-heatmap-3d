package com.sinyal.app.heat

import com.sinyal.app.ar.GridCell
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How signal falls off in this particular home.
 *
 * [referenceDbm] is the modelled strength one metre from the router and
 * [pathLossExponent] how fast it decays. Free space is about 2.0; walls, floors
 * and furniture push it higher, so the fitted value silently absorbs the
 * building's own attenuation without needing a wall map.
 */
data class PropagationFit(
    val routerX: Float,
    val routerZ: Float,
    val referenceDbm: Float,
    val pathLossExponent: Float,
    val rmseDb: Float,
)

/** A proposed router position and what it is predicted to buy. */
data class RouterAdvice(
    val fit: PropagationFit,
    val suggestedX: Float,
    val suggestedZ: Float,
    val currentWeakDbm: Int,
    val predictedWeakDbm: Int,
    val moveDistanceMeters: Float,
) {
    val gainDb: Int get() = predictedWeakDbm - currentWeakDbm

    /**
     * Whether the fit is tight enough to quote numbers from.
     *
     * Indoor readings scatter by a few dB on their own; past roughly 6 dB of
     * residual the model is no longer describing this home, and a confident
     * "move it 27 metres" would be worse than admitting the uncertainty.
     */
    val isReliable: Boolean get() = fit.rmseDb <= MAX_TRUSTED_RMSE_DB

    /** Below this the move is not worth the cable. */
    val isWorthMoving: Boolean
        get() = isReliable && gainDb >= MIN_USEFUL_GAIN_DB && moveDistanceMeters >= 0.8f

    private companion object {
        const val MIN_USEFUL_GAIN_DB = 3
        const val MAX_TRUSTED_RMSE_DB = 6.5f
    }
}

/**
 * Works out where the router probably is, then where it would be better.
 *
 * The approach is two passes of the same log-distance model. First it searches
 * for the origin that best explains the readings actually taken — that fit also
 * yields this home's decay exponent. Second, holding those propagation
 * characteristics fixed, it scores every candidate position by how well the
 * *worst-served* parts of the home would do, since a router is chosen for the
 * dead corners rather than for the spot already next to it.
 *
 * Deliberately 2D and wall-blind: reliable wall geometry is not always captured,
 * and the fitted exponent already carries the average cost of the walls that the
 * measurements passed through.
 */
object RouterPlanner {

    fun analyse(cells: List<GridCell>): RouterAdvice? {
        if (cells.size < MIN_CELLS) return null

        val minX = cells.minOf { it.x }
        val maxX = cells.maxOf { it.x }
        val minZ = cells.minOf { it.z }
        val maxZ = cells.maxOf { it.z }

        val fit = bestFit(cells) ?: return null

        var bestX = fit.routerX
        var bestZ = fit.routerZ
        var bestScore = percentileOf(predictAll(cells, fit, fit.routerX, fit.routerZ))

        forEachCandidate(minX, maxX, minZ, maxZ) { cx, cz ->
            val score = percentileOf(predictAll(cells, fit, cx, cz))
            if (score > bestScore) {
                bestScore = score
                bestX = cx
                bestZ = cz
            }
        }

        val currentWeak = percentileOf(predictAll(cells, fit, fit.routerX, fit.routerZ))
        return RouterAdvice(
            fit = fit,
            suggestedX = bestX,
            suggestedZ = bestZ,
            currentWeakDbm = currentWeak.roundToInt(),
            predictedWeakDbm = bestScore.roundToInt(),
            moveDistanceMeters = hypot(bestX - fit.routerX, bestZ - fit.routerZ),
        )
    }

    /**
     * Searches candidate origins for the one whose decay curve fits the readings best.
     *
     * The search is anchored around the strongest reading rather than sweeping the
     * whole home. Signal strength is a hard physical constraint: a -26 dBm sample
     * was taken within a couple of metres of the antenna, so origins on the far
     * side of the house are not merely unlikely, they are impossible. Without this
     * anchor the fit could settle in a distant corner and then advise a move
     * longer than the building.
     */
    private fun bestFit(cells: List<GridCell>): PropagationFit? {
        val anchor = cells.maxByOrNull { it.rssiDbm } ?: return null
        var best: PropagationFit? = null

        forEachCandidate(
            anchor.x - ANCHOR_RADIUS,
            anchor.x + ANCHOR_RADIUS,
            anchor.z - ANCHOR_RADIUS,
            anchor.z + ANCHOR_RADIUS,
        ) { cx, cz ->
            val candidate = fitAt(cells, cx, cz)
            if (best == null || candidate.rmseDb < best!!.rmseDb) best = candidate
        }
        return best
    }

    /**
     * Least-squares fit of `rssi = A + n * (-10 log10 d)` for a fixed origin.
     *
     * Distance is floored at half a metre: the model diverges at zero, and no
     * measurement is ever really taken at the antenna itself.
     */
    private fun fitAt(cells: List<GridCell>, cx: Float, cz: Float): PropagationFit {
        var sumU = 0.0
        var sumY = 0.0
        var sumUU = 0.0
        var sumUY = 0.0
        val n = cells.size

        for (cell in cells) {
            val d = hypot(cell.x - cx, cell.z - cz).coerceAtLeast(MIN_DISTANCE)
            val u = -10.0 * log10(d.toDouble())
            val y = cell.rssiDbm.toDouble()
            sumU += u
            sumY += y
            sumUU += u * u
            sumUY += u * y
        }

        val denominator = n * sumUU - sumU * sumU
        val rawExponent = if (denominator == 0.0) DEFAULT_EXPONENT.toDouble() else {
            (n * sumUY - sumU * sumY) / denominator
        }
        val exponent = rawExponent.coerceIn(MIN_EXPONENT, MAX_EXPONENT)
        // Re-derive the intercept after clamping, so the two stay consistent.
        val reference = (sumY - exponent * sumU) / n

        var squaredError = 0.0
        for (cell in cells) {
            val d = hypot(cell.x - cx, cell.z - cz).coerceAtLeast(MIN_DISTANCE)
            val predicted = reference + exponent * (-10.0 * log10(d.toDouble()))
            val error = cell.rssiDbm - predicted
            squaredError += error * error
        }

        return PropagationFit(
            routerX = cx,
            routerZ = cz,
            referenceDbm = reference.toFloat(),
            pathLossExponent = exponent.toFloat(),
            rmseDb = sqrt(squaredError / n).toFloat(),
        )
    }

    private fun predictAll(
        cells: List<GridCell>,
        fit: PropagationFit,
        cx: Float,
        cz: Float,
    ): List<Double> = cells.map { cell ->
        val d = hypot(cell.x - cx, cell.z - cz).coerceAtLeast(MIN_DISTANCE)
        fit.referenceDbm + fit.pathLossExponent * (-10.0 * log10(d.toDouble()))
    }

    /**
     * Score for a placement: the 10th percentile of predicted coverage.
     *
     * The strict minimum would let one freak cell in a far corner decide every
     * recommendation; the low decile still speaks for the weak areas without
     * being hostage to a single reading.
     */
    private fun percentileOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * WEAK_PERCENTILE).toInt()
        return sorted[index]
    }

    private inline fun forEachCandidate(
        minX: Float,
        maxX: Float,
        minZ: Float,
        maxZ: Float,
        action: (Float, Float) -> Unit,
    ) {
        val stepX = ((maxX - minX) / GRID_STEPS).coerceAtLeast(MIN_STEP)
        val stepZ = ((maxZ - minZ) / GRID_STEPS).coerceAtLeast(MIN_STEP)
        var z = minZ
        while (z <= maxZ) {
            var x = minX
            while (x <= maxX) {
                action(x, z)
                x += stepX
            }
            z += stepZ
        }
    }

    private const val MIN_CELLS = 8
    private const val GRID_STEPS = 24
    private const val MIN_STEP = 0.25f
    private const val MIN_DISTANCE = 0.5f
    /** How far from the strongest reading the router could plausibly sit. */
    private const val ANCHOR_RADIUS = 5.0f
    private const val WEAK_PERCENTILE = 0.10
    private const val DEFAULT_EXPONENT = 2.5f
    private const val MIN_EXPONENT = 1.6
    private const val MAX_EXPONENT = 5.0
}
