package com.sinyal.app.ui.screens

import android.app.Application
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.content.Context
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.sinyal.app.ar.DepthProbe
import com.sinyal.app.ar.PlaneHarvester
import com.sinyal.app.ar.RoomModel
import com.sinyal.app.ar.SampleGrid
import com.sinyal.app.ar.PathPoint
import com.sinyal.app.ar.SamplePoint
import com.sinyal.app.ar.WalkPath
import com.sinyal.app.ar.WallSegment
import com.sinyal.app.ar.distanceBetween
import com.sinyal.app.data.CompletedScan
import com.sinyal.app.core.FrameCapture
import com.sinyal.app.data.PlacePhoto
import com.sinyal.app.data.ScanRepository
import com.sinyal.app.data.ScanStore
import com.sinyal.app.core.HeadingProvider
import com.sinyal.app.core.LocationProvider
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import com.sinyal.app.wifi.WifiSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sinyal.app.R
import androidx.annotation.StringRes
import com.google.ar.core.Config
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.sinyal.app.ar.ApSurveyRecorder
import com.sinyal.app.ar.SpeedReading
import com.sinyal.app.ar.SpeedSurvey
import com.sinyal.app.net.ThroughputProbe

data class CaptureUiState(
    val grid: SampleGrid = SampleGrid(),
    val link: WifiSnapshot = WifiSnapshot.Disconnected,
    val tracking: Boolean = false,
    /** Resource id of the tracking advice to show, resolved by the screen. */
    @StringRes val trackingHint: Int? = null,
    /** Most recent throughput probe, when the speed survey is on. */
    val lastSpeedMbps: Double? = null,
    val wallPlanes: Int = 0,
    val photoCount: Int = 0,
    val photoFlash: Boolean = false,
    val trackingJumps: Int = 0,
    val elapsedMs: Long = 0L,
    /** Hardware depth sampling is live; points so far. */
    val depthActive: Boolean = false,
    val depthPoints: Int = 0,
    val finished: Boolean = false,
)

/**
 * Records Wi-Fi strength against AR camera position.
 *
 * Two rates are in play and they are deliberately decoupled: ARCore delivers
 * frames at 30 Hz, while the Wi-Fi radio is polled at 4 Hz. Frames therefore
 * consume the most recent reading rather than triggering their own, and a
 * sample is only banked once the phone has actually moved — otherwise standing
 * still would pile hundreds of readings into one spot and skew its mean.
 */
class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val scanner = WifiScanner(app)
    private val repository = ScanRepository(app)
    private val heading = HeadingProvider(app)
    private val location = LocationProvider(app)
    private val depthProbe = DepthProbe()

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var session: Session? = null
    /**
     * The fixed point every reading is measured from.
     *
     * ARCore's raw session coordinates drift: when it relocalises, the whole
     * frame shifts and everything recorded earlier silently belongs to a
     * different world. An anchor is the one thing ARCore moves *with* the room
     * when it corrects itself, so measuring against it keeps a scan from
     * stretching across thirty metres of empty space.
     */
    /**
     * Accumulated correction that cancels every teleport seen so far.
     *
     * ARCore relocalises by shifting its whole coordinate frame, which reads as
     * the phone jumping several metres between two frames. Subtracting the jump
     * from everything after it keeps one continuous frame — a 76-second walk was
     * otherwise stretching a house across 28 metres.
     *
     * An origin anchor was tried first and does not work for this: it is only
     * valid while ARCore still tracks the spot it was placed on, and walking to
     * another room pauses it, at which point its pose stops being updated and
     * the correction it provides is stale in exactly the moment it is needed.
     */
    private var offsetX = 0f
    private var offsetY = 0f
    private var offsetZ = 0f
    /**
     * Wall geometry banked while each plane was still tracked.
     *
     * Keyed by the plane itself so a growing wall keeps replacing its own entry
     * rather than piling up duplicates.
     */
    private val apSurvey = ApSurveyRecorder()
    private val throughput = ThroughputProbe()
    private val speedReadings = mutableListOf<SpeedReading>()
    private var speedProbeRunning = false
    private var lastSpeedAtMs = 0L
    private var measureSpeed = false
    private val trackedWalls = LinkedHashMap<Plane, WallSegment>()
    private var floorYSeen: Float? = null

    /** Last position accepted as real, used to size the correction for a jump. */
    private var lastAcceptedX = 0f
    private var lastAcceptedY = 0f
    private var lastAcceptedZ = 0f
    private var pathBroken = false
    private var surveySweeps = 0
    private var frameCounter = 0
    private val scanId = System.currentTimeMillis().toString()
    private val photos = mutableListOf<PlacePhoto>()
    @Volatile private var photoRequested = false
    private var sensorOrientation: Int? = null
    private var startAzimuth: Float? = null
    private val pathPoints = mutableListOf<PathPoint>()
    private var startedAtMs: Long = 0L
    private var lastFrameX = Float.NaN
    private var lastFrameY = Float.NaN
    private var lastFrameZ = Float.NaN
    private var lastFrameAtMs = 0L
    private var jumps = 0
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastZ = Float.NaN

    init {
        heading.start()
        location.start()
        viewModelScope.launch {
            monitor.snapshots().collect { snapshot ->
                _state.update { it.copy(link = snapshot) }
            }
        }
    }

    /** Called once per AR frame, with everything that frame can tell us. */
    /** Turned on from the mode screen; off by default because it costs data. */
    fun enableSpeedSurvey(enabled: Boolean) {
        measureSpeed = enabled
    }

    fun onArFrame(session: Session, frame: Frame) {
        ensurePlaneFinding(session)
        depthProbe.ensureDepth(session)

        val camera = frame.camera
        val isTracking = camera.trackingState == TrackingState.TRACKING
        this.session = session

        val raw = camera.pose.translation
        val x = raw[0] - offsetX
        val y = raw[1] - offsetY
        val z = raw[2] - offsetZ

        if (photoRequested && isTracking) {
            photoRequested = false
            capturePhoto(session, frame, x, y, z)
        }

        if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
        // ARCore's yaw is whatever direction the phone faced first; pairing that
        // instant with the compass is what makes every later position a bearing.
        if (startAzimuth == null && isTracking) startAzimuth = heading.azimuthDegrees

        // Walls are what turn an estimated box into a real floor plan, so the
        // count is surfaced live — a scan with zero is worth correcting while
        // the user is still walking, not after the fact.
        if (frameCounter++ % WALL_POLL_FRAMES == 0) {
            collectPlanes(session)
            val walls = wallCount()
            _state.update { it.copy(wallPlanes = walls) }
        }

        // Depth frames cost more than a pose read; sampling on the wall cadence
        // still lands a few hundred surface points over a normal walk.
        if (frameCounter % DEPTH_POLL_FRAMES == 0) {
            depthProbe.onFrame(frame, offsetX, offsetY, offsetZ)
            _state.update {
                it.copy(depthActive = depthProbe.active, depthPoints = depthProbe.count)
            }
        }

        // A relocalisation jump is not walking. ARCore keeps reporting TRACKING
        // through one, so the pose simply teleports — and every reading taken
        // afterwards lands in the wrong place, stretching a 4x3 m room into a
        // corridor. Anything faster than a person can move is discarded.
        if (isJump(x, y, z)) {
            jumps++
            _state.update { it.copy(trackingJumps = jumps) }

            // Absorb the teleport rather than merely skipping the frame. The pose
            // moved but the person did not, so the jump is folded into the
            // correction and everything afterwards stays in the same frame as
            // everything before.
            offsetX += x - lastAcceptedX
            offsetY += y - lastAcceptedY
            offsetZ += z - lastAcceptedZ

            resetContinuity(lastAcceptedX, lastAcceptedY, lastAcceptedZ)
            pathBroken = true
            return
        }

        lastAcceptedX = x
        lastAcceptedY = y
        lastAcceptedZ = z

        val link = _state.value.link
        val moved = lastX.isNaN() ||
            distanceBetween(x, y, z, lastX, lastY, lastZ) >= MIN_MOVE_METERS

        _state.update { current ->
            val grid = if (isTracking && link.connected && moved) {
                current.grid + SamplePoint(x, y, z, link.rssiDbm)
            } else {
                current.grid
            }
            current.copy(
                grid = grid,
                tracking = isTracking,
                elapsedMs = System.currentTimeMillis() - startedAtMs,
            )
        }

        if (isTracking && link.connected) {
            apSurvey.observeConnection(link.bssid, link.rssiDbm, System.currentTimeMillis())
        }

        // The platform only refreshes its scan list a few times a minute, so
        // asking on every frame would burn cycles re-reading the same answer.
        if (isTracking && frameCounter % SURVEY_POLL_FRAMES == 0) {
            recordNeighbours(x, z)
        }

        if (isTracking && link.connected) maybeProbeSpeed(x, z)

        if (isTracking && moved) {
            lastX = x
            lastY = y
            lastZ = z
            appendPathVertex(x, z)
        }
    }

    override fun onCleared() {
        heading.stop()
        location.stop()
    }

    /**
     * Makes sure the live session is really looking for planes.
     *
     * The mode is asked for when the session is created, but that request is a
     * hand-off to the rendering library and there is no guarantee it survives
     * whatever the library configures afterwards. Walls never appeared, and
     * neither did floors — no plane overlay was drawn at all — which points at
     * the setting rather than at the room. Reading the live config back and
     * correcting it costs one comparison per frame and cannot be undone by
     * anyone downstream.
     *
     * Runs on the render thread, which is where `configure` has to be called.
     */
    private fun ensurePlaneFinding(session: Session) {
        val config = session.config
        if (config.planeFindingMode == Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL) {
            return
        }

        runCatching {
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            session.configure(config)
        }
    }

    /**
     * Banks the geometry of every plane that is live right now.
     *
     * Has to run during the walk: extents are only meaningful while a plane is
     * TRACKING, and reading them once at the end returns zeros.
     */
    private fun collectPlanes(session: Session) {
        val planes = runCatching { session.getAllTrackables(Plane::class.java) }
            .getOrNull()
            .orEmpty()

        planes.forEach { plane ->
            if (plane.trackingState != TrackingState.TRACKING) return@forEach
            if (plane.subsumedBy != null) return@forEach

            when (plane.type) {
                Plane.Type.VERTICAL -> {
                    val segment = PlaneHarvester.segmentOf(plane) ?: return@forEach
                    val known = trackedWalls[plane]
                    // Planes grow as they are explored; keep the fullest view.
                    if (known == null || segment.length > known.length) {
                        trackedWalls[plane] = segment
                    }
                }

                Plane.Type.HORIZONTAL_UPWARD_FACING -> {
                    val y = plane.centerPose.ty()
                    floorYSeen = minOf(floorYSeen ?: y, y)
                }

                else -> Unit
            }
        }
    }

    /**
     * Banks every access point currently visible, at this position.
     *
     * A fresh sweep is requested on the same cadence the platform allows — four
     * per two minutes since Android 9 — and the cache is read each time. That
     * gives a handful of positions per transmitter over a walk rather than a
     * continuous trail, which is a real limit of the platform and is stated
     * where the result is shown rather than smoothed over.
     */
    private fun recordNeighbours(x: Float, z: Float) {
        if (surveySweeps % SWEEPS_PER_REFRESH == 0) scanner.requestScan()
        surveySweeps++

        val seen = scanner.cachedResults(_state.value.link.bssid)
            .map { it.bssid to (it.ssid to it.rssiDbm) }
        if (seen.isEmpty()) return

        apSurvey.observe(
            seen = seen,
            x = x,
            z = z,
            atMs = System.currentTimeMillis(),
            minSpacingMeters = SURVEY_SPACING_METERS,
        )
    }

    /**
     * Measures what this spot can actually carry, occasionally.
     *
     * One probe at a time and well spaced: each is a real download, and firing
     * them on top of each other would measure the app competing with itself.
     * The position is taken when the probe starts — a walk covers about a metre
     * while it runs, which is inside the resolution of the map anyway.
     */
    private fun maybeProbeSpeed(x: Float, z: Float) {
        if (!measureSpeed || speedProbeRunning) return
        val now = System.currentTimeMillis()
        if (now - lastSpeedAtMs < SPEED_INTERVAL_MS) return

        speedProbeRunning = true
        lastSpeedAtMs = now
        viewModelScope.launch {
            val mbps = throughput.measure()
            if (mbps != null) {
                speedReadings += SpeedReading(x, z, mbps, now)
                _state.update { it.copy(lastSpeedMbps = mbps) }
            }
            speedProbeRunning = false
        }
    }

    /** Walls a finished scan would keep, from what has been banked so far. */
    private fun wallCount(): Int =
        PlaneHarvester.build(trackedWalls.values.toList(), floorYSeen, _state.value.grid)
            ?.takeIf { !it.wallsAreEstimated }
            ?.walls
            ?.size
            ?: 0

    /**
     * Whether the pose teleported rather than the phone moved.
     *
     * Judged on speed, with a floor under the interval so a fast frame cannot
     * divide its way to an absurd figure, plus a hard cap on any single step.
     *
     * The previous version bailed out whenever the frame gap was long — and a
     * long gap is exactly what a relocalisation produces, because ARCore stalls
     * while it works. The check switched itself off at the only moment it
     * mattered, which is how a two-minute walk recorded 221 m of travel with a
     * single 13.9 m stride in it.
     */
    private fun isJump(x: Float, y: Float, z: Float): Boolean {
        val now = System.currentTimeMillis()
        val previousX = lastFrameX
        val previousY = lastFrameY
        val previousZ = lastFrameZ
        val elapsed = now - lastFrameAtMs

        lastFrameX = x
        lastFrameY = y
        lastFrameZ = z
        lastFrameAtMs = now

        if (previousX.isNaN()) return false

        val horizontal = kotlin.math.hypot(x - previousX, z - previousZ)
        if (horizontal > MAX_STEP_METERS) return true

        // A hand can raise a phone, but not by most of a metre between frames.
        if (kotlin.math.abs(y - previousY) > MAX_RISE_METERS) return true

        val seconds = (maxOf(elapsed, MIN_GAP_MS).toFloat()) / 1000f
        return horizontal / seconds > MAX_WALK_SPEED_MPS
    }

    /** After a jump the old anchor is meaningless; start measuring afresh from here. */
    private fun resetContinuity(x: Float, y: Float, z: Float) {
        lastX = x
        lastY = y
        lastZ = z
    }

    /** Asks for a photo on the next tracked frame; the shutter must not block AR. */
    fun requestPhoto() {
        photoRequested = true
    }

    /**
     * Grabs the current camera frame and pins it to where the phone is standing.
     *
     * Encoding happens off the AR thread, but the [Image] must be acquired and
     * released on this frame — ARCore recycles its buffers immediately, and
     * holding one starves the session.
     */
    private fun capturePhoto(session: Session, frame: Frame, x: Float, y: Float, z: Float) {
        val image = runCatching { frame.acquireCameraImage() }.getOrNull() ?: return
        val rotation = sensorRotation(session)
        val index = photos.size + 1
        val file = File(photoDirectory(), "photo_$index.jpg")

        val saved = try {
            FrameCapture.saveJpeg(image, file, rotation)
        } finally {
            runCatching { image.close() }
        }
        if (!saved) return

        photos += PlacePhoto(
            id = "p$index",
            label = getApplication<Application>().getString(R.string.capture_photo_label, index),
            x = x,
            y = y,
            z = z,
            fileName = file.name,
            capturedAtMs = System.currentTimeMillis(),
        )
        _state.update { it.copy(photoCount = photos.size, photoFlash = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(FLASH_MS)
            _state.update { it.copy(photoFlash = false) }
        }
    }

    private fun photoDirectory(): File =
        File(File(getApplication<Application>().filesDir, "photos"), scanId)

    /** The sensor is mounted sideways; without this every photo lands rotated. */
    private fun sensorRotation(session: Session): Int {
        sensorOrientation?.let { return it }
        val resolved = runCatching {
            val manager = getApplication<Application>()
                .getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.getCameraCharacteristics(session.cameraConfig.cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        }.getOrDefault(90)
        sensorOrientation = resolved
        return resolved
    }

    /** Keeps the route coarse: a vertex every 35 cm is plenty to shape a heatmap. */
    private fun appendPathVertex(x: Float, z: Float) {
        val last = pathPoints.lastOrNull()
        if (last == null ||
            pathBroken ||
            kotlin.math.hypot(x - last.x, z - last.z) >= WalkPath.VERTEX_SPACING_METERS
        ) {
            pathPoints += PathPoint(x, z, startsRun = pathBroken)
            pathBroken = false
        }
    }

    fun onTrackingHint(@StringRes hint: Int?) {
        _state.update { it.copy(trackingHint = hint) }
    }

    /** ARCore's own account of why tracking is unhappy, kept for the census. */
    fun onTrackingFailure(reason: TrackingFailureReason?) {
    }

    /**
     * Freezes the walk-through into a [CompletedScan].
     *
     * Planes are read once here rather than tracked per frame: ARCore keeps
     * growing and merging them for the whole session, so the final call sees
     * the most complete set.
     */
    fun finish() {
        val current = _state.value
        // One last sweep so a wall still in view at the finish is not missed.
        session?.let { collectPlanes(it) }
        // Depth, when the hardware offered it, grows the walls the planes saw
        // into the walls the room actually has.
        val room = (PlaneHarvester.build(trackedWalls.values.toList(), floorYSeen, current.grid)
            ?: RoomModel.fromSamplesOnly(current.grid))
            ?.let(depthProbe::refine)
        if (room != null) {

            val scan = CompletedScan(
                id = scanId,
                savedAtMs = System.currentTimeMillis(),
                room = room,
                grid = current.grid,
                ssid = current.link.ssid,
                durationMs = current.elapsedMs,
                path = WalkPath(pathPoints.toList()),
                startAzimuthDegrees = startAzimuth,
                geoAnchor = location.anchor,
                photos = photos.toList(),
                apSurvey = apSurvey.snapshot(),
                speedSurvey = SpeedSurvey(speedReadings.toList()),
            )
            ScanStore.put(scan)
            repository.save(scan)
        }
        _state.update { it.copy(finished = true) }
    }

    private companion object {
        /** Half a pace. Finer cells need denser readings to be worth the resolution. */
        const val MIN_MOVE_METERS = 0.12f

        /** No single frame-to-frame stride is a real one past this. */
        const val MAX_STEP_METERS = 0.9f

        /**
         * A scanning walk measured 0.46 m/s, which at 30 fps is about 15 mm of
         * real motion per frame. The census showed frames moving 200 mm — six
         * metres per second — so most of what a loose ceiling let through was
         * never movement at all, and it accumulated: a 3 x 4 m room came out
         * 13.7 m long.
         *
         * 1.8 m/s is still faster than anyone walks while scanning, and the
         * elapsed time is real, so a genuinely slow frame is not punished for it.
         */
        const val MAX_WALK_SPEED_MPS = 1.8f

        /** Guards the division when two frames arrive almost together. */
        const val MIN_GAP_MS = 60L

        /** Vertical teleports are what put a floor eleven metres in the air. */
        const val MAX_RISE_METERS = 0.6f

        /** Roughly once a second at 30 fps. */
        const val WALL_POLL_FRAMES = 30

        /** Same cadence as wall polling; depth images are far costlier to read. */
        const val DEPTH_POLL_FRAMES = 30

        /** Twice a second is plenty; the underlying list changes far slower. */
        const val SURVEY_POLL_FRAMES = 15

        /** Every 40th sweep is ~20 s apart, inside the four-per-two-minutes cap. */
        const val SWEEPS_PER_REFRESH = 40

        /** Readings closer together than this add nothing but weight. */
        const val SURVEY_SPACING_METERS = 0.6f

        /** Far enough apart that probes never overlap on a slow link. */
        const val SPEED_INTERVAL_MS = 6_000L

        /** How long the shutter confirmation stays lit. */
        const val FLASH_MS = 420L
    }
}
