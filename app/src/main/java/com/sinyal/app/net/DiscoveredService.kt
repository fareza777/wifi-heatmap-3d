package com.sinyal.app.net

/** A service a LAN device advertises, found via mDNS or UPnP. */
data class DiscoveredService(
    /** Address of the host offering the service. */
    val ipAddress: String,
    /** Instance or friendly name, e.g. "Living Room Speaker". */
    val name: String,
    /** Protocol label: "_ipp._tcp", "ssdp:rootdevice", etc. */
    val type: String,
    /** TCP/UDP port the service answers on; 0 when unknown. */
    val port: Int,
    /** Source protocol so the UI can badge it. */
    val via: Via,
) {
    enum class Via { MDNS, SSDP }
}

/**
 * What a UPnP device says about itself in its description document.
 *
 * This is the fingerprint the TCP sweep cannot get: a router announces its
 * brand and model here, which turns a bare gateway address into "it is the
 * TP-Link in the hallway".
 */
data class UpnpFingerprint(
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val deviceType: String?,
    val serverHeader: String?,
) {
    /** Best single-line identification, most descriptive first. */
    val label: String?
        get() = listOfNotNull(friendlyName, modelName, manufacturer)
            .firstOrNull { it.isNotBlank() }
}
