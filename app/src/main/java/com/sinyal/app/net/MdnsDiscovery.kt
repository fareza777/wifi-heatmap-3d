package com.sinyal.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * mDNS/Bonjour browsing via the platform's NsdManager.
 *
 * The JmDNS-style approach — send PTR queries for well-known service types and
 * resolve whatever answers — maps onto NsdManager's discovery/resolve split:
 * `discoverServices` learns an instance exists, `resolveService` turns it into
 * a host and port. Service types are fixed to the dozen a home network
 * realistically carries; a wildcard `_services._dns-sd._udp` enumeration is
 * what JmDNS would do, but on Android the platform driver throttles concurrent
 * listeners and a bounded set finds the same devices more reliably.
 *
 * A multicast lock is held for the duration: Android drops inbound multicast
 * to save power without it, which is the usual reason a scan returns empty.
 */
class MdnsDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Listens for [windowMs], resolving each found service, then stops
     * everything. Results flow through [onService] as they resolve so the UI
     * fills live; the returned list is the final de-duplicated set.
     */
    suspend fun discover(
        windowMs: Long,
        onService: (DiscoveredService) -> Unit,
    ): List<DiscoveredService> = withContext(Dispatchers.IO) {
        val found = CopyOnWriteArrayList<DiscoveredService>()
        val listeners = CopyOnWriteArrayList<NsdManager.DiscoveryListener>()
        val pendingResolves = ConcurrentHashMap.newKeySet<String>()

        val lock = wifiManager.createMulticastLock(LOCK_TAG).apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }

        try {
            SERVICE_TYPES.forEach { type ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        // Resolution is serialised per instance: the platform
                        // rejects duplicate resolve calls for the same service.
                        val key = "${serviceInfo.serviceName}|${serviceInfo.serviceType}"
                        if (!pendingResolves.add(key)) return
                        runCatching {
                            nsd.resolveService(serviceInfo, resolveListener { service ->
                                collect(service, found, onService)
                            })
                        }
                    }
                }
                runCatching {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    listeners += listener
                }
            }

            delayFor(windowMs)
        } finally {
            listeners.forEach { listener ->
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
            runCatching { if (lock.isHeld) lock.release() }
        }

        dedupe(found)
    }

    private fun resolveListener(
        onResolved: (NsdServiceInfo) -> Unit,
    ): NsdManager.ResolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) = onResolved(serviceInfo)
    }

    private fun collect(
        service: NsdServiceInfo,
        found: MutableList<DiscoveredService>,
        onService: (DiscoveredService) -> Unit,
    ) {
        addressesOf(service).forEach { ip ->
            val item = DiscoveredService(
                ipAddress = ip,
                name = service.serviceName.orEmpty(),
                type = service.serviceType.orEmpty().removeSuffix("."),
                port = service.port,
                via = DiscoveredService.Via.MDNS,
            )
            found += item
            onService(item)
        }
    }

    /** API 34 returns every address; before that only one host is exposed. */
    private fun addressesOf(service: NsdServiceInfo): List<String> =
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching { service.hostAddresses }
                .getOrNull()
                ?.mapNotNull { it.hostAddress }
                .orEmpty()
        } else {
            @Suppress("DEPRECATION")
            service.host?.hostAddress?.let(::listOf).orEmpty()
        }

    private fun dedupe(services: List<DiscoveredService>): List<DiscoveredService> =
        services.distinctBy { "${it.ipAddress}|${it.name}|${it.type}|${it.port}" }

    private suspend fun delayFor(ms: Long) {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(min(500L, deadline - System.currentTimeMillis()).coerceAtLeast(50L))
        }
    }

    private companion object {
        const val LOCK_TAG = "sinyal-mdns"

        /** The service types a home LAN realistically advertises. */
        val SERVICE_TYPES = listOf(
            "_http._tcp.",       // embedded web UIs — routers, NAS, printers
            "_ipp._tcp.",        // IPP printers
            "_printer._tcp.",    // LPD printers
            "_pdl-datastream._tcp.",
            "_airplay._tcp.",
            "_raop._tcp.",
            "_googlecast._tcp.",
            "_spotify-connect._tcp.",
            "_smb._tcp.",        // Windows shares / NAS
            "_afpovertcp._tcp.", // Time Machine / Mac shares
            "_ssh._tcp.",
            "_workstation._tcp.",
            "_companion-link._tcp.", // Apple devices
            "_homekit._tcp.",
            "_hap._tcp.",
            "_mqtt._tcp.",
        )
    }
}
