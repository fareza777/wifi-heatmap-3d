package com.sinyal.app.export

import android.content.res.Resources
import com.sinyal.app.R
import com.sinyal.app.data.CompletedScan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the scan out as prose.
 *
 * An image travels well in a chat but cannot be searched, quoted or pasted into
 * a message to a landlord or an ISP. The narrative states the same findings in
 * sentences — and only the findings the result screen also shows. Area, walls,
 * router advice and GPS were dropped there for being less precise than they
 * looked; a shared report is the last place to reintroduce them.
 */
object NarrativeWriter {

    fun write(res: Resources, scan: CompletedScan): String = buildString {
        val grid = scan.grid
        appendLine(res.getString(R.string.narrative_title))
        appendLine(scan.ssid ?: res.getString(R.string.narrative_unknown_network))
        appendLine(dateFormat().format(Date(scan.savedAtMs)))
        appendLine()

        appendLine(res.getString(R.string.narrative_summary_title))
        appendLine(
            res.getString(
                R.string.narrative_points,
                grid.cellCount,
                duration(res, scan.durationMs),
            ),
        )
        val weakest = grid.weakestRssi
        val strongest = grid.strongestRssi
        if (weakest != null && strongest != null) {
            appendLine(res.getString(R.string.narrative_range, weakest, strongest))
        }
        appendLine(res.getString(verdict(weakest)))

        if (scan.photos.isNotEmpty()) {
            appendLine()
            appendLine(res.getString(R.string.narrative_photos_title))
            scan.photos.forEachIndexed { index, photo ->
                appendLine(res.getString(R.string.narrative_photo, index + 1, photo.label))
            }
        }

        appendLine()
        appendLine(res.getString(R.string.narrative_footer))
    }

    /** Translates the weakest reading into what it actually costs the user. */
    private fun verdict(weakestDbm: Int?): Int = when {
        weakestDbm == null -> R.string.narrative_verdict_nodata
        weakestDbm >= -60 -> R.string.narrative_verdict_good
        weakestDbm >= -70 -> R.string.narrative_verdict_mixed
        else -> R.string.narrative_verdict_bad
    }

    private fun duration(res: Resources, millis: Long): String {
        val minutes = millis / 60_000
        val seconds = (millis % 60_000) / 1000
        return if (minutes > 0) {
            res.getString(R.string.narrative_duration_ms, minutes, seconds)
        } else {
            res.getString(R.string.narrative_duration_s, seconds)
        }
    }

    /**
     * Built per call rather than held as a constant.
     *
     * [SimpleDateFormat] is not thread-safe, and it also caches the locale it was
     * created with — which would pin every future report to whatever language the
     * app happened to start in.
     */
    private fun dateFormat() =
        SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
}
