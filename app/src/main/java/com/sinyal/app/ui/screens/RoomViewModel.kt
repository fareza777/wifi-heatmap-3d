package com.sinyal.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sinyal.app.data.CompletedScan
import com.sinyal.app.data.PlacePhoto
import com.sinyal.app.data.ScanRepository
import com.sinyal.app.data.ScanStore
import com.sinyal.app.heat.HeatField
import com.sinyal.app.heat.HeatTile
import com.sinyal.app.export.MapRenderer
import com.sinyal.app.export.NarrativeWriter
import com.sinyal.app.export.ShareHelper
import com.sinyal.app.heat.SignalScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sinyal.app.R

/**
 * What the result screen draws.
 *
 * Deliberately short. It used to carry the strongest and weakest points, a
 * router suggestion and a bearing for every landmark — all derived from the
 * same drifting coordinates, all removed from the screen because they read as
 * precise and were not. Keeping them computed here only invited them back.
 */
data class RoomUiState(
    val scan: CompletedScan? = null,
    val tiles: List<HeatTile> = emptyList(),
    val scale: SignalScale? = null,
    val building: Boolean = true,
)

/**
 * Bakes the measured cells into a dense floor mosaic.
 *
 * The measured grid is deliberately coarse (0.5 m) so the capture stays cheap;
 * this resamples it onto a finer lattice through [HeatField] so the floor reads
 * as a smooth field rather than a handful of blocks. Tile count is capped
 * because every tile becomes its own renderable on a low-end GPU.
 */
class RoomViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScanRepository(app)

    // Read once here rather than at each call: every export needs it, and the
    // ViewModel already owns the Application these come from.
    private val resources = app.resources

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Renders the plan view and hands it to the share sheet.
     *
     * Rendering runs off the main thread: a 1080x1500 bitmap with a few hundred
     * tiles is not free, and the share sheet should not open onto a frozen frame.
     */
    fun exportImage() = viewModelScope.launch {
        val current = _state.value
        val scan = current.scan ?: return@launch
        val scale = current.scale ?: return@launch

        val bitmap = withContext(Dispatchers.Default) {
            MapRenderer.render(
                res = resources,
                scan = scan,
                tiles = current.tiles,
                scale = scale,
                title = scan.ssid ?: resources.getString(R.string.map_default_title),
                subtitle = resources.getString(R.string.map_subtitle, scan.grid.cellCount),
            )
        }
        ShareHelper.shareImage(
            getApplication(),
            bitmap,
            resources.getString(R.string.map_share_caption, scan.ssid.orEmpty()).trim(),
        )
    }

    /** Builds the written report from the same numbers the screen is showing. */
    fun exportNarrative() = viewModelScope.launch {
        val current = _state.value
        val scan = current.scan ?: return@launch

        val text = withContext(Dispatchers.Default) {
            NarrativeWriter.write(res = resources, scan = scan)
        }
        ShareHelper.shareText(getApplication(), text)
    }

    /** Renames a landmark and refreshes both the store and what is on screen. */
    fun relabelPhoto(photoId: String, label: String) = viewModelScope.launch {
        val scan = _state.value.scan ?: return@launch
        val trimmed = label.trim().ifBlank { return@launch }
        val updated = scan.copy(
            photos = scan.photos.map { photo ->
                if (photo.id == photoId) photo.copy(label = trimmed) else photo
            },
        )
        ScanStore.put(updated)
        _state.update { it.copy(scan = updated) }
        withContext(Dispatchers.IO) { repository.save(updated) }
    }

    /** Absolute path a photo lives at, for decoding into the UI. */
    fun photoPath(photo: PlacePhoto): String {
        val scanId = _state.value.scan?.id ?: return ""
        return repository.photoFile(scanId, photo.fileName).absolutePath
    }

    private fun load() = viewModelScope.launch {
        val scan = ScanStore.latest
        if (scan == null) {
            _state.update { it.copy(building = false) }
            return@launch
        }
        val cells = scan.grid.occupiedCells
        val scale = SignalScale.forCells(cells)
        val tiles = withContext(Dispatchers.Default) { bake(scan, scale) }

        _state.update {
            it.copy(scan = scan, tiles = tiles, scale = scale, building = false)
        }
    }

    private fun bake(scan: CompletedScan, scale: SignalScale): List<HeatTile> {
        // Only real detected walls are handed over. An estimated perimeter is
        // just a box around the walk, so it would cost the crossing test on
        // every sample and never block anything worth blocking.
        val field = HeatField(
            cells = scan.grid.occupiedCells,
            walls = if (scan.room.wallsAreEstimated) emptyList() else scan.room.walls,
        )
        val bounds = scan.room.bounds

        val columns = (bounds.width / TILE_SIZE).toInt().coerceAtLeast(1)
        val rows = (bounds.depth / TILE_SIZE).toInt().coerceAtLeast(1)
        // Coarsen rather than truncate, so a large home still gets full coverage —
        // but never past MAX_TILE_SIZE. Unbounded coarsening once produced 5 m
        // slabs, which stopped reading as a heatmap at all.
        val stride = maxOf(1, ((columns.toLong() * rows) / MAX_TILES).toInt() + 1)
        val step = (TILE_SIZE * stride).coerceAtMost(MAX_TILE_SIZE)

        val tiles = mutableListOf<HeatTile>()
        var z = bounds.minZ
        while (z <= bounds.maxZ) {
            var x = bounds.minX
            while (x <= bounds.maxX) {
                // Only paint where the user actually went. Interpolation happily
                // extends into rooms nobody entered, and a confident colour over
                // unvisited floor is an invention, not a measurement.
                val onRoute = scan.path.isEmpty ||
                    scan.path.distanceTo(x, z) <= PATH_MASK_METERS
                if (onRoute) field.estimateAt(x, z)?.let { estimate ->
                    // A tile this thin on evidence is a guess wearing a colour;
                    // leaving the floor bare says so honestly.
                    if (estimate.confidence >= MIN_CONFIDENCE) {
                        tiles += HeatTile(
                            x = x,
                            z = z,
                            size = step,
                            rssiDbm = estimate.rssiDbm,
                            heightMeters = reliefHeight(estimate.rssiDbm, scale),
                            confidence = estimate.confidence,
                        )
                    }
                }
                x += step
            }
            z += step
        }
        return tiles
    }

    /** Maps signal onto tile thickness; the floor stays a solid slab at the low end. */
    private fun reliefHeight(rssiDbm: Int, scale: SignalScale): Float =
        MIN_RELIEF + scale.normalize(rssiDbm) * (MAX_RELIEF - MIN_RELIEF)

    private companion object {
        const val MIN_RELIEF = 0.04f
        const val MAX_RELIEF = 0.75f
        const val PATH_MASK_METERS = 1.2f

        /** Below this the tile rests on one distant reading and is dropped. */
        const val MIN_CONFIDENCE = 0.45f
        const val TILE_SIZE = 0.24f
        const val MAX_TILE_SIZE = 0.45f
        const val MAX_TILES = 520
    }
}
