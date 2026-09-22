package com.sinyal.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureNanoTime

/** Result of one benchmark run against a peer phone. */
data class LanBenchResult(
    /** Median round trip while the link is otherwise idle. */
    val latencyMs: Double,
    /** Mean gap between consecutive idle round trips. */
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    /** Median round trip measured while the download was saturating the link. */
    val loadedLatencyMs: Double?,
    val loadedLatencyMaxMs: Double?,
)

enum class LanBenchPhase {
    IDLE,
    LATENCY,
    DOWNLOAD,
    UPLOAD,
    DONE,
}

/**
 * iperf-style benchmark between two phones on the same Wi-Fi.
 *
 * The protocol is three line-based commands over one TCP connection, kept
 * deliberately small because the wire is not the product — the numbers are:
 *
 *  - `PING`        → peer answers `PONG`, RTT is the latency sample.
 *  - `DOWN <n>`    → peer streams n payload bytes (measures *this* side's
 *                    receive speed).
 *  - `UP <n>`      → this side streams n bytes after the command; the peer
 *                    drains them and replies `DONE`.
 *
 * Throughput is bytes moved over wall time, and a second connection samples
 * PING latency while the transfer runs — that is the loaded-latency figure a
 * raw download number never shows.
 *
 * Only java.net sockets are used, so the whole thing runs in a plain JVM unit
 * test over loopback — no Wi-Fi required to prove the protocol.
 */
class LanBenchServer {

    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Socket>()

    val port: Int get() = serverSocket?.localPort ?: -1
    val isRunning: Boolean get() = serverSocket?.isClosed == false

    /**
     * Binds [DEFAULT_PORT] when it is free so a manual peer can guess it, and
     * falls back to an ephemeral port when it is taken. [port] reports what
     * was actually bound.
     */
    fun start() {
        if (isRunning) return
        serverSocket = try {
            ServerSocket(DEFAULT_PORT)
        } catch (e: java.io.IOException) {
            ServerSocket(0)
        }
    }

    /**
     * Accepts connections until [stop]. Blocking by design — run it on
     * Dispatchers.IO; closing the socket is what ends the loop.
     */
    fun acceptLoop() {
        val server = serverSocket ?: return
        while (isRunning) {
            val client = try {
                server.accept()
            } catch (e: java.net.SocketException) {
                break
            } catch (e: java.io.IOException) {
                break
            }
            clients += client
            Thread({ serveClient(client) }, "sinyal-bench-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        serverSocket = null
    }

    private fun serveClient(socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            val payload = ByteArray(CHUNK_BYTES)

            while (isRunning && !socket.isClosed) {
                val command = readCommand(input) ?: break
                when {
                    command == "PING" -> output.writeBytes("PONG\n")
                    command.startsWith("DOWN ") -> {
                        val bytes = command.removePrefix("DOWN ").toLongOrNull() ?: break
                        var remaining = bytes
                        while (remaining > 0) {
                            val n = min(remaining.toInt(), payload.size)
                            output.write(payload, 0, n)
                            remaining -= n
                        }
                        output.flush()
                    }
                    command.startsWith("UP ") -> {
                        val bytes = command.removePrefix("UP ").toLongOrNull() ?: break
                        var remaining = bytes
                        while (remaining > 0) {
                            val n = input.read(payload, 0, min(remaining.toInt(), payload.size))
                            if (n < 0) return
                            remaining -= n
                        }
                        output.writeBytes("DONE\n")
                        output.flush()
                    }
                    else -> break
                }
                output.flush()
            }
        } catch (e: java.io.IOException) {
            // Peer hung up mid-command — normal when a run is cancelled.
        } finally {
            clients -= socket
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024

        /** Preferred port for hosts; what the UI suggests for manual entry. */
        const val DEFAULT_PORT = 5_309
    }
}

/**
 * Commands are ASCII lines ending in `\n`, short enough to parse a byte at a
 * time — mixing a buffered line reader with raw payload reads on the same
 * stream would eat payload bytes into the line buffer.
 */
internal fun readCommand(input: DataInputStream): String? {
    val line = StringBuilder()
    while (true) {
        val b = try {
            input.read()
        } catch (e: java.io.IOException) {
            return null
        }
        if (b < 0) return null
        if (b == '\n'.code) break
        line.append(b.toChar())
    }
    return line.toString().trim()
}

class LanBenchClient {

    /** One round trip on a fresh or reused socket, or null when unreachable. */
    fun pingOnce(input: DataInputStream, output: DataOutputStream): Double? {
        return try {
            val nanos = measureNanoTime {
                output.writeBytes("PING\n")
                output.flush()
                val reply = readCommand(input)
                if (reply != "PONG") return null
            }
            nanos / 1_000_000.0
        } catch (e: java.io.IOException) {
            null
        }
    }

    /** Connects and runs [samples] round trips on the one connection. */
    suspend fun ping(host: String, port: Int, samples: Int = PING_SAMPLES): List<Double> =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = IO_TIMEOUT_MS
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    (0 until samples).mapNotNull { pingOnce(input, output) }
                }
            }.getOrDefault(emptyList())
        }

    /**
     * Side-channel RTT samples on a caller-owned socket.
     *
     * The caller keeps the socket and closes it when the transfer is over:
     * cancelling a coroutine does not interrupt a blocking read, but a closed
     * socket ends it at once — otherwise `coroutineScope` would sit on the
     * finished sampler until the socket timeout expired.
     */
    private suspend fun loadedPings(
        socket: Socket,
        bucket: CopyOnWriteArrayList<Double>,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            while (!socket.isClosed) {
                val rtt = pingOnce(input, output) ?: break
                bucket += rtt
                kotlinx.coroutines.delay(LOADED_PING_MS)
            }
        }
    }

    /**
     * Streams [bytes] from the peer and returns the observed rate.
     *
     * A second connection pings through the same transfer: whatever the
     * measurement numbers say, the loaded latency is what a game or a call
     * would feel while a download runs.
     */
    private suspend fun download(
        host: String,
        port: Int,
        bytes: Long,
        onLive: (Double) -> Unit,
    ): Pair<Double, List<Double>> = coroutineScope {
        val loaded = CopyOnWriteArrayList<Double>()
        val side = runCatching {
            Socket().apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                soTimeout = IO_TIMEOUT_MS
            }
        }.getOrNull()
        val sampler = side?.let { launch { loadedPings(it, loaded) } }
        val mbps = async(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = IO_TIMEOUT_MS * 4
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                output.writeBytes("DOWN $bytes\n")
                output.flush()

                val buffer = ByteArray(CHUNK_BYTES)
                var received = 0L
                var lastTick = 0L
                val start = System.nanoTime()
                while (received < bytes) {
                    val n = input.read(buffer, 0, min((bytes - received).toInt(), buffer.size))
                    if (n < 0) break
                    received += n
                    val now = System.nanoTime()
                    if (now - lastTick > 200_000_000L) {
                        lastTick = now
                        onLive(mbps(received, now - start))
                    }
                }
                mbps(received, System.nanoTime() - start)
            }
        }.await()
        runCatching { side?.close() }
        sampler?.join()
        mbps to loaded.toList()
    }

    /** Pushes [bytes] to the peer and waits for its `DONE`. */
    private suspend fun upload(
        host: String,
        port: Int,
        bytes: Long,
        onLive: (Double) -> Unit,
    ): Double = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = IO_TIMEOUT_MS * 4
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            output.writeBytes("UP $bytes\n")
            output.flush()

            val payload = ByteArray(CHUNK_BYTES)
            var sent = 0L
            var lastTick = 0L
            val start = System.nanoTime()
            while (sent < bytes) {
                val n = min((bytes - sent).toInt(), payload.size)
                output.write(payload, 0, n)
                sent += n
                val now = System.nanoTime()
                if (now - lastTick > 200_000_000L) {
                    lastTick = now
                    onLive(mbps(sent, now - start))
                }
            }
            output.flush()
            // DONE only arrives once the peer has actually drained the payload,
            // so its wait belongs inside the measured time.
            val ack = readCommand(input)
            val total = System.nanoTime() - start
            if (ack != "DONE") mbps(sent, total) else mbps(sent, total)
        }
    }

    /**
     * Full bench: idle latency, download (with loaded pings), upload.
     *
     * Payload size adapts like the internet speed test does: a 1 MB probe
     * estimates the rate, then the real transfer is sized to about four
     * seconds — a fast LAN neither finishes inside the setup time nor crawls
     * for a fixed gigabyte on a weak one.
     */
    suspend fun bench(
        host: String,
        port: Int,
        onPhase: (LanBenchPhase) -> Unit,
        onLive: (Double) -> Unit = {},
    ): LanBenchResult? = withContext(Dispatchers.IO) {
        runCatching {
            onPhase(LanBenchPhase.LATENCY)
            val samples = ping(host, port)
            if (samples.isEmpty()) return@withContext null
            val idleMedian = samples.median()
            val jitter = samples.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average()

            onPhase(LanBenchPhase.DOWNLOAD)
            val probeDown = download(host, port, PROBE_BYTES, onLive).first
            val downBytes = sizedBytes(probeDown)
            val (downMbps, loaded) = download(host, port, downBytes, onLive)

            onPhase(LanBenchPhase.UPLOAD)
            val probeUp = upload(host, port, PROBE_BYTES, onLive)
            val upBytes = sizedBytes(probeUp)
            val upMbps = upload(host, port, upBytes, onLive)

            onPhase(LanBenchPhase.DONE)
            LanBenchResult(
                latencyMs = idleMedian,
                jitterMs = if (jitter.isNaN()) 0.0 else jitter,
                downloadMbps = max(probeDown, downMbps),
                uploadMbps = max(probeUp, upMbps),
                loadedLatencyMs = loaded.takeIf { it.isNotEmpty() }?.median(),
                loadedLatencyMaxMs = loaded.maxOrNull(),
            )
        }.getOrNull()
    }

    /** Picks a payload that should occupy roughly [TARGET_MS] at [mbps]. */
    internal fun sizedBytes(mbps: Double): Long {
        val bytes = (mbps / 8.0) * 1_000_000.0 * (TARGET_MS / 1000.0)
        return bytes.toLong().coerceIn(MIN_BYTES, MAX_BYTES)
    }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0) 0.0 else (bytes * 8.0) / (nanos / 1_000_000_000.0) / 1_000_000.0

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        return sorted()[size / 2]
    }

    companion object {
        /** First transfer; large enough to time meaningfully, small enough to be fast. */
        const val PROBE_BYTES = 1_000_000L
        const val MIN_BYTES = 4_000_000L
        const val MAX_BYTES = 96_000_000L
        const val TARGET_MS = 4_000L
        const val PING_SAMPLES = 10
        const val LOADED_PING_MS = 220L
        const val CONNECT_TIMEOUT_MS = 4_000
        const val IO_TIMEOUT_MS = 10_000
        const val CHUNK_BYTES = 64 * 1024

        /** mDNS service type the host side advertises; what a client looks for. */
        const val SERVICE_TYPE = "_sinyalbench._tcp."
    }
}
