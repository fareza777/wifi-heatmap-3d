package com.sinyal.app.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Maps a MAC prefix to the company that registered it.
 *
 * The full IEEE registry is 3.8 MB of CSV; stripped to just the prefix and a
 * short name it is 830 KB, and the APK's own deflate takes that to under 400 KB
 * — worth carrying to name every device on a home network without a round trip
 * to a server.
 *
 * Shipped as plain text rather than gzip on purpose: the build gunzips a `.gz`
 * asset while packaging, so the file inside the APK would not be the file the
 * code opened. The zip compresses it to the same size either way.
 *
 * Loaded once, lazily — nothing pays for it until something asks.
 */
class OuiLookup(private val context: Context) {

    @Volatile
    private var table: Map<String, String>? = null

    suspend fun vendorOf(macAddress: String?): String? {
        val prefix = normalisePrefix(macAddress) ?: return null
        return load()[prefix]
    }

    /** Warms the table so the first row of a list does not wait on file IO. */
    suspend fun preload() {
        load()
    }

    private suspend fun load(): Map<String, String> {
        table?.let { return it }
        return withContext(Dispatchers.IO) {
            table ?: runCatching {
                val parsed = HashMap<String, String>(45_000)
                context.applicationContext.assets.open(ASSET).use { raw ->
                    raw.bufferedReader().forEachLine { line ->
                        val tab = line.indexOf('\t')
                        if (tab == PREFIX_LENGTH) {
                            parsed[line.substring(0, tab)] = line.substring(tab + 1)
                        }
                    }
                }
                parsed
            }.onFailure {
                // Not fatal — every row just loses its vendor name. Logged rather
                // than swallowed, because a silent empty table looks identical to
                // a database that simply has no match for this router.
                Log.w(TAG, "Vendor table failed to load", it)
            }.getOrDefault(emptyMap()).also { table = it }
        }
    }

    /**
     * Locally administered addresses carry no vendor at all.
     *
     * Android randomises the MAC it presents to each network by default since
     * Android 10, and a randomised address sets the second-least-significant bit
     * of the first octet. Looking one up would return whichever company happens
     * to own that prefix — a confident, wrong answer.
     */
    private fun normalisePrefix(macAddress: String?): String? {
        val hex = macAddress?.replace(":", "")?.replace("-", "")?.uppercase() ?: return null
        if (hex.length < PREFIX_LENGTH) return null

        val firstOctet = hex.substring(0, 2).toIntOrNull(16) ?: return null
        if (firstOctet and LOCALLY_ADMINISTERED != 0) return null

        return hex.substring(0, PREFIX_LENGTH)
    }

    private companion object {
        const val ASSET = "oui.txt"
        const val TAG = "OuiLookup"
        const val PREFIX_LENGTH = 6
        const val LOCALLY_ADMINISTERED = 0x02
    }
}
