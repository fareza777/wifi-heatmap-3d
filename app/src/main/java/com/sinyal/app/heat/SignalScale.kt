package com.sinyal.app.heat

import com.sinyal.app.ar.GridCell

/**
 * Maps a reading in dBm onto the colour ramp, on a fixed scale.
 *
 * Fixed, not fitted to the scan. An earlier version stretched the ramp across
 * whatever range a single walk happened to cover, which made faint variation
 * visible but made the colours mean something different in every scan — a room
 * measuring −59 to −49 dBm, which is strong everywhere, came out with red at one
 * end and green at the other. Red has to mean "this is a problem", or it is
 * decoration.
 *
 * The stops are the thresholds the rest of the app already uses to judge a
 * reading, so the colour on the map, the word next to the gauge and the number
 * in the report can never disagree:
 *
 *  - −50 dBm and above is as good as it gets indoors
 *  - −60 is comfortable
 *  - −67 is where video calls start to suffer
 *  - −75 is where most things stall
 *  - below that, effectively dead
 *
 * Two scans of different rooms are therefore directly comparable, and a room
 * that is strong throughout is honestly painted green throughout.
 */
class SignalScale(val minDbm: Int, val maxDbm: Int) {

    private val span: Float = (maxDbm - minDbm).toFloat().coerceAtLeast(1f)

    fun normalize(rssiDbm: Int): Float =
        ((rssiDbm - minDbm) / span).coerceIn(0f, 1f)

    companion object {
        /**
         * The usable indoor range. Below −90 nothing works and above −35 the
         * difference stops mattering, so spending ramp on either is wasted.
         */
        const val FLOOR_DBM = -90
        const val CEILING_DBM = -35

        private val Fixed = SignalScale(FLOOR_DBM, CEILING_DBM)

        /**
         * Always the same scale, whatever the scan contains.
         *
         * [cells] is still accepted so callers need not care, and so the choice
         * stays in one place if a per-scan mode is ever offered as an option.
         */
        fun forCells(cells: List<GridCell>): SignalScale = Fixed
    }
}
