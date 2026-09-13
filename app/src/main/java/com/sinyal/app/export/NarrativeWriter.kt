package com.sinyal.app.export

import android.content.res.Resources
import com.sinyal.app.R
import com.sinyal.app.core.Bearing
import com.sinyal.app.data.CompletedScan
import com.sinyal.app.heat.RouterAdvice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the scan out as prose.
 *
 * An image travels well in a chat but cannot be searched, quoted or pasted into
 * a message to a landlord or an ISP. The narrative states the same findings in
 * sentences, and — unlike the map — it can say plainly where the numbers stop
 * being trustworthy.
 */
object NarrativeWriter {

    fun write(
        res: Resources,
        scan: CompletedScan,
        advice: RouterAdvice?,
        strongestBearing: Bearing?,
        weakestBearing: Bearing?,
        photoBearings: Map<String, Bearing>,
    ): String = buildString {
        val grid = scan.grid
        appendLine(res.getString(R.string.narrative_title))
        appendLine(scan.ssid ?: res.getString(R.string.narrative_unknown_network))
        appendLine(dateFormat().format(Date(scan.savedAtMs)))
        appendLine()

        appendLine(res.getString(R.string.narrative_summary_title))
        appendLine(
            res.getString(
                R.string.narrative_area,
                grid.coveredAreaSqM,
                grid.cellCount,
                duration(res, scan.durationMs),
            ),
        )
        appendLine(
            res.getString(R.string.narrative_range, grid.weakestRssi, grid.strongestRssi),
        )
        appendLine(res.getString(verdict(grid.weakestRssi)))
        appendLine()

        appendLine(res.getString(R.string.narrative_points_title))
        appendLine(
            res.getString(
                R.string.narrative_strongest,
                grid.strongestRssi,
                place(res, strongestBearing),
            ),
        )
        appendLine(
            res.getString(
                R.string.narrative_weakest,
                grid.weakestRssi,
                place(res, weakestBearing),
            ),
        )
        appendLine()

        if (scan.photos.isNotEmpty()) {
            appendLine(res.getString(R.string.narrative_photos_title))
            scan.photos.forEach { photo ->
                appendLine(
                    res.getString(
                        R.string.narrative_photo,
                        photo.label,
                        place(res, photoBearings[photo.id]),
                    ),
                )
            }
            appendLine()
        }

        appendLine(res.getString(R.string.narrative_router_title))
        appendLine(routerAdvice(res, advice))
        appendLine()

        appendLine(res.getString(R.string.narrative_room_title))
        appendLine(
            if (scan.room.wallsAreEstimated) {
                res.getString(R.string.narrative_room_estimated)
            } else {
                res.getString(R.string.narrative_room_measured, scan.room.walls.size)
            },
        )

        scan.geoAnchor?.let { anchor ->
            appendLine()
            appendLine(res.getString(R.string.narrative_location_title))
            appendLine(
                res.getString(
                    R.string.narrative_location_body,
                    anchor.latitude,
                    anchor.longitude,
                    anchor.accuracyMeters,
                ),
            )
        }

        appendLine()
        appendLine(res.getString(R.string.narrative_footer))
    }

    private fun place(res: Resources, bearing: Bearing?): String = bearing?.let {
        res.getString(
            R.string.narrative_place,
            it.distanceMeters,
            res.getString(it.compassLabel),
        )
    } ?: ""

    /** Translates the weakest reading into what it actually costs the user. */
    private fun verdict(weakestDbm: Int?): Int = when {
        weakestDbm == null -> R.string.narrative_verdict_nodata
        weakestDbm >= -60 -> R.string.narrative_verdict_good
        weakestDbm >= -70 -> R.string.narrative_verdict_mixed
        else -> R.string.narrative_verdict_bad
    }

    private fun routerAdvice(res: Resources, advice: RouterAdvice?): String = when {
        advice == null -> res.getString(R.string.narrative_router_nodata)

        !advice.isReliable -> res.getString(
            R.string.narrative_router_unreliable,
            advice.fit.rmseDb,
        )

        advice.isWorthMoving -> res.getString(
            R.string.narrative_router_move,
            advice.moveDistanceMeters,
            advice.gainDb,
            advice.currentWeakDbm,
            advice.predictedWeakDbm,
            advice.fit.pathLossExponent,
            advice.fit.rmseDb,
        )

        else -> res.getString(R.string.narrative_router_optimal)
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
