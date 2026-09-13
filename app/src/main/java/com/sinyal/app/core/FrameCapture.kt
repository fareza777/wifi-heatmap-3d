package com.sinyal.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Turns an ARCore camera frame into a JPEG on disk.
 *
 * ARCore hands out frames as YUV_420_888 with per-plane row strides that rarely
 * match the image width, so the planes are repacked into NV21 by hand rather
 * than copied wholesale — a straight copy produces the familiar green-and-
 * skewed result. The sensor is also mounted in landscape, so the frame is
 * rotated before saving; an un-rotated photo of a desk is hard to recognise as
 * the desk.
 */
object FrameCapture {

    private const val JPEG_QUALITY = 85
    private const val MAX_EDGE_PX = 1280

    /** Writes [image] to [target]. Returns false if the frame could not be read. */
    fun saveJpeg(image: Image, target: File, rotationDegrees: Int): Boolean = runCatching {
        val nv21 = toNv21(image)
        val raw = ByteArrayOutputStream().use { stream ->
            YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                .compressToJpeg(Rect(0, 0, image.width, image.height), JPEG_QUALITY, stream)
            stream.toByteArray()
        }

        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: return@runCatching false
        val oriented = transform(decoded, rotationDegrees)

        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        true
    }.getOrDefault(false)

    /** Interleaves the chroma planes into NV21, honouring both strides. */
    private fun toNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height * 3 / 2)

        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        var offset = 0
        for (row in 0 until height) {
            yBuffer.position(row * yPlane.rowStride)
            yBuffer.get(output, offset, width)
            offset += width
        }

        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {
            for (column in 0 until chromaWidth) {
                val uvIndex = row * uPlane.rowStride + column * uPlane.pixelStride
                // NV21 orders chroma as V then U.
                output[offset++] = vBuffer.get(row * vPlane.rowStride + column * vPlane.pixelStride)
                output[offset++] = uBuffer.get(uvIndex)
            }
        }
        return output
    }

    /** Rotates and downscales in one pass, so no oversized bitmap is ever kept. */
    private fun transform(source: Bitmap, rotationDegrees: Int): Bitmap {
        val longestEdge = maxOf(source.width, source.height)
        val scale = if (longestEdge > MAX_EDGE_PX) MAX_EDGE_PX.toFloat() / longestEdge else 1f
        if (rotationDegrees == 0 && scale == 1f) return source

        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
