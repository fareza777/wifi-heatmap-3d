package com.sinyal.app.ar

import com.sinyal.app.wifi.SignalQuality
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One signal reading taken at a known point in the AR world. */
data class SamplePoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val rssiDbm: Int,
)

/**
 * The averaged reading for one cell of the grid.
 *
 * [x], [y], [z] are the cell centre, so a renderer can place a marker without
 * knowing anything about the binning.
 */
data class GridCell(
    val ix: Int,
    val iy: Int,
    val iz: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val rssiDbm: Int,
    val sampleCount: Int,
    /** Running sum of linear power, kept so the mean stays physically correct. */
    val powerSum: Double = 10.0.pow(rssiDbm / 10.0),
) {
    val quality: SignalQuality get() = SignalQuality.of(rssiDbm)
}

/**
 * Spatial binning of Wi-Fi samples.
 *
 * Readings are averaged into fixed-size voxels rather than kept individually.
 * That does three jobs at once: it keeps the node count renderable on a weak
 * GPU, it averages away the several-dB jitter a stationary phone still sees,
 * and it is already the structure the heatmap interpolation will want.
 *
 * Instances are immutable — [plus] returns a new grid.
 */
class SampleGrid private constructor(
    val cellSizeMeters: Float,
    private val cells: Map<Long, GridCell>,
) {
    constructor(cellSizeMeters: Float = DEFAULT_CELL_SIZE) : this(cellSizeMeters, emptyMap())

    val occupiedCells: List<GridCell> get() = cells.values.toList()
    val cellCount: Int get() = cells.size
    val totalSamples: Int get() = cells.values.sumOf { it.sampleCount }

    val weakestRssi: Int? get() = cells.values.minOfOrNull { it.rssiDbm }
    val strongestRssi: Int? get() = cells.values.maxOfOrNull { it.rssiDbm }

    /** Rough floor coverage: distinct cells projected onto the horizontal plane. */
    val coveredAreaSqM: Float
        get() {
            val footprint = cells.values.map { it.ix to it.iz }.toSet()
            return footprint.size * cellSizeMeters * cellSizeMeters
        }

    /** Returns a new grid with [sample] folded into its cell's running mean. */
    operator fun plus(sample: SamplePoint): SampleGrid {
        val ix = floor(sample.x / cellSizeMeters).toInt()
        val iy = floor(sample.y / cellSizeMeters).toInt()
        val iz = floor(sample.z / cellSizeMeters).toInt()
        val key = keyOf(ix, iy, iz)

        val existing = cells[key]
        val power = 10.0.pow(sample.rssiDbm / 10.0)
        val merged = if (existing == null) {
            GridCell(
                ix = ix,
                iy = iy,
                iz = iz,
                x = (ix + 0.5f) * cellSizeMeters,
                y = (iy + 0.5f) * cellSizeMeters,
                z = (iz + 0.5f) * cellSizeMeters,
                rssiDbm = sample.rssiDbm,
                sampleCount = 1,
                powerSum = power,
            )
        } else {
            // Averaged in linear power, not in decibels. dBm is logarithmic, so
            // the arithmetic mean of -40 and -60 is not -50 — the stronger
            // reading carries a hundred times the power and must dominate.
            // Averaging the decibels directly biased every cell towards weak.
            val n = existing.sampleCount + 1
            val totalPower = existing.powerSum + power
            existing.copy(
                rssiDbm = (10.0 * log10(totalPower / n)).roundToInt(),
                sampleCount = n,
                powerSum = totalPower,
            )
        }

        return SampleGrid(cellSizeMeters, cells + (key to merged))
    }

    companion object {
        const val DEFAULT_CELL_SIZE = 0.35f

        /** Rebuilds a grid from already-averaged cells, e.g. when loading from disk. */
        fun fromCells(cellSizeMeters: Float, cells: List<GridCell>): SampleGrid =
            SampleGrid(
                cellSizeMeters,
                cells.associateBy { keyOf(it.ix, it.iy, it.iz) },
            )

        /** Packs three signed cell indices into one key; ±1 km of range is ample indoors. */
        private fun keyOf(ix: Int, iy: Int, iz: Int): Long =
            (ix.toLong() and 0x1FFFFF shl 42) or
                (iy.toLong() and 0x1FFFFF shl 21) or
                (iz.toLong() and 0x1FFFFF)
    }
}

/** Straight-line distance between two points, in metres. */
fun distanceBetween(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
): Float {
    val dx = ax - bx
    val dy = ay - by
    val dz = az - bz
    return sqrt(dx * dx + dy * dy + dz * dz)
}
