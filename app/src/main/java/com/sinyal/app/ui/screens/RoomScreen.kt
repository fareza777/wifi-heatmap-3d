package com.sinyal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.filament.MaterialInstance
import com.sinyal.app.ar.RoomModel
import com.sinyal.app.ar.WalkPath
import com.sinyal.app.data.PlacePhoto
import com.sinyal.app.heat.SignalScale
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.BannerAd
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.components.PhotoStrip
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.components.rememberFullImage
import com.sinyal.app.ui.components.signalColorAt
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.LocalPalette
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.SignalQuality
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.Scene
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import com.sinyal.app.ar.SpeedSurvey
import com.sinyal.app.ui.components.LinkRow

/**
 * Discrete tints the ramp is quantised into.
 *
 * One material is built per step, so this trades GPU objects for smoothness.
 * At 14 the banding between adjacent tiles was visible as stripes; 26 reads as
 * a continuous field while still costing a fixed, small number of materials.
 */
private const val RAMP_STEPS = 26
private const val WALL_THICKNESS = 0.09f

/** Walls are rendered at this fraction of their real height, dollhouse-style. */
private const val WALL_CUTAWAY_FRACTION = 0.45f
private const val SLAB_THICKNESS = 0.06f
private const val MARKER_HEIGHT = 1.15f
private const val MARKER_MAST_THICKNESS = 0.035f
private const val MARKER_BEAD_RADIUS = 0.1f
private const val PHOTO_PIN_HEIGHT = 1.7f
private const val PHOTO_MAST_THICKNESS = 0.075f
private const val PHOTO_DISC_SIZE = 0.42f
private const val GRID_LINE_WIDTH = 0.015f
private const val GRID_LINE_HEIGHT = 0.005f

@Composable
fun RoomScreen(
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onOpenCoverage: () -> Unit,
    adsRemoved: Boolean,
    modifier: Modifier = Modifier,
    viewModel: RoomViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scan = state.scan
    // The photo subtitle is built inside a lambda the compiler does not treat as
    // composable, so the strings are read through the context rather than
    // stringResource.
    var panel by remember { mutableStateOf(RoomPanel.COMPACT) }
    var selectedPhoto by remember { mutableStateOf<PlacePhoto?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Ink.Raised, Ink.Base)),
            ),
    ) {
        val span = scan?.let {
            maxOf(it.room.bounds.width, it.room.bounds.depth).coerceAtLeast(2.5f)
        } ?: 4f
        val orbit = rememberOrbitState(span)
        var viewport by remember { mutableStateOf(IntSize.Zero) }

        if (scan != null) {
            // The map now fills the screen; the panel floats over it rather than
            // taking a slice of it away.
            Dollhouse(
                room = scan.room,
                tiles = state.tiles,
                path = scan.path,
                scale = state.scale,
                photos = scan.photos,
                highlightedPhotoId = selectedPhoto?.id,
                orbit = orbit,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewport = it }
                    .mapGestures(orbit, viewport.height) { tap ->
                        val hit = nearestPhoto(
                            tap = tap,
                            photos = scan.photos,
                            room = scan.room,
                            orbit = orbit,
                            viewport = viewport,
                        )
                        if (hit != null) {
                            selectedPhoto = hit
                        } else if (scan.photos.isEmpty() || !nearAnyPhoto(
                                tap = tap,
                                photos = scan.photos,
                                room = scan.room,
                                orbit = orbit,
                                viewport = viewport,
                            )
                        ) {
                            // Tapping empty floor hides the reading, the way a
                            // photo viewer hides its chrome. Discovered by
                            // accident, which is the point — the small link in
                            // the panel header was not being found.
                            panel = if (panel == RoomPanel.MINIMIZED) {
                                RoomPanel.COMPACT
                            } else {
                                RoomPanel.MINIMIZED
                            }
                        }
                    },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                BackBar(title = stringResource(R.string.room_title), onBack = onBack)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    CompassRose(
                        startAzimuthDegrees = scan?.startAzimuthDegrees,
                        cameraYawDegrees = orbit.yawDegrees,
                    )
                    // Panning can wander off the model; this is the way back.
                    MapButton(text = stringResource(R.string.room_recenter), onClick = orbit::reset)
                }
            }

            Spacer(Modifier.weight(1f))

            when {
                state.building -> InfoCard(stringResource(R.string.room_building))
                scan == null -> InfoCard(stringResource(R.string.room_no_scan))
                panel == RoomPanel.MINIMIZED -> ShowPanelPill { panel = RoomPanel.COMPACT }
                else -> ResultPanel(
                    room = scan.room,
                    coveredAreaSqM = scan.grid.coveredAreaSqM,
                    measuredCells = scan.grid.cellCount,
                    weakest = scan.grid.weakestRssi,
                    strongest = scan.grid.strongestRssi,
                    ssid = scan.ssid,
                    scale = state.scale,
                    panel = panel,
                    onPanelChange = { panel = it },
                    photos = scan.photos,
                    photoPathFor = viewModel::photoPath,
                    speedSurvey = scan.speedSurvey,
                    onSelectPhoto = { selectedPhoto = it },
                    onExportImage = viewModel::exportImage,
                    onExportNarrative = viewModel::exportNarrative,
                    onRescan = onRescan,
                    onOpenCoverage = onOpenCoverage.takeIf { !scan.apSurvey.isEmpty },
                )
            }
            Spacer(Modifier.height(10.dp))
            BannerAd(adsRemoved = adsRemoved)
            Spacer(Modifier.height(10.dp))
        }

        selectedPhoto?.let { photo ->
            PhotoDetailDialog(
                photo = photo,
                path = viewModel.photoPath(photo),
                onRename = { label -> viewModel.relabelPhoto(photo.id, label) },
                onDismiss = { selectedPhoto = null },
            )
        }
    }
}

/**
 * Finds which pin a tap landed on.
 *
 * Photo positions are projected to the screen and compared in pixels, so the
 * generous target is a finger-sized disc regardless of how far the map is
 * zoomed out.
 */
/**
 * Whether a tap was plausibly aimed at a pin even though it missed one.
 *
 * Used to decide that a near miss should do nothing, rather than hide the panel
 * — the wrong response to "I tried to open that photo".
 */
private fun nearAnyPhoto(
    tap: androidx.compose.ui.geometry.Offset,
    photos: List<PlacePhoto>,
    room: RoomModel,
    orbit: OrbitState,
    viewport: IntSize,
): Boolean = nearestPhoto(tap, photos, room, orbit, viewport, TAP_FORGIVENESS_PX) != null

private fun nearestPhoto(
    tap: androidx.compose.ui.geometry.Offset,
    photos: List<PlacePhoto>,
    room: RoomModel,
    orbit: OrbitState,
    viewport: IntSize,
    radiusPx: Float = TAP_RADIUS_PX,
): PlacePhoto? {
    if (photos.isEmpty() || viewport == IntSize.Zero) return null

    var best: PlacePhoto? = null
    var bestDistance = radiusPx

    photos.forEach { photo ->
        val screen = projectToScreen(
            Float3(
                photo.x - room.bounds.centerX,
                PHOTO_PIN_HEIGHT,
                photo.z - room.bounds.centerZ,
            ),
            orbit,
            viewport,
        ) ?: return@forEach

        val distance = kotlin.math.hypot(screen.x - tap.x, screen.y - tap.y)
        if (distance < bestDistance) {
            bestDistance = distance
            best = photo
        }
    }
    return best
}

/**
 * How close a tap has to land to count as hitting a pin.
 *
 * Generous on purpose: the pin is a thin mast with a small bead, the model is
 * usually being viewed at an angle, and a miss now costs more than it used to
 * because an empty-space tap hides the panel. A tap that was meant for the pin
 * and hid the panel instead reads as the pin being broken.
 */
private const val TAP_RADIUS_PX = 220f

/** Wide enough that a near miss is read as intent, not as tapping the floor. */
private const val TAP_FORGIVENESS_PX = 340f

/**
 * The room seen from outside.
 *
 * Everything is translated so the room centre sits at the world origin, which
 * keeps the orbit camera's framing independent of wherever ARCore happened to
 * put its origin during the walk.
 */
@Composable
private fun Dollhouse(
    room: RoomModel,
    tiles: List<com.sinyal.app.heat.HeatTile>,
    path: WalkPath,
    scale: SignalScale?,
    photos: List<PlacePhoto>,
    highlightedPhotoId: String?,
    orbit: OrbitState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val floorTint = palette.sceneFloor
    val wallTint = palette.sceneWall
    val gridTint = palette.strokeStrong

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine) { }

    LaunchedEffect(
        orbit.yawDegrees,
        orbit.pitchDegrees,
        orbit.distance,
        orbit.targetX,
        orbit.targetZ,
    ) {
        cameraNode.lookAt(orbit.eye, orbit.target, Float3(0f, 1f, 0f))
    }

    Scene(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        cameraNode = cameraNode,
        cameraManipulator = null,
    ) {
        val ramp = remember(materialLoader) { rampMaterials(materialLoader) }
        val wallMaterial = remember(materialLoader, wallTint) {
            materialLoader.createColorInstance(wallTint, 0f, 0.85f, 0.05f)
        }
        val gridMaterial = remember(materialLoader, gridTint) {
            materialLoader.createColorInstance(gridTint, 0f, 0.9f, 0.02f)
        }
        val slabMaterial = remember(materialLoader, floorTint) {
            materialLoader.createColorInstance(floorTint, 0f, 0.95f, 0.02f)
        }
        val startMaterial = remember(materialLoader) {
            materialLoader.createColorInstance(Color(0xFFE8ECF5), 0.1f, 0.6f, 0.2f)
        }
        val photoMaterial = remember(materialLoader) {
            materialLoader.createColorInstance(Color(0xFF4FC3F7), 0.3f, 0.3f, 0.5f)
        }
        val highlightMaterial = remember(materialLoader) {
            materialLoader.createColorInstance(Color(0xFFFFFFFF), 0.1f, 0.2f, 0.8f)
        }

        val offsetX = room.bounds.centerX
        val offsetZ = room.bounds.centerZ

        // A solid slab under everything, so unmeasured patches read as floor
        // that was never walked rather than holes through the model.
        CubeNode(
            size = Float3(room.bounds.width, SLAB_THICKNESS, room.bounds.depth),
            materialInstance = slabMaterial,
            position = Float3(0f, -SLAB_THICKNESS / 2f, 0f),
        )

        // A one-metre grid on the slab: without a scale reference the model could
        // be a studio flat or a warehouse.
        val halfWidth = room.bounds.width / 2f
        val halfDepth = room.bounds.depth / 2f
        for (step in -halfWidth.toInt()..halfWidth.toInt()) {
            key("gridX", step) {
                CubeNode(
                    size = Float3(GRID_LINE_WIDTH, GRID_LINE_HEIGHT, room.bounds.depth),
                    materialInstance = gridMaterial,
                    position = Float3(step.toFloat(), -SLAB_THICKNESS / 2f + 0.031f, 0f),
                )
            }
        }
        for (step in -halfDepth.toInt()..halfDepth.toInt()) {
            key("gridZ", step) {
                CubeNode(
                    size = Float3(room.bounds.width, GRID_LINE_HEIGHT, GRID_LINE_WIDTH),
                    materialInstance = gridMaterial,
                    position = Float3(0f, -SLAB_THICKNESS / 2f + 0.031f, step.toFloat()),
                )
            }
        }

        tiles.forEach { tile ->
            key(tile.x, tile.z) {
                CubeNode(
                    size = Float3(tile.size, tile.heightMeters, tile.size),
                    materialInstance = ramp[rampIndexFor(tile.rssiDbm, scale)],
                    position = Float3(
                        tile.x - offsetX,
                        tile.heightMeters / 2f,
                        tile.z - offsetZ,
                    ),
                )
            }
        }

        path.points.firstOrNull()?.let { origin ->
            Pin(
                x = origin.x - offsetX,
                z = origin.z - offsetZ,
                height = MARKER_HEIGHT * 0.55f,
                beadRadius = MARKER_BEAD_RADIUS * 0.9f,
                material = startMaterial,
            )
        }

        // Every photographed landmark gets its own pin, so a coloured cell can be
        // recognised as "the desk" rather than a coordinate.
        photos.forEach { photo ->
            key("photo", photo.id) {
                // The opened photo's pin grows, which is what ties the thumbnail
                // being looked at to a specific place on the model.
                val selected = photo.id == highlightedPhotoId
                val px = photo.x - offsetX
                val pz = photo.z - offsetZ

                // A disc on the floor, so the pin is findable even when the pole
                // is edge-on to the camera or lost against a bright tile.
                CubeNode(
                    size = Float3(PHOTO_DISC_SIZE, 0.03f, PHOTO_DISC_SIZE),
                    materialInstance = photoMaterial,
                    position = Float3(px, 0.05f, pz),
                )
                Pin(
                    x = px,
                    z = pz,
                    height = PHOTO_PIN_HEIGHT,
                    beadRadius = MARKER_BEAD_RADIUS * 1.7f,
                    material = photoMaterial,
                    mastThickness = PHOTO_MAST_THICKNESS,
                )
                if (selected) {
                    SphereNode(
                        radius = MARKER_BEAD_RADIUS * 2.6f,
                        materialInstance = highlightMaterial,
                        position = Float3(px, PHOTO_PIN_HEIGHT, pz),
                    )
                }
            }
        }

        // Walls are drawn as a cutaway, the way a physical dollhouse is built:
        // full height would enclose the floor and hide the very thing being shown.
        val drawnHeight = room.wallHeight * WALL_CUTAWAY_FRACTION
        room.walls.forEachIndexed { index, wall ->
            key(index) {
                CubeNode(
                    size = Float3(wall.length, drawnHeight, WALL_THICKNESS),
                    materialInstance = wallMaterial,
                    position = Float3(
                        wall.centerX - offsetX,
                        drawnHeight / 2f,
                        wall.centerZ - offsetZ,
                    ),
                    rotation = Float3(0f, wall.yawDegrees, 0f),
                )
            }
        }
    }
}

/**
 * A pin: a thin mast with a bead on top, standing at a point on the floor.
 *
 * Drawn as two nodes rather than a model so it costs nothing to place dozens of
 * them, and so the bead can be sized independently — that is what makes a pin
 * findable when the mast is edge-on to the camera.
 */
@Composable
private fun io.github.sceneview.SceneScope.Pin(
    x: Float,
    z: Float,
    height: Float,
    beadRadius: Float,
    material: com.google.android.filament.MaterialInstance,
    mastThickness: Float = MARKER_MAST_THICKNESS,
) {
    CubeNode(
        size = Float3(mastThickness, height, mastThickness),
        materialInstance = material,
        position = Float3(x, height / 2f, z),
    )
    SphereNode(
        radius = beadRadius,
        materialInstance = material,
        position = Float3(x, height, z),
    )
}

/**
 * How much of the reading sits over the model.
 *
 * [MINIMIZED] exists because the 3D map is the thing people came for, and on a
 * phone even a collapsed card covers the half of the room nearest the camera.
 */
enum class RoomPanel { MINIMIZED, COMPACT, FULL }

/** The one control left on screen when the panel is out of the way. */
@Composable
private fun ShowPanelPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Ink.Surface.copy(alpha = 0.92f))
            .border(1.dp, Ink.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.room_show_panel),
            style = MaterialTheme.typography.labelLarge,
            color = Accent.Bright,
        )
        Text(
            text = " ▲",
            style = MaterialTheme.typography.labelLarge,
            color = Accent.Bright,
        )
    }
}

@Composable
private fun ResultPanel(
    room: RoomModel,
    coveredAreaSqM: Float,
    measuredCells: Int,
    weakest: Int?,
    strongest: Int?,
    ssid: String?,
    scale: SignalScale?,
    panel: RoomPanel,
    onPanelChange: (RoomPanel) -> Unit,
    speedSurvey: SpeedSurvey,
    photos: List<PlacePhoto>,
    photoPathFor: (PlacePhoto) -> String,
    onSelectPhoto: (PlacePhoto) -> Unit,
    onExportImage: () -> Unit,
    /** Null when this scan recorded no other transmitters, so there is nothing to open. */
    onOpenCoverage: (() -> Unit)?,
    onExportNarrative: () -> Unit,
    onRescan: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        DragHandle(onClick = { onPanelChange(RoomPanel.MINIMIZED) })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ssid ?: stringResource(R.string.room_unknown_network),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextTone.Primary,
                )
                // Signal facts only. Area and room dimensions come out of the
                // same drifting coordinates that put the strongest-point marker in
                // the wrong place, so printing them would just be the same error
                // wearing a decimal point.
                Text(
                    text = if (weakest != null && strongest != null) {
                        stringResource(
                            R.string.room_summary_signal,
                            measuredCells,
                            weakest,
                            strongest,
                        )
                    } else {
                        stringResource(R.string.room_summary_signal_empty, measuredCells)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        if (panel == RoomPanel.FULL) {
                            R.string.room_collapse
                        } else {
                            R.string.room_expand
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent.Bright,
                    modifier = Modifier
                        .clickable {
                            onPanelChange(
                                if (panel == RoomPanel.FULL) {
                                    RoomPanel.COMPACT
                                } else {
                                    RoomPanel.FULL
                                },
                            )
                        }
                        .padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onPanelChange(RoomPanel.MINIMIZED) }
                        .padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.room_minimize),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTone.Secondary,
                    )
                    Text(
                        text = " ▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTone.Secondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        QuickLegend(tappableHint = photos.isNotEmpty())

        AnimatedVisibility(visible = panel == RoomPanel.FULL) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!speedSurvey.isEmpty) {
                    Spacer(Modifier.height(14.dp))
                    SpeedSummary(speedSurvey)
                }
                if (photos.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    PhotoStrip(
                        photos = photos,
                        pathFor = photoPathFor,
                        onSelect = onSelectPhoto,
                    )
                }

                Spacer(Modifier.height(12.dp))
                RampLegend(scale)

                Spacer(Modifier.height(14.dp))
                SymbolLegend()

                onOpenCoverage?.let { open ->
                    Spacer(Modifier.height(14.dp))
                    LinkRow(
                        text = stringResource(R.string.room_open_coverage),
                        onClick = open,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedAction(
                        stringResource(R.string.room_export_image),
                        onExportImage,
                        Modifier.weight(1f),
                    )
                    OutlinedAction(
                        stringResource(R.string.room_export_report),
                        onExportNarrative,
                        Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        GradientButton(text = stringResource(R.string.room_rescan), onClick = onRescan)
    }
}

/**
 * A landmark, full size, with the field to name it.
 *
 * Naming happens here rather than during the walk because this is the first
 * moment the photo and the map are visible together — which is what makes
 * "meja PC" the obvious label instead of a guess from memory.
 */
@Composable
private fun PhotoDetailDialog(
    photo: PlacePhoto,
    path: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(photo.id) { mutableStateOf(photo.label) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        confirmButton = {
            TextButton(onClick = {
                onRename(draft)
                onDismiss()
            }) {
                Text(stringResource(R.string.room_save), color = Accent.Bright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextTone.Secondary)
            }
        },
        title = {
            Text(
                text = stringResource(R.string.room_landmark_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextTone.Primary,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val bitmap = rememberFullImage(path)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = photo.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.room_landmark_name)) },
                    placeholder = { Text(stringResource(R.string.room_landmark_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** What every colour and shape in the model stands for. */
@Composable
private fun SymbolLegend() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.room_legend_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(8.dp))
        LegendRow(
            Color(0xFF4FC3F7),
            stringResource(R.string.room_legend_blue),
            stringResource(R.string.room_legend_blue_meaning),
        )
        LegendRow(
            Color(0xFFE8ECF5),
            stringResource(R.string.room_legend_pin),
            stringResource(R.string.room_legend_pin_meaning),
        )
        LegendRow(
            Color(0xFFB9C2D6),
            stringResource(R.string.room_legend_slab),
            stringResource(R.string.room_legend_slab_meaning),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.room_legend_colour_note),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Tertiary,
        )
    }
}

@Composable
private fun LegendRow(tint: Color, name: String, meaning: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(tint),
        )
        Text(
            text = stringResource(R.string.room_legend_entry, name, meaning),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * The four pins, always on screen.
 *
 * The full legend lives behind "Detail", but a reader who does not already know
 * what a purple pole means will not go looking for an explanation — they will
 * conclude the model is decorative. This row costs one line and removes that.
 */
/**
 * What the walk actually measured, as opposed to how loud the radio was.
 *
 * Kept as a summary rather than a second heatmap: a probe costs a real download
 * so a walk yields a dozen readings, and a dozen points is enough to rank rooms
 * against each other but nowhere near enough to paint a surface without
 * inventing most of it.
 */
@Composable
private fun SpeedSummary(survey: SpeedSurvey) {
    val slowest = survey.slowest ?: return
    val fastest = survey.fastest ?: return
    val average = survey.average ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.room_speed_title),
            style = MaterialTheme.typography.labelSmall,
            color = Accent.Bright,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.room_speed_summary, slowest, average, fastest),
            style = MaterialTheme.typography.bodyLarge,
            color = TextTone.Primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.room_speed_count, survey.readings.size),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.room_speed_note),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
    }
}

@Composable
private fun QuickLegend(tappableHint: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendChip(Color(0xFF4FC3F7), stringResource(R.string.room_chip_photo))
        }
        Text(
            text = stringResource(
                if (tappableHint) {
                    R.string.room_gesture_hint_photo
                } else {
                    R.string.room_gesture_hint
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LegendChip(tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tint),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Secondary,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** Small floating control that sits over the map without a card behind it. */
@Composable
private fun MapButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Ink.Surface.copy(alpha = 0.92f))
            .border(1.dp, Ink.StrokeStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextTone.Secondary,
        )
    }
}

/** Secondary action, quiet enough not to compete with the primary button. */
@Composable
private fun OutlinedAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(Ink.Raised)
            .border(1.dp, Ink.StrokeStrong, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextTone.Secondary,
        )
    }
}

/** Signals the panel can be opened, without spending a row on a label. */
@Composable
private fun DragHandle(onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                // A grab bar this thin is hard to hit, so the whole strip above
                // it takes the tap.
                .padding(top = 4.dp, bottom = 12.dp)
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Ink.StrokeStrong),
        )
    }
}

/** Continuous colour bar with the two anchors that actually matter labelled. */
@Composable
private fun RampLegend(scale: SignalScale?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            repeat(RAMP_STEPS) { step ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(signalColorAt(step / (RAMP_STEPS - 1f))),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.unit_dbm, scale?.minDbm ?: -100),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
            Text(
                text = stringResource(R.string.unit_dbm, scale?.maxDbm ?: -30),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextTone.Secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One material per ramp step; per-tile instances would allocate hundreds. */
private fun rampMaterials(loader: MaterialLoader): List<MaterialInstance> =
    (0 until RAMP_STEPS).map { step ->
        loader.createColorInstance(
            signalColorAt(step / (RAMP_STEPS - 1f)),
            0f,
            0.55f,
            0.25f,
        )
    }

private fun rampIndexFor(rssiDbm: Int, scale: SignalScale?): Int {
    val fraction = scale?.normalize(rssiDbm) ?: SignalQuality.normalize(rssiDbm)
    return (fraction * (RAMP_STEPS - 1)).toInt().coerceIn(0, RAMP_STEPS - 1)
}

