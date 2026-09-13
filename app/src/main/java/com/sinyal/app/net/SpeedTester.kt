package com.sinyal.app.net

import androidx.annotation.StringRes
import com.sinyal.app.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

/** Which part of the test is running, so the UI can narrate it. */
enum class SpeedPhase(@StringRes val label: Int) {
    IDLE(R.string.speed_idle),
    LATENCY(R.string.speed_latency),
    DOWNLOAD(R.string.speed_download),
    UPLOAD(R.string.speed_upload),
    DONE(R.string.speed_done),
    FAILED(R.string.speed_failed),
}

data class SpeedResult(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val latencyMs: Int? = null,
    val jitterMs: Int? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    /** Live figure for the phase in progress, so the gauge has something to move. */
    val liveMbps: Double = 0.0,
    /**
     * Why the test stopped, when it did.
     *
     * A resource id rather than a message, because the only text worth showing a
     * user here is our own: the exception's own message is a Java class name and
     * a host name, which explains nothing and cannot be translated.
     */
    @StringRes val error: Int? = null,
)

/**
 * Throughput measured against Cloudflare's public speed endpoints.
 *
 * Those endpoints exist for exactly this, are anycast so the nearest edge
 * answers, and need no key — which matters because a speed test pointed at a
 * single distant server measures the distance to that server more than it
 * measures the connection.
 *
 * What this reports is internet throughput through the Wi-Fi link, not the Wi-Fi
 * link's own capacity. On a fast router with a slow subscription the two differ
 * enormously, and the UI says so rather than letting the number be misread.
 */
class SpeedTester {

    /**
     * Runs latency, download and upload in turn, emitting progress through
     * [onProgress]. Cancellation is honoured between and during transfers.
     */
    suspend fun run(onProgress: (SpeedResult) -> Unit): SpeedResult =
        withContext(Dispatchers.IO) {
            var result = SpeedResult(phase = SpeedPhase.LATENCY)
            onProgress(result)

            runCatching {
                val samples = measureLatency()
                result = result.copy(
                    latencyMs = samples.median(),
                    jitterMs = samples.jitter(),
                    phase = SpeedPhase.DOWNLOAD,
                )
                onProgress(result)

                val down = measureDownload { live ->
                    onProgress(result.copy(liveMbps = live))
                }
                result = result.copy(
                    downloadMbps = down,
                    liveMbps = 0.0,
                    phase = SpeedPhase.UPLOAD,
                )
                onProgress(result)

                val up = measureUpload { live ->
                    onProgress(result.copy(liveMbps = live))
                }
                result = result.copy(
                    uploadMbps = up,
                    liveMbps = 0.0,
                    phase = SpeedPhase.DONE,
                )
            }.onFailure {
                result = result.copy(
                    phase = SpeedPhase.FAILED,
                    error = R.string.speed_error_generic,
                )
            }

            onProgress(result)
            result
        }

    /** Several round trips, because one is indistinguishable from a fluke. */
    private suspend fun measureLatency(): List<Int> {
        val samples = mutableListOf<Int>()
        repeat(LATENCY_SAMPLES) {
            if (!currentCoroutineContext().isActive) return samples
            val elapsed = measureTimeMillis {
                openConnection("$DOWN_URL?bytes=0").apply {
                    requestMethod = "GET"
                    inputStream.use { it.readBytes() }
                    disconnect()
                }
            }
            samples += elapsed.toInt()
        }
        return samples
    }

    /**
     * Downloads in growing chunks.
     *
     * A fixed payload either finishes instantly on fibre — measuring connection
     * setup rather than bandwidth — or takes minutes on a weak link. Starting
     * small and growing until enough time has elapsed adapts to both.
     */
    private suspend fun measureDownload(onLive: (Double) -> Unit): Double {
        var bytesTotal = 0L
        var elapsedTotal = 0L
        var chunk = FIRST_CHUNK_BYTES

        while (elapsedTotal < TARGET_MILLIS && currentCoroutineContext().isActive) {
            var received = 0L
            val elapsed = measureTimeMillis {
                val connection = openConnection("$DOWN_URL?bytes=$chunk")
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        received += read
                    }
                }
                connection.disconnect()
            }

            bytesTotal += received
            elapsedTotal += elapsed
            onLive(megabitsPerSecond(bytesTotal, elapsedTotal))
            chunk = (chunk * 2).coerceAtMost(MAX_CHUNK_BYTES)
        }

        return megabitsPerSecond(bytesTotal, elapsedTotal)
    }

    private suspend fun measureUpload(onLive: (Double) -> Unit): Double {
        val payload = ByteArray(UPLOAD_CHUNK_BYTES) { (it and 0xFF).toByte() }
        var bytesTotal = 0L
        var elapsedTotal = 0L

        while (elapsedTotal < TARGET_MILLIS && currentCoroutineContext().isActive) {
            val elapsed = measureTimeMillis {
                val connection = openConnection(UP_URL).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setFixedLengthStreamingMode(payload.size)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                connection.outputStream.use { out: OutputStream ->
                    out.write(payload)
                    out.flush()
                }
                connection.inputStream.use { it.readBytes() }
                connection.disconnect()
            }

            bytesTotal += payload.size
            elapsedTotal += elapsed
            onLive(megabitsPerSecond(bytesTotal, elapsedTotal))
        }

        return megabitsPerSecond(bytesTotal, elapsedTotal)
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun megabitsPerSecond(bytes: Long, millis: Long): Double =
        if (millis <= 0) 0.0 else (bytes * 8.0 / 1_000_000.0) / (millis / 1000.0)

    private companion object {
        const val DOWN_URL = "https://speed.cloudflare.com/__down"
        const val UP_URL = "https://speed.cloudflare.com/__up"

        const val LATENCY_SAMPLES = 6
        const val FIRST_CHUNK_BYTES = 1_000_000L
        const val MAX_CHUNK_BYTES = 25_000_000L
        const val UPLOAD_CHUNK_BYTES = 1_000_000
        const val BUFFER_BYTES = 32 * 1024

        /** Each direction runs until it has had this long to settle. */
        const val TARGET_MILLIS = 6_000L
        const val TIMEOUT_MS = 15_000
    }
}

/** Middle sample, which a single outlier cannot drag around. */
private fun List<Int>.median(): Int? {
    if (isEmpty()) return null
    val sorted = sorted()
    return sorted[sorted.size / 2]
}

/** Mean absolute difference between consecutive round trips. */
private fun List<Int>.jitter(): Int? {
    if (size < 2) return null
    val deltas = zipWithNext { a, b -> kotlin.math.abs(b - a) }
    return deltas.average().toInt()
}
