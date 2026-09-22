package com.sinyal.app.net

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * One SSDP responder: the raw headers plus, when fetchable, the parsed device
 * description the LOCATION header points at.
 */
data class UpnpDevice(
    val ipAddress: String,
    val server: String?,
    val serviceType: String?,
    val location: String?,
    val usn: String?,
    val fingerprint: UpnpFingerprint?,
)

/**
 * UPnP device discovery, the SSDP way.
 *
 * One M-SEARCH multicast to 239.255.255.250:1900 asks every UPnP stack on the
 * LAN to announce itself; each answer is a unicast UDP reply to the sender
 * port, so a plain DatagramSocket suffices — no group join needed. The reply's
 * LOCATION points at a device-description XML, which is what carries the
 * friendly name and vendor the screen actually shows.
 */
class UpnpDiscovery(context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Listens for [windowMs] after broadcasting the search. Each parsed
     * response (before description fetch) is pushed through [onDevice].
     */
    suspend fun discover(
        windowMs: Long,
        onDevice: (UpnpDevice) -> Unit,
    ): List<UpnpDevice> = withContext(Dispatchers.IO) {
        val lock = wifiManager.createMulticastLock(LOCK_TAG).apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }
        try {
            val raw = collectResponses(windowMs)
            val devices = raw.distinctBy { "${it.ipAddress}|${it.usn}|${it.serviceType}" }

            // Fetch descriptions in parallel; each is a LAN round-trip of a
            // few milliseconds, and there is no reason to serialize them.
            devices.map { device ->
                async {
                    val fingerprint = device.location?.let { fetchFingerprint(it) }
                    device.copy(fingerprint = fingerprint)
                }
            }.awaitAll().also { enriched ->
                enriched.forEach(onDevice)
            }
        } finally {
            runCatching { if (lock.isHeld) lock.release() }
        }
    }

    private fun collectResponses(windowMs: Long): List<UpnpDevice> {
        val results = mutableListOf<UpnpDevice>()
        val socket = DatagramSocket().apply {
            reuseAddress = true
            soTimeout = 250
        }
        try {
            val query = QUERY.toByteArray(Charsets.UTF_8)
            val target = InetAddress.getByName(SSDP_ADDRESS)
            socket.send(DatagramPacket(query, query.size, target, SSDP_PORT))

            val deadline = System.currentTimeMillis() + windowMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                parseResponse(packet.address.hostAddress ?: continue, text)
                    ?.let(results::add)
            }
        } catch (_: Exception) {
            // No route to the multicast group — mobile data active, Wi-Fi off,
            // or an isolated AP. Empty is the honest answer.
        } finally {
            runCatching { socket.close() }
        }
        return results
    }

    /** SSDP replies are HTTP-shaped headers over UDP; parse them as such. */
    private fun parseResponse(ip: String, text: String): UpnpDevice? {
        val headers = HashMap<String, String>()
        text.lineSequence()
            .drop(1) // the "HTTP/1.1 200 OK" status line
            .forEach { line ->
                val sep = line.indexOf(':')
                if (sep > 0) {
                    headers[line.substring(0, sep).trim().uppercase()] =
                        line.substring(sep + 1).trim()
                }
            }

        val usn = headers["USN"] ?: return null
        return UpnpDevice(
            ipAddress = ip,
            server = headers["SERVER"],
            serviceType = headers["ST"],
            location = headers["LOCATION"],
            usn = usn,
            fingerprint = null,
        )
    }

    /**
     * GETs the device description and extracts the first device block.
     *
     * Streaming XML rather than a DOM: the document is read once, top to
     * bottom, and only the handful of fields a fingerprint needs are kept.
     */
    private fun fetchFingerprint(location: String): UpnpFingerprint? = runCatching {
        val connection = (URL(location).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
        }
        val body = try {
            connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                val out = StringBuilder()
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0 || out.length > MAX_DOC_BYTES) break
                    out.append(buffer, 0, read)
                }
                out.toString()
            }
        } finally {
            connection.disconnect()
        }
        parseDescription(body)
    }.getOrNull()

    private fun parseDescription(xml: String): UpnpFingerprint? {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(xml))
        }

        var friendlyName: String? = null
        var manufacturer: String? = null
        var modelName: String? = null
        var deviceType: String? = null
        var inDevice = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "device" -> inDevice = true
                    "friendlyName" -> if (inDevice && friendlyName == null) {
                        friendlyName = parser.nextText()?.trim()
                    }
                    "manufacturer" -> if (inDevice && manufacturer == null) {
                        manufacturer = parser.nextText()?.trim()
                    }
                    "modelName" -> if (inDevice && modelName == null) {
                        modelName = parser.nextText()?.trim()
                    }
                    "deviceType" -> if (inDevice && deviceType == null) {
                        deviceType = parser.nextText()?.trim()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "device" && friendlyName != null) {
                    // First <device> is the root device — the one worth showing.
                    return UpnpFingerprint(friendlyName, manufacturer, modelName, deviceType, null)
                }
            }
            event = parser.next()
        }
        return UpnpFingerprint(friendlyName, manufacturer, modelName, deviceType, null)
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val CONNECT_TIMEOUT_MS = 2500
        const val READ_TIMEOUT_MS = 2500
        const val MAX_DOC_BYTES = 96 * 1024
        const val LOCK_TAG = "sinyal-ssdp"

        val QUERY = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: ssdp:all\r\n")
            append("\r\n")
        }
    }
}
