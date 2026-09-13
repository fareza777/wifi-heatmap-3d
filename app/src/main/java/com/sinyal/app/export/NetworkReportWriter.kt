package com.sinyal.app.export

import android.content.res.Resources
import com.sinyal.app.R
import com.sinyal.app.wifi.InternetStatus
import com.sinyal.app.wifi.NearbyAp
import com.sinyal.app.wifi.SecurityAudit
import com.sinyal.app.wifi.SecurityType
import com.sinyal.app.wifi.SignalQuality
import com.sinyal.app.wifi.WifiSnapshot
import com.sinyal.app.wifi.estimatedDistanceMeters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the whole survey out as a plain-text report.
 *
 * Meant to be forwarded: to a landlord arguing the signal is fine, to an ISP
 * that wants evidence, or into a note for the next visit. That rules out a
 * screenshot — this has to be searchable, quotable and readable on a laptop —
 * and it rules out silent omissions, so anything the scan could not determine
 * is written down as unknown rather than left out.
 */
object NetworkReportWriter {

    private const val RULE = "────────────────────────────────────────"

    fun write(
        res: Resources,
        link: WifiSnapshot,
        internet: InternetStatus,
        networks: List<NearbyAp>,
        vendors: Map<String, String>,
    ): String = buildString {
        // The user's own locale, not a pinned one: the report is read by whoever
        // the phone belongs to, in the language the rest of the app is showing.
        val stamp = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
            .format(Date())

        appendLine(res.getString(R.string.report_title))
        appendLine(stamp)
        appendLine(RULE)
        appendLine()

        appendConnection(res, link, internet)
        appendAudit(res, networks, link.ssid)
        appendSurvey(res, networks, vendors)

        appendLine(RULE)
        appendLine(res.getString(R.string.report_footer))
    }

    private fun StringBuilder.appendConnection(
        res: Resources,
        link: WifiSnapshot,
        internet: InternetStatus,
    ) {
        appendLine(res.getString(R.string.report_connection_title))
        if (!link.connected) {
            appendLine(res.getString(R.string.report_not_connected))
            appendLine()
            return
        }

        val unknown = res.getString(R.string.report_unknown)
        appendLine(res.getString(R.string.report_row_ssid, link.ssid ?: unknown))
        appendLine(res.getString(R.string.report_row_bssid, link.bssid ?: unknown))
        appendLine(
            res.getString(
                R.string.report_row_signal,
                link.rssiDbm,
                res.getString(link.quality.label),
            ),
        )
        appendLine(res.getString(R.string.report_row_band, link.band.label, link.channel))
        appendLine(res.getString(R.string.report_row_speed, link.linkSpeedMbps))
        appendLine(res.getString(R.string.report_row_standard, link.generation.label))
        appendLine(res.getString(R.string.report_row_internet, res.getString(internet.summary)))
        appendLine()
    }

    private fun StringBuilder.appendAudit(
        res: Resources,
        networks: List<NearbyAp>,
        ssid: String?,
    ) {
        val report = SecurityAudit.run(res, networks, ssid)
        appendLine(res.getString(R.string.report_audit_title))

        if (report.networksExamined == 0) {
            appendLine(res.getString(R.string.report_audit_empty))
            appendLine()
            return
        }

        report.yourNetwork?.let { appendFinding(res, it) }

        if (report.findings.isEmpty()) {
            appendLine(res.getString(R.string.report_audit_clean, report.networksExamined))
        } else {
            report.findings.forEach { appendFinding(res, it) }
        }
        appendLine()
    }

    private fun StringBuilder.appendFinding(
        res: Resources,
        finding: com.sinyal.app.wifi.SecurityFinding,
    ) {
        appendLine(
            res.getString(
                R.string.report_finding,
                res.getString(finding.level.label),
                finding.title,
            ),
        )
        appendLine(res.getString(R.string.report_finding_detail, finding.detail))
        if (finding.networks.isNotEmpty()) {
            appendLine(
                res.getString(
                    R.string.report_finding_networks,
                    finding.networks.joinToString(", "),
                ),
            )
        }
    }

    private fun StringBuilder.appendSurvey(
        res: Resources,
        networks: List<NearbyAp>,
        vendors: Map<String, String>,
    ) {
        appendLine(res.getString(R.string.report_survey_title, networks.size))
        if (networks.isEmpty()) {
            appendLine(res.getString(R.string.report_survey_empty))
            appendLine()
            return
        }

        networks.forEachIndexed { index, ap ->
            val connected = if (ap.isCurrent) res.getString(R.string.report_ap_connected) else ""
            appendLine(res.getString(R.string.report_ap_name, index + 1, ap.ssid) + connected)
            appendLine(res.getString(R.string.report_ap_bssid, ap.bssid))
            vendors[ap.bssid]?.let {
                appendLine(res.getString(R.string.report_ap_vendor, it))
            }
            appendLine(
                res.getString(
                    R.string.report_ap_signal,
                    ap.rssiDbm,
                    (SignalQuality.normalize(ap.rssiDbm) * 100).toInt(),
                    estimatedDistanceMeters(ap.rssiDbm),
                ),
            )
            appendLine(
                res.getString(
                    R.string.report_ap_band,
                    ap.band.label,
                    ap.channel,
                    ap.channelWidthMhz,
                ),
            )
            appendLine(
                res.getString(
                    R.string.report_ap_security,
                    res.getString(ap.security.label),
                    flags(res, ap),
                ),
            )
            appendLine(res.getString(R.string.report_ap_standard, ap.generation.label))
        }
        appendLine()
    }

    /** Extra notes only when they apply, so the common case stays uncluttered. */
    private fun flags(res: Resources, ap: NearbyAp): String {
        val notes = buildList {
            if (ap.security == SecurityType.OPEN) add(res.getString(R.string.report_flag_open))
            if (ap.security == SecurityType.OWE) add(res.getString(R.string.report_flag_owe))
            if (ap.supportsWps) add(res.getString(R.string.report_flag_wps))
            if (ap.isHidden) add(res.getString(R.string.report_flag_hidden))
        }
        return if (notes.isEmpty()) {
            ""
        } else {
            res.getString(R.string.report_flags, notes.joinToString(", "))
        }
    }
}
