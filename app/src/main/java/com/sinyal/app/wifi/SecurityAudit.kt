package com.sinyal.app.wifi

import android.content.res.Resources
import com.sinyal.app.R

/** How much a finding should worry the person reading it. */
enum class RiskLevel(val label: Int) {
    HIGH(R.string.risk_high),
    MEDIUM(R.string.risk_medium),
    INFO(R.string.risk_info),
}

/** One thing worth knowing about the airspace, in plain language. */
data class SecurityFinding(
    val title: String,
    val detail: String,
    val level: RiskLevel,
    /** Networks this finding is about, so the card can name them. */
    val networks: List<String>,
)

data class SecurityReport(
    val findings: List<SecurityFinding>,
    val networksExamined: Int,
    val yourNetwork: SecurityFinding?,
) {
    val highCount: Int get() = findings.count { it.level == RiskLevel.HIGH }
}

/**
 * Reads the scan for the handful of conditions that actually put someone at risk.
 *
 * Everything here comes from beacons that any device in range can already see —
 * nothing is probed, joined, or attacked. The point is to translate capability
 * strings into consequences, because "[WEP][ESS]" tells a user nothing and
 * "anyone nearby can read this traffic" tells them everything.
 *
 * Findings are resolved to text here rather than carried as resource ids,
 * because each one interpolates counts and network names; the caller passes the
 * [Resources] it already holds.
 */
object SecurityAudit {

    fun run(res: Resources, networks: List<NearbyAp>, currentSsid: String?): SecurityReport {
        if (networks.isEmpty()) {
            return SecurityReport(emptyList(), 0, null)
        }

        val findings = buildList {
            unencrypted(res, networks)?.let(::add)
            wep(res, networks)?.let(::add)
            wps(res, networks)?.let(::add)
            twins(res, networks)?.let(::add)
            hidden(res, networks)?.let(::add)
            legacy(res, networks)?.let(::add)
        }

        return SecurityReport(
            findings = findings.sortedBy { it.level.ordinal },
            networksExamined = networks.size,
            yourNetwork = currentSsid?.let { ssid ->
                networks.firstOrNull { it.isCurrent }?.let { verdictFor(res, it, ssid) }
            },
        )
    }

    /** The one finding phrased about the user's own network rather than the air. */
    private fun verdictFor(res: Resources, ap: NearbyAp, ssid: String): SecurityFinding = when {
        ap.security.isUnencrypted -> SecurityFinding(
            title = res.getString(R.string.audit_own_unencrypted_title),
            detail = res.getString(R.string.audit_own_unencrypted_body),
            level = RiskLevel.HIGH,
            networks = listOf(ssid),
        )

        ap.security == SecurityType.WPA -> SecurityFinding(
            title = res.getString(R.string.audit_own_wpa_title),
            detail = res.getString(R.string.audit_own_wpa_body),
            level = RiskLevel.MEDIUM,
            networks = listOf(ssid),
        )

        ap.supportsWps -> SecurityFinding(
            title = res.getString(R.string.audit_own_wps_title),
            detail = res.getString(R.string.audit_own_wps_body),
            level = RiskLevel.MEDIUM,
            networks = listOf(ssid),
        )

        ap.security == SecurityType.WPA3 -> SecurityFinding(
            title = res.getString(R.string.audit_own_wpa3_title),
            detail = res.getString(R.string.audit_own_wpa3_body),
            level = RiskLevel.INFO,
            networks = listOf(ssid),
        )

        else -> SecurityFinding(
            title = res.getString(
                R.string.audit_own_secured_title,
                res.getString(ap.security.label),
            ),
            detail = res.getString(R.string.audit_own_secured_body),
            level = RiskLevel.INFO,
            networks = listOf(ssid),
        )
    }

    private fun unencrypted(res: Resources, networks: List<NearbyAp>): SecurityFinding? {
        val names = networks
            .filter { it.security == SecurityType.OPEN }
            .map { it.ssid }
            .distinct()
        if (names.isEmpty()) return null

        return SecurityFinding(
            title = res.getString(R.string.audit_open_title, names.size),
            detail = res.getString(R.string.audit_open_body),
            level = RiskLevel.MEDIUM,
            networks = names,
        )
    }

    private fun wep(res: Resources, networks: List<NearbyAp>): SecurityFinding? {
        val names = networks.filter { it.security == SecurityType.WEP }.map { it.ssid }.distinct()
        if (names.isEmpty()) return null

        return SecurityFinding(
            title = res.getString(R.string.audit_wep_title, names.size),
            detail = res.getString(R.string.audit_wep_body),
            level = RiskLevel.HIGH,
            networks = names,
        )
    }

    private fun wps(res: Resources, networks: List<NearbyAp>): SecurityFinding? {
        val names = networks.filter { it.supportsWps }.map { it.ssid }.distinct()
        if (names.isEmpty()) return null

        return SecurityFinding(
            title = res.getString(R.string.audit_wps_title, names.size),
            detail = res.getString(R.string.audit_wps_body),
            level = RiskLevel.MEDIUM,
            networks = names,
        )
    }

    /**
     * Same name, different protection.
     *
     * A rogue access point copies a familiar SSID and leaves itself open so
     * devices join without prompting. Several BSSIDs under one SSID is normal —
     * that is what a mesh looks like — so only a mismatch in security is
     * reported, and it is reported as something to check rather than an attack:
     * a router replaced mid-upgrade produces exactly the same pattern.
     */
    private fun twins(res: Resources, networks: List<NearbyAp>): SecurityFinding? {
        val suspect = networks
            .filter { !it.isHidden }
            .groupBy { it.ssid }
            .filterValues { group ->
                group.size > 1 && group.map { it.security }.distinct().size > 1
            }
            .keys
            .toList()
        if (suspect.isEmpty()) return null

        return SecurityFinding(
            title = res.getString(R.string.audit_twin_title),
            detail = res.getString(R.string.audit_twin_body),
            level = RiskLevel.HIGH,
            networks = suspect,
        )
    }

    private fun hidden(res: Resources, networks: List<NearbyAp>): SecurityFinding? {
        val count = networks.count { it.isHidden }
        if (count == 0) return null

        return SecurityFinding(
            title = res.getString(R.string.audit_hidden_title, count),
            detail = res.getString(R.string.audit_hidden_body),
            level = RiskLevel.INFO,
            networks = emptyList(),
        )
    }

    private fun legacy(res: Resources, networks: List<NearbyAp>): SecurityFinding? {
        val names = networks
            .filter { it.security == SecurityType.WPA }
            .map { it.ssid }
            .distinct()
        if (names.isEmpty()) return null

        return SecurityFinding(
            title = res.getString(R.string.audit_legacy_title, names.size),
            detail = res.getString(R.string.audit_legacy_body),
            level = RiskLevel.MEDIUM,
            networks = names,
        )
    }
}
