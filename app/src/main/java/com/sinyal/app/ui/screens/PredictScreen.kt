package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.R
import com.sinyal.app.data.ScanStore
import com.sinyal.app.heat.CoveragePrediction
import com.sinyal.app.heat.CoveragePredictor
import com.sinyal.app.ar.RoomModel
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.StatTile
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.Band
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PredictUiState(
    val room: RoomModel? = null,
    val ssid: String? = null,
    val band: Band = Band.GHZ_24,
    val routerX: Float = 0f,
    val routerZ: Float = 0f,
    val loaded: Boolean = false,
)

/**
 * Where the coverage would land if the router stood somewhere else.
 *
 * Sits on the last completed scan: the walls and floor it reconstructed are
 * the scene the ray-caster runs against, so predictions stay honest to the
 * user's actual home rather than an invented rectangle.
 */
class PredictViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PredictUiState())
    val state: StateFlow<PredictUiState> = _state.asStateFlow()

    init {
        val scan = ScanStore.latest
        val room = scan?.room
        _state.value = PredictUiState(
            room = room,
            ssid = scan?.ssid,
            routerX = room?.bounds?.centerX ?: 0f,
            routerZ = room?.bounds?.centerZ ?: 0f,
            loaded = true,
        )
    }

    fun setBand(band: Band) {
        _state.value = _state.value.copy(band = band)
    }

    /** Router position in scan coordinates; the Canvas maps taps back. */
    fun moveRouter(x: Float, z: Float) {
        _state.value = _state.value.copy(routerX = x, routerZ = z)
    }
}

/**
 * Drag the router around your floor plan; the colour field answers instantly.
 * This is a model, not a measurement — stated on screen because the two look
 * alike and are not.
 */
@Composable
fun PredictScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PredictViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val room = state.room

    val prediction = remember(room, state.routerX, state.routerZ, state.band) {
        room?.let {
            CoveragePredictor.predict(
                walls = it.walls,
                bounds = it.bounds,
                routerX = state.routerX,
                routerZ = state.routerZ,
                band = state.band,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            BackBar(title = stringResource(R.string.predict_title), onBack = onBack)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.predict_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )

            if (state.loaded && room == null) {
                Spacer(Modifier.height(18.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text(
                        text = stringResource(R.string.predict_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Tertiary,
                    )
                }
                Spacer(Modifier.height(28.dp))
                return@Column
            }

            if (room != null && prediction != null) {
                Spacer(Modifier.height(14.dp))
                BandPicker(selected = state.band, onPick = viewModel::setBand)

                Spacer(Modifier.height(12.dp))
                PredictCanvas(
                    prediction = prediction,
                    room = room,
                    routerX = state.routerX,
                    routerZ = state.routerZ,
                    accent = Accent.Bright,
                    onMove = viewModel::moveRouter,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.predict_best),
                        value = stringResource(R.string.unit_dbm, prediction.bestDbm),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.predict_worst),
                        value = stringResource(R.string.unit_dbm, prediction.worstDbm),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.predict_weak_area),
                        value = stringResource(R.string.predict_weak_value, prediction.weakAreaSqM),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.predict_drag_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent.Bright,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.predict_model_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BandPicker(selected: Band, onPick: (Band) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Band.GHZ_24, Band.GHZ_5).forEach { band ->
            val isPicked = band == selected
            val shape = RoundedCornerShape(12.dp)
            Text(
                text = band.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isPicked) TextTone.Primary else TextTone.Tertiary,
                modifier = Modifier
                    .clip(shape)
                    .background(if (isPicked) Accent.Glow else Ink.Surface)
                    .clickable { onPick(band) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The floor plan as a canvas: predicted field underneath, walls and the
 * draggable router on top.
 */
@Composable
private fun PredictCanvas(
    prediction: CoveragePrediction,
    room: RoomModel,
    routerX: Float,
    routerZ: Float,
    accent: Color,
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = prediction.bounds
    val aspect = (bounds.width / bounds.depth).coerceIn(0.5f, 2.5f)

    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Surface)
            .pointerInput(bounds) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { },
                    onDragCancel = { },
                ) { change, _ ->
                    val nx = change.position.x / size.width
                    val nz = change.position.y / size.height
                    onMove(
                        bounds.minX + nx * bounds.width,
                        bounds.minZ + nz * bounds.depth,
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / bounds.width
            val scaleY = size.height / bounds.depth
            fun toX(x: Float) = (x - bounds.minX) * scaleX
            fun toZ(z: Float) = (z - bounds.minZ) * scaleY

            val cellW = prediction.cellSizeMeters * scaleX
            val cellH = prediction.cellSizeMeters * scaleY
            prediction.cells.forEach { cell ->
                drawRect(
                    color = signalColorFor(cell.rssiDbm).copy(alpha = 0.34f),
                    topLeft = Offset(
                        toX(cell.x) - cellW / 2f,
                        toZ(cell.z) - cellH / 2f,
                    ),
                    size = Size(cellW + 0.5f, cellH + 0.5f),
                )
            }

            room.walls.forEach { wall ->
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(toX(wall.ax), toZ(wall.az)),
                    end = Offset(toX(wall.bx), toZ(wall.bz)),
                    strokeWidth = 4.dp.toPx(),
                )
            }

            // Router marker.
            val cx = toX(routerX)
            val cz = toZ(routerZ)
            drawCircle(color = accent, radius = 9.dp.toPx(), center = Offset(cx, cz))
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(cx, cz),
            )
        }
    }
}
