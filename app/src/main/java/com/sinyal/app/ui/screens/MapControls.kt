package com.sinyal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.TextTone
import dev.romainguy.kotlin.math.Float3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Vertical field of view the camera renders with; the projection below matches it. */
const val CAMERA_FOV_DEGREES = 45.0

/**
 * Orbit camera state, owned by the screen rather than by the renderer.
 *
 * Carries a look-at target as well as an orbit, so the view can be walked over
 * to a far corner instead of only spun around a fixed centre.
 */
class OrbitState(private val span: Float) {
    var yawDegrees by mutableFloatStateOf(0f)
    var pitchDegrees by mutableFloatStateOf(52f)
    var distance by mutableFloatStateOf(span * 1.5f)
    var targetX by mutableFloatStateOf(0f)
    var targetZ by mutableFloatStateOf(0f)

    val target: Float3 get() = Float3(targetX, 0f, targetZ)

    val eye: Float3
        get() {
            val pitch = Math.toRadians(pitchDegrees.toDouble())
            val yaw = Math.toRadians(yawDegrees.toDouble())
            val horizontal = distance * cos(pitch)
            return Float3(
                targetX + (horizontal * sin(yaw)).toFloat(),
                (distance * sin(pitch)).toFloat(),
                targetZ + (horizontal * cos(yaw)).toFloat(),
            )
        }

    fun orbitBy(dx: Float, dy: Float) {
        yawDegrees -= dx * ORBIT_SENSITIVITY
        pitchDegrees = (pitchDegrees + dy * ORBIT_SENSITIVITY).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    fun zoomBy(factor: Float) {
        distance = (distance / factor).coerceIn(span * 0.2f, span * 6f)
    }

    /**
     * Slides the look-at point across the floor.
     *
     * Screen movement is converted through the camera's own basis, so dragging
     * left always walks the view left regardless of how far it has been spun.
     */
    fun panBy(dx: Float, dy: Float, metersPerPixel: Float) {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val rightX = cos(yaw).toFloat()
        val rightZ = -sin(yaw).toFloat()
        val forwardX = -sin(yaw).toFloat()
        val forwardZ = -cos(yaw).toFloat()

        targetX -= (dx * rightX - dy * forwardX) * metersPerPixel
        targetZ -= (dx * rightZ - dy * forwardZ) * metersPerPixel

        // Keep the view tethered to the model; drifting into empty space is
        // disorienting and there is nothing out there to look at.
        val limit = span
        targetX = targetX.coerceIn(-limit, limit)
        targetZ = targetZ.coerceIn(-limit, limit)
    }

    fun reset() {
        yawDegrees = 0f
        pitchDegrees = 52f
        distance = span * 1.5f
        targetX = 0f
        targetZ = 0f
    }
}

@Composable
fun rememberOrbitState(span: Float): OrbitState = remember(span) { OrbitState(span) }

/**
 * One finger orbits, two fingers slide the view, pinch zooms, a tap selects.
 *
 * Written against the raw pointer stream rather than `detectTransformGestures`
 * because that helper reports a centroid without saying how many fingers made
 * it — and orbiting and panning have to be told apart to be usable.
 *
 * The handlers live on an overlay above the renderer: SceneView wraps a
 * SurfaceView, and that child swallowed touches before any modifier on the same
 * node could see them, which is why pinch-to-zoom previously did nothing.
 */
fun Modifier.mapGestures(
    state: OrbitState,
    viewportHeightPx: Int,
    onTap: (Offset) -> Unit,
): Modifier = this.pointerInput(viewportHeightPx) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        var travelled = 0f
        var sawSecondFinger = false

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break

            val pan = event.calculatePan()
            val zoom = event.calculateZoom()
            travelled += pan.getDistance()

            if (pressed >= 2) {
                sawSecondFinger = true
                if (zoom != 1f) state.zoomBy(zoom)
                state.panBy(pan.x, pan.y, metersPerPixel(state, viewportHeightPx))
            } else {
                state.orbitBy(pan.x, pan.y)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }

        if (!sawSecondFinger && travelled < TAP_SLOP_PX) onTap(first.position)
    }
}

/** How much world distance one pixel of drag covers at the current zoom. */
private fun metersPerPixel(state: OrbitState, viewportHeightPx: Int): Float {
    if (viewportHeightPx <= 0) return 0f
    val visibleHeight = 2.0 * state.distance * tan(Math.toRadians(CAMERA_FOV_DEGREES / 2.0))
    return (visibleHeight / viewportHeightPx).toFloat()
}

/**
 * Projects a world point onto the viewport.
 *
 * Written out by hand because the tap handler sits above the renderer and can no
 * longer ask it what was hit. The camera basis is reconstructed from the same
 * orbit state that positions it, so the two cannot drift apart.
 *
 * Returns null for points behind the camera.
 */
fun projectToScreen(
    world: Float3,
    state: OrbitState,
    viewport: IntSize,
): Offset? {
    if (viewport.width == 0 || viewport.height == 0) return null

    val eye = state.eye
    val target = state.target
    val forward = normalize(Float3(target.x - eye.x, target.y - eye.y, target.z - eye.z))
    val right = normalize(cross(forward, Float3(0f, 1f, 0f)))
    val up = cross(right, forward)

    val relative = Float3(world.x - eye.x, world.y - eye.y, world.z - eye.z)
    val depth = dot(relative, forward)
    if (depth <= 0.01f) return null

    val focal = (1.0 / tan(Math.toRadians(CAMERA_FOV_DEGREES / 2.0))).toFloat()
    val aspect = viewport.width.toFloat() / viewport.height.toFloat()

    val ndcX = dot(relative, right) / depth * focal / aspect
    val ndcY = dot(relative, up) / depth * focal

    return Offset(
        (ndcX * 0.5f + 0.5f) * viewport.width,
        (0.5f - ndcY * 0.5f) * viewport.height,
    )
}

/**
 * A compass, parked outside the plan rather than drawn into it.
 *
 * The previous long pole cutting across the model was read as part of the room.
 * A labelled arrow in the corner says the same thing and stops competing with
 * the data.
 */
@Composable
fun CompassRose(
    startAzimuthDegrees: Float?,
    cameraYawDegrees: Float,
    modifier: Modifier = Modifier,
) {
    if (startAzimuthDegrees == null) return

    val needleColor = Accent.Bright
    val relative = Math.toRadians((cameraYawDegrees - startAzimuthDegrees).toDouble())
    val rotation = Math.toDegrees(atan2(sin(relative), -cos(relative))).toFloat()

    Box(
        modifier = modifier
            .size(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Accent.Base.copy(alpha = 0.45f), RoundedCornerShape(29.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(30.dp).rotate(rotation)) {
            val arrow = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width * 0.82f, size.height)
                lineTo(size.width / 2f, size.height * 0.72f)
                lineTo(size.width * 0.18f, size.height)
                close()
            }
            drawPath(arrow, color = needleColor)
        }
        Text(
            text = "U",
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Primary,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ---- small vector helpers, kept local to avoid pulling in a math dependency ----

private fun normalize(v: Float3): Float3 {
    val length = kotlin.math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    return if (length == 0f) v else Float3(v.x / length, v.y / length, v.z / length)
}

private fun cross(a: Float3, b: Float3): Float3 = Float3(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x,
)

private fun dot(a: Float3, b: Float3): Float = a.x * b.x + a.y * b.y + a.z * b.z

private const val ORBIT_SENSITIVITY = 0.35f
private const val MIN_PITCH = 12f
private const val MAX_PITCH = 88f

/** Movement below this still counts as a tap rather than a drag. */
private const val TAP_SLOP_PX = 24f
