package com.sinyal.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

/**
 * A short download, used to measure what a spot can actually carry.
 *
 * Signal strength is not speed, and the gap between them is where most real
 * complaints live: −55 dBm on a channel three neighbours are sharing can be
 * slower than −70 dBm on an empty one. A heatmap of dBm cannot show that, and
 * everyone who has moved a router on the strength of one has found out.
 *
 * Deliberately small and short. This runs while someone is walking around their
 * home, so it has to cost a fraction of a second and a fraction of a megabyte,
 * not produce a benchmark. What it gives is a comparison between rooms, not an
 * absolute figure to quote at an ISP — the speed test screen is for that.
 */
class ThroughputProbe {

    /** Megabits per second at this moment, or null when the probe could not run. */
    suspend fun measure(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            var received = 0L
            val elapsed = measureTimeMillis {
                val connection = (URL("$DOWN_URL?bytes=$PAYLOAD_BYTES").openConnection()
                    as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                }
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

            if (elapsed <= 0 || received <= 0) return@runCatching null
            (received * 8.0 / 1_000_000.0) / (elapsed / 1000.0)
        }.getOrNull()
    }

    private companion object {
        const val DOWN_URL = "https://speed.cloudflare.com/__down"

        /**
         * Half a megabyte: long enough to get past connection setup on a slow
         * link, small enough that a full walk costs a few megabytes rather than
         * someone's data allowance.
         */
        const val PAYLOAD_BYTES = 500_000

        const val BUFFER_BYTES = 32 * 1024
        const val TIMEOUT_MS = 8_000
    }
}
