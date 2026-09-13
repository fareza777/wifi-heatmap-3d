package com.sinyal.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

/** One round trip, or the absence of one. */
data class PingReply(
    val sequence: Int,
    val millis: Double?,
) {
    val lost: Boolean get() = millis == null
}

data class PingReport(
    val host: String,
    val resolvedTo: String? = null,
    val replies: List<PingReply> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
) {
    private val times: List<Double> get() = replies.mapNotNull { it.millis }

    val sent: Int get() = replies.size
    val received: Int get() = times.size
    val lossPercent: Int
        get() = if (sent == 0) 0 else ((sent - received) * 100) / sent

    val best: Double? get() = times.minOrNull()
    val worst: Double? get() = times.maxOrNull()
    val average: Double? get() = times.takeIf { it.isNotEmpty() }?.average()

    /**
     * Spread around the average.
     *
     * Reported next to the average because the two answer different questions: a
     * 30 ms average with 2 ms deviation is a good line, the same average with
     * 40 ms deviation stutters in a call even though the headline looks the same.
     */
    val deviation: Double?
        get() {
            val mean = average ?: return null
            if (times.size < 2) return null
            return sqrt(times.sumOf { (it - mean) * (it - mean) } / times.size)
        }
}

/**
 * Round-trip time to any host the user names.
 *
 * The router, a NAS, a game server, a site that feels slow — a speed test cannot
 * answer those, because it measures one fixed endpoint. Latency to the specific
 * machine someone is having trouble with is a different measurement.
 *
 * ICMP is tried first through the system `ping` binary, which is what actually
 * reports the round trip a network engineer means. Plenty of hosts and some
 * mobile networks drop ICMP entirely, so a TCP connect stands in when it does —
 * the report says which was used, since a TCP figure includes handshake time and
 * is not directly comparable.
 */
class PingTester {

    /** Emits after every reply so the chart fills as it goes. */
    suspend fun run(
        host: String,
        count: Int,
        onProgress: (PingReport) -> Unit,
    ): PingReport = withContext(Dispatchers.IO) {
        val target = host.trim()
        if (target.isEmpty()) {
            return@withContext PingReport(host = target, error = ERROR_EMPTY)
        }

        val address = runCatching { InetAddress.getByName(target) }.getOrNull()
            ?: return@withContext PingReport(host = target, error = ERROR_UNRESOLVED)

        var report = PingReport(
            host = target,
            resolvedTo = address.hostAddress,
            running = true,
        )
        onProgress(report)

        val replies = mutableListOf<PingReply>()
        for (sequence in 1..count) {
            if (!currentCoroutineContext().isActive) break

            val millis = icmp(target) ?: tcp(address)
            replies += PingReply(sequence, millis)
            report = report.copy(replies = replies.toList())
            onProgress(report)

            if (sequence < count) kotlinx.coroutines.delay(INTERVAL_MS)
        }

        report.copy(running = false).also(onProgress)
    }

    /**
     * One ICMP echo through the system binary.
     *
     * Returns null when ping is missing, blocked, or times out, which is the
     * signal to fall back rather than to report a loss.
     */
    private fun icmp(host: String): Double? = runCatching {
        val process = ProcessBuilder(
            "/system/bin/ping", "-n", "-c", "1", "-W", TIMEOUT_SECONDS.toString(), host,
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS)) {
            process.destroy()
            return null
        }
        if (process.exitValue() != 0) return null

        TIME_PATTERN.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
    }.getOrNull()

    /** Handshake time to an open port — not ICMP, but a real round trip. */
    private fun tcp(address: InetAddress): Double? = runCatching {
        var connected = false
        val nanos = measureNanoTime {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(address, TCP_PROBE_PORT),
                    TIMEOUT_SECONDS * 1000,
                )
                connected = true
            }
        }
        if (connected) nanos / 1_000_000.0 else null
    }.getOrNull()

    companion object {
        const val ERROR_EMPTY = "empty"
        const val ERROR_UNRESOLVED = "unresolved"

        /** Matches `time=12.3 ms` in ping's output, whatever the locale spells around it. */
        private val TIME_PATTERN = Regex("""time[=<]\s*([0-9.]+)""")
        private const val TIMEOUT_SECONDS = 2
        private const val INTERVAL_MS = 700L
        private const val TCP_PROBE_PORT = 80
    }
}
