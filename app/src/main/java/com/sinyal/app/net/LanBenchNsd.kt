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

/** One advertised bench peer: where to point the client at. */
data class BenchHost(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * Publishes this phone's bench server on the LAN so the other phone can find
 * it without typing an address.
 *
 * Same NsdManager machinery as [MdnsDiscovery], pointed the other way: an
 * instance of `_sinyalbench._tcp` is registered for the lifetime of the
 * server, and withdrawn again on [stop].
 */
class LanBenchAdvertiser(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    private var listener: NsdManager.RegistrationListener? = null
    @Volatile var advertised = false
        private set

    fun advertise(port: Int, name: String) {
        if (listener != null) return
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = LanBenchClient.SERVICE_TYPE
            setPort(port)
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                advertised = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                advertised = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                advertised = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = registration
        runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        }
    }

    fun stop() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
        advertised = false
    }
}

/** Finds bench peers: the other half of [LanBenchAdvertiser]. */
class LanBenchSeeker(context: Context) {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Listens for [windowMs], resolving every `_sinyalbench._tcp` instance it
     * hears. A multicast lock is held the whole time — without it the radio
     * drops inbound multicast and discovery silently finds nothing.
     */
    suspend fun discover(
        windowMs: Long,
        onHost: (BenchHost) -> Unit,
    ): List<BenchHost> = withContext(Dispatchers.IO) {
        val found = CopyOnWriteArrayList<BenchHost>()
        val pendingResolves = ConcurrentHashMap.newKeySet<String>()

        val lock = wifiManager.createMulticastLock(LOCK_TAG).apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val key = "${serviceInfo.serviceName}|${serviceInfo.serviceType}"
                if (!pendingResolves.add(key)) return
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int,
                        ) = Unit

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            addressesOf(serviceInfo).forEach { ip ->
                                val host = BenchHost(
                                    name = serviceInfo.serviceName.orEmpty(),
                                    host = ip,
                                    port = serviceInfo.port,
                                )
                                found += host
                                onHost(host)
                            }
                        }
                    })
                }
            }
        }

        try {
            runCatching {
                nsd.discoverServices(
                    LanBenchClient.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
            }
            val deadline = System.currentTimeMillis() + windowMs
            while (System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(min(400L, deadline - System.currentTimeMillis()))
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { if (lock.isHeld) lock.release() }
        }

        found.distinctBy { "${it.host}|${it.port}" }
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

    private companion object {
        const val LOCK_TAG = "sinyal-bench-mdns"
    }
}
