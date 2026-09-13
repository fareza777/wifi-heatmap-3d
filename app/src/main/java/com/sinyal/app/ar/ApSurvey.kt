package com.sinyal.app.ar

/** One access point's strength, measured where the walk happened to be. */
data class ApReading(
    val bssid: String,
    val ssid: String,
    val x: Float,
    val z: Float,
    val rssiDbm: Int,
    val atMs: Long,
)

/** A moment the phone changed which radio it was talking to. */
data class RoamEvent(
    val atMs: Long,
    val fromBssid: String?,
    val toBssid: String,
    /** Strength of the radio being left, at the moment of leaving. */
    val leavingDbm: Int?,
    val joiningDbm: Int,
)

/**
 * Every access point seen during the walk, not just the one in use.
 *
 * The walk is already being made and the scan list is already being read; this
 * is what turns one coverage map into a map per transmitter. For anyone with a
 * mesh or a repeater that answers the question no single-AP heatmap can: which
 * room each node actually owns, and where the handover ought to happen.
 *
 * Sampling is sparse by force. Since Android 9 a foreground app may request a
 * fresh scan four times per two minutes; between those the platform's cached
 * list is stale. So a two-minute walk yields a handful of positions per AP
 * rather than a continuous trail, and the UI says so instead of drawing a
 * confident surface through four points.
 */
class ApSurvey(
    val readings: List<ApReading> = emptyList(),
    val roams: List<RoamEvent> = emptyList(),
) {
    val isEmpty: Boolean get() = readings.isEmpty()

    /** BSSIDs seen, most-sampled first. */
    fun transmitters(): List<ApIdentity> =
        readings.groupBy { it.bssid }
            .map { (bssid, rows) ->
                ApIdentity(
                    bssid = bssid,
                    ssid = rows.first().ssid,
                    sampleCount = rows.size,
                    strongestDbm = rows.maxOf { it.rssiDbm },
                )
            }
            .sortedByDescending { it.sampleCount }

    /** The readings for one transmitter, ready to bin into a grid. */
    fun samplesFor(bssid: String, floorY: Float): List<SamplePoint> =
        readings.filter { it.bssid == bssid }
            .map { SamplePoint(it.x, floorY, it.z, it.rssiDbm) }
}

/** One transmitter seen during a walk. */
data class ApIdentity(
    val bssid: String,
    val ssid: String,
    val sampleCount: Int,
    val strongestDbm: Int,
)

/**
 * Collects readings and roaming events while a scan runs.
 *
 * Kept mutable and separate from [ApSurvey] so the capture loop can append
 * cheaply, and the finished scan gets an immutable snapshot.
 */
class ApSurveyRecorder {

    private val readings = mutableListOf<ApReading>()
    private val roams = mutableListOf<RoamEvent>()
    private var lastBssid: String? = null
    private var lastRssi: Int? = null

    /**
     * Records one sweep of the scan list at a position.
     *
     * Deduplicated against the previous sweep: the platform hands back the same
     * cached list until it refreshes, and storing it repeatedly would weight one
     * standing-still moment as though it were a dozen measurements.
     */
    fun observe(
        seen: List<Pair<String, Pair<String, Int>>>,
        x: Float,
        z: Float,
        atMs: Long,
        minSpacingMeters: Float,
    ) {
        seen.forEach { (bssid, value) ->
            val (ssid, rssi) = value
            val previous = readings.lastOrNull { it.bssid == bssid }
            val moved = previous == null ||
                kotlin.math.hypot(x - previous.x, z - previous.z) >= minSpacingMeters
            if (moved) {
                readings += ApReading(bssid, ssid, x, z, rssi, atMs)
            }
        }
    }

    /** Notes which radio the phone is actually connected to right now. */
    fun observeConnection(bssid: String?, rssiDbm: Int, atMs: Long) {
        if (bssid == null) return
        val previous = lastBssid

        if (previous != null && previous != bssid) {
            roams += RoamEvent(
                atMs = atMs,
                fromBssid = previous,
                toBssid = bssid,
                leavingDbm = lastRssi,
                joiningDbm = rssiDbm,
            )
        }
        lastBssid = bssid
        lastRssi = rssiDbm
    }

    fun snapshot(): ApSurvey = ApSurvey(readings.toList(), roams.toList())
}
