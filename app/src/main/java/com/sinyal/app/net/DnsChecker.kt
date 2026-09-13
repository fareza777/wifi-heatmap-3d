package com.sinyal.app.net

import androidx.annotation.StringRes
import com.sinyal.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.system.measureTimeMillis

/** One name resolution attempt. */
data class DnsProbe(
    val hostName: String,
    val resolvedTo: String?,
    val millis: Int,
) {
    val succeeded: Boolean get() = resolvedTo != null
}

data class DnsReport(
    val probes: List<DnsProbe>,
    val servers: List<String>,
) {
    val allResolved: Boolean get() = probes.isNotEmpty() && probes.all { it.succeeded }
    val averageMs: Int?
        get() = probes.filter { it.succeeded }
            .map { it.millis }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()

    /** Plain reading of what the numbers mean for browsing. */
    @get:StringRes
    val verdict: Int
        get() = when {
            probes.isEmpty() -> R.string.dns_untested
            !allResolved -> R.string.dns_partial_failure
            (averageMs ?: 0) > SLOW_MS -> R.string.dns_slow
            else -> R.string.dns_ok
        }

    private companion object {
        /** Above this, a new site visibly hesitates before it starts loading. */
        const val SLOW_MS = 300
    }
}

/**
 * Checks that name resolution actually works.
 *
 * A connection can pass every signal test and still be useless if DNS is broken:
 * pages simply fail to open while the Wi-Fi icon shows full bars. This is the
 * one failure that looks exactly like a signal problem but is not.
 */
class DnsChecker(private val scanner: LanScanner) {

    suspend fun run(): DnsReport = withContext(Dispatchers.IO) {
        val probes = HOSTS.map { host ->
            var address: String? = null
            val elapsed = measureTimeMillis {
                address = runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
            }
            DnsProbe(host, address, elapsed.toInt())
        }
        DnsReport(probes = probes, servers = scanner.readDetails().dnsServers)
    }

    private companion object {
        /** Spread across operators, so one provider being down is not read as DNS failure. */
        val HOSTS = listOf("google.com", "cloudflare.com", "wikipedia.org")
    }
}
