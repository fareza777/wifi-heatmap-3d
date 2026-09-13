package com.sinyal.app.data

import com.sinyal.app.ar.RoomModel
import com.sinyal.app.ar.SampleGrid
import com.sinyal.app.ar.WalkPath
import com.sinyal.app.core.GeoAnchor
import com.sinyal.app.ar.ApSurvey
import com.sinyal.app.ar.SpeedSurvey

/** A finished walk-through, ready to be rendered. */
data class CompletedScan(
    val id: String,
    val savedAtMs: Long,
    val room: RoomModel,
    val grid: SampleGrid,
    val ssid: String?,
    val durationMs: Long,
    val path: WalkPath,
    /** Compass azimuth when the session began; null if no compass reading landed. */
    val startAzimuthDegrees: Float?,
    /** Coarse world anchor. Never used for positioning inside the home. */
    val geoAnchor: GeoAnchor?,
    val photos: List<PlacePhoto>,
    /** Every other transmitter seen along the way, and the handovers between them. */
    val apSurvey: ApSurvey = ApSurvey(),
    /** Throughput actually measured along the way, when the user asked for it. */
    val speedSurvey: SpeedSurvey = SpeedSurvey(),
)

/**
 * Hands the finished scan from the capture screen to the result screen.
 *
 * Navigation arguments only carry strings, and the grid is far too large to
 * serialise into a route, so the scan lives here for the one hop between
 * screens. Nothing is persisted yet — closing the app discards it.
 */
object ScanStore {
    @Volatile
    var latest: CompletedScan? = null
        private set

    fun put(scan: CompletedScan) {
        latest = scan
    }

    fun clear() {
        latest = null
    }
}
