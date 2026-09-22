package com.sinyal.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.content.res.Resources
import com.sinyal.app.R
import com.sinyal.app.data.CompletedScan
import com.sinyal.app.heat.HeatTile
import com.sinyal.app.heat.SignalScale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a shareable top-down map of a scan.
 *
 * Deliberately re-drawn on a 2D canvas rather than screenshotting the 3D view.
 * A screenshot inherits whatever angle the user left the model at, along with
 * perspective foreshortening that makes distances unreadable; a plan view can
 * carry a scale bar, a north arrow and a legend that all mean something.
 *
 * When the compass reading is available the whole plan is rotated so north
 * points up, which is what lets the image be compared against any other map.
 */
object MapRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1500
    private const val MARGIN = 64f
    private const val MAP_TOP = 210f
    private const val MAP_BOTTOM = 1080f

    private const val BACKGROUND = 0xFF06070A.toInt()
    private const val PANEL = 0xFF12141D.toInt()
    private const val TEXT_PRIMARY = 0xFFEEF1F7.toInt()
    private const val TEXT_SECONDARY = 0xFF9AA1B4.toInt()
    private const val TEXT_TERTIARY = 0xFF5C6376.toInt()
    private const val ACCENT = 0xFFA78BFA.toInt()

    fun render(
        res: Resources,
        scan: CompletedScan,
        tiles: List<HeatTile>,
        scale: SignalScale,
        title: String,
        subtitle: String,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        drawHeader(canvas, res, title, subtitle)

        val projection = Projection.of(scan, tiles)
        drawTiles(canvas, tiles, scale, projection)
        drawPath(canvas, scan, projection)
        drawMarkers(canvas, scan, projection)
        drawNorth(canvas, res, scan)
        drawLegend(canvas, res, scale)
        drawFooter(canvas, res, scan)

        return bitmap
    }

    // ---- geometry ----

    /** Maps world metres onto canvas pixels, north-up when a heading is known. */
    private class Projection(
        private val rotationRadians: Float,
        private val centreX: Float,
        private val centreZ: Float,
        private val pixelsPerMeter: Float,
    ) {
        fun x(worldX: Float, worldZ: Float): Float {
            val dx = worldX - centreX
            val dz = worldZ - centreZ
            return WIDTH / 2f + (dx * cos(rotationRadians) - dz * sin(rotationRadians)) *
                pixelsPerMeter
        }

        fun y(worldX: Float, worldZ: Float): Float {
            val dx = worldX - centreX
            val dz = worldZ - centreZ
            return (MAP_TOP + MAP_BOTTOM) / 2f +
                (dx * sin(rotationRadians) + dz * cos(rotationRadians)) * pixelsPerMeter
        }

        fun size(meters: Float): Float = meters * pixelsPerMeter

        companion object {
            fun of(scan: CompletedScan, tiles: List<HeatTile>): Projection {
                val rotation = Math.toRadians(
                    (scan.startAzimuthDegrees ?: 0f).toDouble(),
                ).toFloat()

                val bounds = scan.room.bounds
                val centreX = bounds.centerX
                val centreZ = bounds.centerZ

                // Rotation changes the footprint, so measure the extent after it.
                var halfWidth = 0.5f
                var halfHeight = 0.5f
                (tiles.map { it.x to it.z } + scan.path.points.map { it.x to it.z })
                    .forEach { (worldX, worldZ) ->
                        val dx = worldX - centreX
                        val dz = worldZ - centreZ
                        halfWidth = max(
                            halfWidth,
                            kotlin.math.abs(dx * cos(rotation) - dz * sin(rotation)),
                        )
                        halfHeight = max(
                            halfHeight,
                            kotlin.math.abs(dx * sin(rotation) + dz * cos(rotation)),
                        )
                    }

                val usableWidth = WIDTH - MARGIN * 2
                val usableHeight = MAP_BOTTOM - MAP_TOP - MARGIN
                val ppm = minOf(
                    usableWidth / (halfWidth * 2 + 1f),
                    usableHeight / (halfHeight * 2 + 1f),
                )
                return Projection(rotation, centreX, centreZ, ppm)
            }
        }
    }

    // ---- layers ----

    private fun drawHeader(
        canvas: Canvas,
        res: Resources,
        title: String,
        subtitle: String,
    ) {
        val brand = textPaint(ACCENT, 30f, bold = true)
        canvas.drawText(res.getString(R.string.app_name_short), MARGIN, 78f, brand)

        canvas.drawText(title, MARGIN, 138f, textPaint(TEXT_PRIMARY, 52f, bold = true))
        canvas.drawText(subtitle, MARGIN, 180f, textPaint(TEXT_SECONDARY, 30f))
    }

    private fun drawTiles(
        canvas: Canvas,
        tiles: List<HeatTile>,
        scale: SignalScale,
        projection: Projection,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        tiles.forEach { tile ->
            paint.color = rampColor(scale.normalize(tile.rssiDbm))
            val half = projection.size(tile.size) / 2f + 0.5f
            val cx = projection.x(tile.x, tile.z)
            val cy = projection.y(tile.x, tile.z)
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        }
    }

    private fun drawPath(canvas: Canvas, scan: CompletedScan, projection: Projection) {
        val points = scan.path.points
        if (points.size < 2) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xE6FFFFFF.toInt()
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val px = projection.x(point.x, point.z)
            val py = projection.y(point.x, point.z)
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawMarkers(
        canvas: Canvas,
        scan: CompletedScan,
        projection: Projection,
    ) {
        scan.photos.forEachIndexed { index, photo ->
            marker(
                canvas,
                projection.x(photo.x, photo.z),
                projection.y(photo.x, photo.z),
                0xFF4FC3F7.toInt(),
                "${index + 1}",
            )
        }
        scan.path.points.firstOrNull()?.let { start ->
            marker(
                canvas,
                projection.x(start.x, start.z),
                projection.y(start.x, start.z),
                0xFFE8ECF5.toInt(),
                "S",
            )
        }
    }

    private fun marker(canvas: Canvas, cx: Float, cy: Float, tint: Int, glyph: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = BACKGROUND
        }
        canvas.drawCircle(cx, cy, 22f, fill)
        canvas.drawCircle(cx, cy, 22f, ring)

        val label = textPaint(BACKGROUND, 26f, bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(glyph, cx, cy + 9f, label)
    }

    private fun drawNorth(canvas: Canvas, res: Resources, scan: CompletedScan) {
        if (scan.startAzimuthDegrees == null) return
        val cx = WIDTH - MARGIN - 40f
        val cy = MAP_TOP + 50f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy + 30f, cx, cy - 30f, paint)
        val head = Path().apply {
            moveTo(cx, cy - 42f)
            lineTo(cx - 12f, cy - 22f)
            lineTo(cx + 12f, cy - 22f)
            close()
        }
        canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
        canvas.drawText(
            res.getString(R.string.map_north),
            cx - 9f,
            cy + 58f,
            textPaint(ACCENT, 28f, bold = true),
        )
    }

    private fun drawLegend(canvas: Canvas, res: Resources, scale: SignalScale) {
        val top = MAP_BOTTOM + 40f
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL }
        canvas.drawRoundRect(
            MARGIN, top, WIDTH - MARGIN, top + 190f, 28f, 28f, panel,
        )

        val barTop = top + 34f
        val barLeft = MARGIN + 32f
        val barRight = WIDTH - MARGIN - 32f
        val steps = 60
        val stepWidth = (barRight - barLeft) / steps
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(steps) { index ->
            paint.color = rampColor(index / (steps - 1f))
            canvas.drawRect(
                barLeft + index * stepWidth,
                barTop,
                barLeft + (index + 1) * stepWidth + 1f,
                barTop + 22f,
                paint,
            )
        }

        canvas.drawText(
            "${scale.minDbm} dBm",
            barLeft,
            barTop + 54f,
            textPaint(TEXT_TERTIARY, 26f),
        )
        val right = textPaint(TEXT_TERTIARY, 26f).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("${scale.maxDbm} dBm", barRight, barTop + 54f, right)

        canvas.drawText(
            res.getString(R.string.map_legend),
            barLeft,
            barTop + 106f,
            textPaint(TEXT_SECONDARY, 25f),
        )
        canvas.drawText(
            res.getString(R.string.map_path_note),
            barLeft,
            barTop + 142f,
            textPaint(TEXT_TERTIARY, 25f),
        )
    }

    private fun drawFooter(canvas: Canvas, res: Resources, scan: CompletedScan) {
        val grid = scan.grid
        val line = res.getString(
            R.string.map_stats,
            grid.cellCount,
            grid.weakestRssi ?: 0,
            grid.strongestRssi ?: 0,
        )
        canvas.drawText(line, MARGIN, HEIGHT - 68f, textPaint(TEXT_SECONDARY, 28f))
        canvas.drawText(
            res.getString(R.string.map_footer),
            MARGIN,
            HEIGHT - 30f,
            textPaint(TEXT_TERTIARY, 24f),
        )
    }

    // ---- helpers ----

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    /** Same stops as the on-screen ramp, so an export matches what was on screen. */
    private fun rampColor(fraction: Float): Int {
        val stops = listOf(
            0f to 0xFFFF4D6A.toInt(),
            0.25f to 0xFFFF8A4C.toInt(),
            0.45f to 0xFFFFC24B.toInt(),
            0.7f to 0xFF9BE564.toInt(),
            1f to 0xFF22E0A3.toInt(),
        )
        val f = fraction.coerceIn(0f, 1f)
        for (index in 0 until stops.lastIndex) {
            val (lowPos, lowColor) = stops[index]
            val (highPos, highColor) = stops[index + 1]
            if (f <= highPos) {
                val span = highPos - lowPos
                val t = if (span <= 0f) 0f else (f - lowPos) / span
                return blend(lowColor, highColor, t)
            }
        }
        return stops.last().second
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )
}
