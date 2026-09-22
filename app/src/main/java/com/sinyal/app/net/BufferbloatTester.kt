package com.sinyal.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.system.measureNanoTime

/** One round-trip time, tagged with whether the link was under load then. */
data class LatencySample(
    val ms: Double,
    val loaded: Boolean,
)

/** How much worse latency gets when the pipe is full — the letter people know. */
enum class BloatGrade {
    A, B, C, D, F,
}

enum class BufferbloatPhase {
    IDLE,
    LOAD,
    DONE,
}

data class BufferbloatReport(
    val idleMedianMs: Double,
    val idleMinMs: Double,
    val loadedMedianMs: Double,
    val loadedP95Ms: Double,
    val loadedMaxMs: Double,
    /** loadedMedian − idleMedian: the number that names the problem. */
    val increaseMs: Double,
    val grade: BloatGrade,
    val samples: List<LatencySample>,
)

/**
 * The loaded-latency check waveform's bufferbloat test made famous, against
 * Cloudflare's edge rather than a dedicated rig.
 *
 * Two halves, same probe: TCP connect time to `speed.cloudflare.com:443`,
 * which is the closest thing to an internet round trip that costs no TLS
 * handshake. First a handful of samples while the link is quiet, then
 * continuous samples while two HTTP downloads fill the pipe — the delta
 * between the two medians is the bufferbloat the user feels in a game or a
 * call, not the speed-test headline.
 *
 * The transport functions are plain sockets and HTTP, so grading and the
 * probe itself are unit-testable on a JVM over loopback.
 */
object BufferbloatTester {

    /** TCP connect RTT — the cheapest internet round trip there is. */
    fun tcpRttMs(host: String, port: Int, timeoutMs: Int = 4_000): Double? {
        return try {
            var socket: Socket? = null
            val nanos = measureNanoTime {
                socket = Socket()
                socket!!.connect(InetSocketAddress(host, port), timeoutMs)
            }
            socket!!.close()
            nanos / 1_000_000.0
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Idle samples, then loaded samples while [LOAD_STREAMS] parallel
     * downloads run for [LOAD_WINDOW_MS]. [onProgress] gets every sample as it
     * lands so the UI can draw the split live rather than at the end.
     */
    suspend fun measure(
        edgeHost: String = EDGE_HOST,
        onPhase: (BufferbloatPhase) -> Unit = {},
        onSample: (LatencySample) -> Unit = {},
    ): BufferbloatReport? = withContext(Dispatchers.IO) {
        onPhase(BufferbloatPhase.IDLE)
        val idle = mutableListOf<Double>()
        repeat(IDLE_SAMPLES) {
            val rtt = tcpRttMs(edgeHost, EDGE_PORT) ?: return@withContext null
            idle += rtt
            onSample(LatencySample(rtt, loaded = false))
            kotlinx.coroutines.delay(IDLE_GAP_MS)
        }

        onPhase(BufferbloatPhase.LOAD)
        val loaded = CopyOnWriteArrayList<Double>()
        // The loaders are deadline-bounded rather than cancelled: a coroutine
        // cancel cannot interrupt a blocking stream read, so the deadline is
        // what each in-flight chunk checks between reads.
        val deadline = System.currentTimeMillis() + LOAD_WINDOW_MS
        coroutineScope {
            val sampler = launch {
                while (isActive) {
                    // Short timeout so cancel() isn't held up by a stuck probe.
                    val rtt = tcpRttMs(edgeHost, EDGE_PORT, timeoutMs = 1_500)
                    if (rtt != null) {
                        loaded += rtt
                        onSample(LatencySample(rtt, loaded = true))
                    }
                    kotlinx.coroutines.delay(LOADED_SAMPLE_MS)
                }
            }
            val loaders = (0 until LOAD_STREAMS).map {
                launch {
                    while (System.currentTimeMillis() < deadline) {
                        runCatching { downloadChunk(LOAD_CHUNK_BYTES, deadline) }
                    }
                }
            }
            loaders.forEach { it.join() }
            sampler.cancel()
        }

        onPhase(BufferbloatPhase.DONE)
        buildReport(idle, loaded)
    }

    /**
     * Turns the two sample sets into the report.
     *
     * Grading follows the shape waveform's test popularised: what matters is
     * how much latency a full pipe *adds*, not the raw loaded figure — a
     * 30 ms idle baseline with 60 ms loaded is a different problem than a
     * 30 ms idle with 800 ms loaded.
     */
    fun buildReport(idle: List<Double>, loaded: List<Double>): BufferbloatReport? {
        if (idle.isEmpty() || loaded.isEmpty()) return null

        val idleMedian = idle.sorted()[idle.size / 2]
        val loadedSorted = loaded.sorted()
        val loadedMedian = loadedSorted[loadedSorted.size / 2]
        val p95 = loadedSorted[((loadedSorted.size - 1) * 95 / 100).coerceAtLeast(0)]
        val increase = loadedMedian - idleMedian

        return BufferbloatReport(
            idleMedianMs = idleMedian,
            idleMinMs = idle.min(),
            loadedMedianMs = loadedMedian,
            loadedP95Ms = p95,
            loadedMaxMs = loadedSorted.last(),
            increaseMs = increase,
            grade = gradeFor(increase),
            samples = idle.map { LatencySample(it, false) } + loaded.map { LatencySample(it, true) },
        )
    }

    /** Median latency added under load, mapped to the letter grade. */
    fun gradeFor(increaseMs: Double): BloatGrade = when {
        increaseMs < 15.0 -> BloatGrade.A
        increaseMs < 50.0 -> BloatGrade.B
        increaseMs < 150.0 -> BloatGrade.C
        increaseMs < 400.0 -> BloatGrade.D
        else -> BloatGrade.F
    }

    /**
     * One GET of [bytes]; throughput is irrelevant — the load is the point.
     * Stops at [deadlineMs] so the load phase ends on time even mid-chunk.
     */
    private fun downloadChunk(bytes: Long, deadlineMs: Long) {
        val connection = (URL("$LOAD_URL?bytes=$bytes").openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = IO_TIMEOUT_MS
                readTimeout = IO_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
        try {
            connection.inputStream.use { stream ->
                val buffer = ByteArray(32 * 1024)
                while (System.currentTimeMillis() < deadlineMs && stream.read(buffer) > 0) Unit
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Human-scale framing of the increase, e.g. "+250 ms". */
    fun formatIncrease(ms: Double): String =
        if (ms < 0) "%.0f ms".format(ms) else "+%.0f ms".format(ms)

    /** Round-trip ms without sub-millisecond noise. */
    fun formatMs(ms: Double): String = "${ms.roundToInt()} ms"

    private const val EDGE_HOST = "speed.cloudflare.com"
    private const val EDGE_PORT = 443
    private const val LOAD_URL = "https://speed.cloudflare.com/__down"

    private const val IDLE_SAMPLES = 8
    private const val IDLE_GAP_MS = 200L
    private const val LOAD_STREAMS = 2
    private const val LOAD_CHUNK_BYTES = 12_000_000L
    private const val LOAD_WINDOW_MS = 8_000L
    private const val LOADED_SAMPLE_MS = 220L
    private const val IO_TIMEOUT_MS = 10_000
}
