package com.sinyal.app.wifi

import androidx.annotation.StringRes
import com.sinyal.app.R

/**
 * How an access point protects itself, read from the scan's capability string.
 *
 * The distinction that matters most to a user is whether a network can be joined
 * without a password at all — and, separately, whether doing so is safe. [OPEN]
 * is both passwordless and unencrypted; [OWE] is passwordless but still
 * encrypted, which are very different propositions despite looking identical in
 * the system Wi-Fi picker.
 */
enum class SecurityType(
    @StringRes val label: Int,
    val joinableWithoutPassword: Boolean,
) {
    OPEN(R.string.security_open, true),
    OWE(R.string.security_owe, true),
    WEP(R.string.security_wep, false),
    WPA(R.string.security_wpa, false),
    WPA2(R.string.security_wpa2, false),
    WPA3(R.string.security_wpa3, false),
    ENTERPRISE(R.string.security_enterprise, false),
    UNKNOWN(R.string.security_unknown, false),
    ;

    /** True when traffic on this network is not encrypted by the link itself. */
    val isUnencrypted: Boolean get() = this == OPEN || this == WEP

    companion object {
        /**
         * Parses [ScanResult.capabilities], e.g. `[WPA2-PSK-CCMP][ESS]`.
         *
         * Order matters: a network advertising both SAE and PSK for backwards
         * compatibility is reported as WPA3, because that is what a modern
         * client will actually negotiate.
         */
        fun parse(capabilities: String?): SecurityType {
            val caps = capabilities.orEmpty().uppercase()
            return when {
                caps.contains("EAP") || caps.contains("IEEE8021X") -> ENTERPRISE
                caps.contains("SAE") -> WPA3
                caps.contains("OWE") -> OWE
                caps.contains("WPA2") || caps.contains("RSN") -> WPA2
                caps.contains("WPA") -> WPA
                caps.contains("WEP") -> WEP
                // An open network advertises only its BSS type and nothing else.
                caps.contains("ESS") || caps.isBlank() -> OPEN
                else -> UNKNOWN
            }
        }
    }
}
