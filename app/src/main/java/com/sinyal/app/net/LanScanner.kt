package com.sinyal.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** One machine answering on the local network. */
data class LanDevice(
    val ipAddress: String,
    val hostName: String?,
    val isThisPhone: Boolean,
    val isGateway: Boolean,
    val responseMs: Int,
) {
    /**
     * Best name available, falling back to the address.
     *
     * Takes a resolver rather than reading resources itself: a reverse lookup
     * usually fails on a home LAN, so most rows end up as a bare IP and only the
     * two special cases need translating.
     */
    fun displayName(thisPhone: String, router: String): String = when {
        isThisPhone -> thisPhone
        isGateway -> hostName ?: router
        else -> hostName ?: ipAddress
    }
}

/** Everything the platform will tell us about the current connection. */
data class ConnectionDetails(
    val ipAddress: String?,
    val gateway: String?,
    val prefixLength: Int?,
    val dnsServers: List<String>,
    val domain: String?,
    val interfaceName: String?,
) {
    /**
     * Dotted-quad form of the prefix.
     *
     * Derived rather than read: `DhcpInfo.netmask` is deprecated and comes back
     * as zero on current Android, while the prefix length is always populated
     * and says exactly the same thing.
     */
    val subnetMask: String?
        get() = prefixLength?.takeIf { it in 0..32 }?.let { prefix ->
            val bits = if (prefix == 0) 0 else -1 shl (32 - prefix)
            "%d.%d.%d.%d".format(
                (bits ushr 24) and 0xFF,
                (bits ushr 16) and 0xFF,
                (bits ushr 8) and 0xFF,
                bits and 0xFF,
            )
        }

    /** Usable host count implied by the prefix, e.g. /24 gives 254. */
    val hostCount: Int?
        get() = prefixLength?.let { prefix ->
            if (prefix in 1..30) (1 shl (32 - prefix)) - 2 else null
        }

    companion object {
        val Empty = ConnectionDetails(null, null, null, emptyList(), null, null)
    }
}

/**
 * Finds the other devices sharing this Wi-Fi.
 *
 * Android stopped exposing the ARP table around API 29, so there is no list to
 * read — the subnet has to be probed. Probing is done with short TCP connects
 * rather than ICMP echo, because `isReachable` silently falls back to TCP
 * without root anyway and reports far more false negatives.
 *
 * A refused connection is a positive result: something answered to refuse it.
 * Only silence within the timeout counts as an empty address.
 */
class LanScanner(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun readDetails(): ConnectionDetails {
        val network = manager.activeNetwork ?: return ConnectionDetails.Empty
        val properties: LinkProperties = manager.getLinkProperties(network)
            ?: return ConnectionDetails.Empty

        val ipv4: LinkAddress? = properties.linkAddresses
            .firstOrNull { it.address is Inet4Address }

        return ConnectionDetails(
            ipAddress = ipv4?.address?.hostAddress,
            gateway = properties.routes
                .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway
                ?.hostAddress,
            prefixLength = ipv4?.prefixLength,
            dnsServers = properties.dnsServers.mapNotNull { it.hostAddress },
            domain = properties.domains,
            interfaceName = properties.interfaceName,
        )
    }

    /**
     * Sweeps the /24 around this phone. Emits each find through [onFound] so the
     * list fills as it goes rather than appearing all at once at the end.
     */
    suspend fun scan(onFound: (LanDevice) -> Unit): List<LanDevice> = coroutineScope {
        val details = withContext(Dispatchers.IO) { readDetails() }
        val localIp = details.ipAddress ?: return@coroutineScope emptyList()
        val prefix = localIp.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return@coroutineScope emptyList()

        val found = mutableListOf<LanDevice>()

        (1..254).chunked(CONCURRENCY).forEach { batch ->
            val results = batch.map { host ->
                async(Dispatchers.IO) { probe("$prefix.$host", localIp, details.gateway) }
            }.awaitAll()

            results.filterNotNull().forEach { device ->
                found += device
                onFound(device)
            }
        }
        found
    }

    private fun probe(ip: String, localIp: String, gateway: String?): LanDevice? {
        val start = System.currentTimeMillis()

        if (ip == localIp) {
            return LanDevice(ip, "localhost", isThisPhone = true, isGateway = false, responseMs = 0)
        }

        val alive = PROBE_PORTS.any { port -> touches(ip, port) } || pings(ip)
        if (!alive) return null

        val elapsed = (System.currentTimeMillis() - start).toInt()
        return LanDevice(
            ipAddress = ip,
            hostName = resolveName(ip),
            isThisPhone = false,
            isGateway = ip == gateway,
            responseMs = elapsed,
        )
    }

    /** A refusal proves something is there; only a timeout means nothing is. */
    private fun touches(ip: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), PROBE_TIMEOUT_MS)
            true
        }
    } catch (_: java.net.ConnectException) {
        true
    } catch (_: Exception) {
        false
    }

    private fun pings(ip: String): Boolean = runCatching {
        InetAddress.getByName(ip).isReachable(PROBE_TIMEOUT_MS)
    }.getOrDefault(false)

    /** Reverse lookup often fails on a home LAN; the IP stands in when it does. */
    private fun resolveName(ip: String): String? = runCatching {
        val name = InetAddress.getByName(ip).canonicalHostName
        if (name == ip) null else name
    }.getOrNull()

    private companion object {
        /** Ports common enough that most devices answer or refuse on one of them. */
        val PROBE_PORTS = listOf(80, 443, 22, 445, 8080, 7)
        const val PROBE_TIMEOUT_MS = 220
        const val CONCURRENCY = 32
    }
}
