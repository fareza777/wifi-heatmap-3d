package com.sinyal.app.wifi

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/**
 * The addressing side of the current connection, as the DHCP lease describes it.
 *
 * Separate from [WifiMonitor], which is about the radio: these are the numbers
 * that decide whether traffic can leave the house, and they are what someone
 * reads out to a support line.
 */
data class LinkAddressing(
    val subnetMask: String?,
    val dhcpServer: String?,
    val leaseSeconds: Int?,
    val dnsPrimary: String?,
    val dnsSecondary: String?,
)

/** Transmit and receive rates, which can differ by a lot on a marginal link. */
data class LinkRates(
    val txMbps: Int?,
    val rxMbps: Int?,
    val maxSupportedTxMbps: Int?,
    val maxSupportedRxMbps: Int?,
)

class LinkInspector(context: Context) {

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Reads the lease.
     *
     * `dhcpInfo` is deprecated in favour of `LinkProperties`, which does not
     * expose the netmask, the DHCP server, or the lease time at all — so this
     * remains the only way to answer those, and the deprecation is accepted
     * rather than worked around with a worse answer.
     */
    @Suppress("DEPRECATION")
    fun addressing(): LinkAddressing {
        val dhcp = wifi.dhcpInfo ?: return LinkAddressing(null, null, null, null, null)
        return LinkAddressing(
            subnetMask = format(dhcp.netmask),
            dhcpServer = format(dhcp.serverAddress),
            leaseSeconds = dhcp.leaseDuration.takeIf { it > 0 },
            dnsPrimary = format(dhcp.dns1),
            dnsSecondary = format(dhcp.dns2),
        )
    }

    @Suppress("DEPRECATION") // connectionInfo is the only synchronous read available.
    fun rates(): LinkRates {
        val info: WifiInfo = wifi.connectionInfo ?: return LinkRates(null, null, null, null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return LinkRates(info.linkSpeed.takeIf { it > 0 }, null, null, null)
        }

        return LinkRates(
            txMbps = info.txLinkSpeedMbps.takeIf { it > 0 },
            rxMbps = info.rxLinkSpeedMbps.takeIf { it > 0 },
            maxSupportedTxMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                info.maxSupportedTxLinkSpeedMbps.takeIf { it > 0 }
            } else {
                null
            },
            maxSupportedRxMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                info.maxSupportedRxLinkSpeedMbps.takeIf { it > 0 }
            } else {
                null
            },
        )
    }

    /** Dotted-quad from the little-endian integer DhcpInfo hands back. */
    private fun format(raw: Int): String? {
        if (raw == 0) return null
        return "%d.%d.%d.%d".format(
            raw and 0xFF,
            (raw shr 8) and 0xFF,
            (raw shr 16) and 0xFF,
            (raw shr 24) and 0xFF,
        )
    }
}
