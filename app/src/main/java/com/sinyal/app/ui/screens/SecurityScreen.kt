package com.sinyal.app.ui.screens

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.net.DnsChecker
import com.sinyal.app.net.DnsReport
import com.sinyal.app.net.LanScanner
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.GradientButton
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.SignalColor
import com.sinyal.app.ui.theme.TextTone
import com.sinyal.app.wifi.RiskLevel
import com.sinyal.app.wifi.SecurityAudit
import com.sinyal.app.wifi.SecurityFinding
import com.sinyal.app.wifi.SecurityReport
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import androidx.compose.ui.text.style.TextOverflow
import com.sinyal.app.wifi.GradedNetwork
import com.sinyal.app.wifi.SecurityGrade
import com.sinyal.app.wifi.SecurityGrading
import com.sinyal.app.wifi.SecurityType

data class SecurityUiState(
    val report: SecurityReport? = null,
    val graded: List<GradedNetwork> = emptyList(),
    val mix: List<Pair<SecurityType, Int>> = emptyList(),
    val dns: DnsReport? = null,
    val gateway: String? = null,
    val running: Boolean = false,
)

class SecurityViewModel(private val app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)
    private val monitor = WifiMonitor(app)
    private val lan = LanScanner(app)
    private val dnsChecker = DnsChecker(lan)

    private val _state = MutableStateFlow(SecurityUiState())
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    init {
        run()
    }

    fun run() = viewModelScope.launch {
        _state.update { it.copy(running = true) }

        val link = withContext(Dispatchers.Default) { monitor.read() }
        val networks = withContext(Dispatchers.Default) { scanner.cachedResults(link.bssid) }
        val report = SecurityAudit.run(app.resources, networks, link.ssid)
        val gateway = withContext(Dispatchers.IO) { lan.readDetails().gateway }
        _state.update {
            it.copy(
                report = report,
                graded = SecurityGrading.gradeAll(networks),
                mix = SecurityGrading.mix(networks),
                gateway = gateway,
            )
        }

        // Name resolution is the slowest step and the only one that touches the
        // network, so the audit is already on screen before it finishes.
        val dns = dnsChecker.run()
        _state.update { it.copy(dns = dns, running = false) }
    }
}

/**
 * What the airspace and the connection say about safety.
 *
 * Every judgement here comes from beacons any device in range already receives,
 * plus name lookups this phone makes for itself. Nothing probes, joins, or
 * attacks another network — the value is in translating capability strings into
 * consequences a person can act on.
 */
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            BackBar(title = stringResource(R.string.security_title), onBack = onBack)

            val report = state.report
            Spacer(Modifier.height(16.dp))
            HeadlineCard(report)

            report?.yourNetwork?.let { verdict ->
                Spacer(Modifier.height(12.dp))
                FindingCard(verdict)
            }

            state.dns?.let { dns ->
                Spacer(Modifier.height(12.dp))
                DnsCard(dns)
            }

            state.gateway?.let { gateway ->
                Spacer(Modifier.height(12.dp))
                RouterCard(gateway) {
                    openRouter(context, gateway)
                }
            }

            if (state.mix.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MixCard(state.mix)
            }

            if (report != null && report.findings.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.security_findings_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                report.findings.forEach { finding ->
                    FindingCard(finding)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (state.graded.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.security_graded_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                state.graded.forEach { graded ->
                    GradedRow(graded)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.security_graded_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTone.Tertiary,
                )
            }

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(
                    if (state.running) R.string.security_checking else R.string.security_recheck,
                ),
                onClick = { viewModel.run() },
                enabled = !state.running,
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.security_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Opens the gateway's own web interface in whatever browser is installed.
 *
 * A missing browser is the only realistic failure, and it is left silent on
 * purpose: crashing the app because a tap could not be honoured would be a far
 * worse outcome than a button that does nothing on a device with no browser.
 */
private fun openRouter(context: android.content.Context, gateway: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$gateway"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed.
    }
}

/** What protection the airspace is actually using, counted. */
@Composable
private fun MixCard(mix: List<Pair<SecurityType, Int>>) {
    val total = mix.sumOf { it.second }.coerceAtLeast(1)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.security_mix_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(10.dp))

        mix.forEach { (type, count) ->
            val tint = when {
                type.isUnencrypted -> SignalColor.Dead
                type.joinableWithoutPassword -> SignalColor.Fair
                type == SecurityType.WPA -> SignalColor.Weak
                else -> SignalColor.Excellent
            }

            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(type.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTone.Secondary,
                    )
                    Text(
                        text = stringResource(R.string.security_mix_count, count),
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Ink.Raised),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(count.toFloat() / total)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tint),
                    )
                }
            }
        }
    }
}

/** One network with its grade and the single fact behind it. */
@Composable
private fun GradedRow(graded: GradedNetwork) {
    val tint = when (graded.grade) {
        SecurityGrade.A -> SignalColor.Excellent
        SecurityGrade.B -> SignalColor.Good
        SecurityGrade.C -> SignalColor.Fair
        SecurityGrade.F -> SignalColor.Dead
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = graded.grade.letter,
                    style = MaterialTheme.typography.titleMedium,
                    color = tint,
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = graded.ap.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(graded.reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
            Text(
                text = stringResource(graded.grade.label),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

@Composable
private fun HeadlineCard(report: SecurityReport?) {
    val high = report?.highCount ?: 0
    val tone = when {
        report == null -> TextTone.Tertiary
        high > 0 -> SignalColor.Dead
        report.findings.any { it.level == RiskLevel.MEDIUM } -> SignalColor.Fair
        else -> SignalColor.Excellent
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.security_summary_title),
            style = MaterialTheme.typography.labelSmall,
            color = TextTone.Tertiary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                report == null -> stringResource(R.string.security_checking)
                report.networksExamined == 0 -> stringResource(R.string.security_no_scan)
                high > 0 -> stringResource(R.string.security_high_count, high)
                report.findings.isEmpty() -> stringResource(R.string.security_all_clear)
                else -> stringResource(
                    R.string.security_attention_count,
                    report.findings.size,
                )
            },
            style = MaterialTheme.typography.headlineSmall,
            color = tone,
        )
        if (report != null && report.networksExamined > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.security_examined, report.networksExamined),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
        }
    }
}

@Composable
private fun FindingCard(finding: SecurityFinding) {
    val tone = when (finding.level) {
        RiskLevel.HIGH -> SignalColor.Dead
        RiskLevel.MEDIUM -> SignalColor.Fair
        RiskLevel.INFO -> SignalColor.Excellent
    }
    val icon = when (finding.level) {
        RiskLevel.HIGH -> Icons.Rounded.Warning
        RiskLevel.MEDIUM -> Icons.Rounded.Info
        RiskLevel.INFO -> Icons.Rounded.CheckCircle
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = finding.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextTone.Primary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = finding.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
        if (finding.networks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                finding.networks.take(3).forEach { name ->
                    NameChip(name, tone)
                }
            }
            if (finding.networks.size > 3) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.security_and_more,
                        finding.networks.size - 3,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun DnsCard(dns: DnsReport) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.security_dns_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextTone.Primary,
            )
            Text(
                text = dns.averageMs?.let { stringResource(R.string.unit_ms, it) }
                    ?: stringResource(R.string.value_none),
                style = MaterialTheme.typography.titleMedium,
                color = if (dns.allResolved) SignalColor.Excellent else SignalColor.Dead,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(dns.verdict),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
        if (dns.servers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.security_dns_servers,
                    dns.servers.take(2).joinToString(", "),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
            )
        }
    }
}

@Composable
private fun RouterCard(gateway: String, onOpen: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.security_router_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextTone.Primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.security_router_body, gateway),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
        )
        Spacer(Modifier.height(12.dp))
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Accent.Glow)
                .border(1.dp, Accent.Base, shape)
                .clickable(onClick = onOpen)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = Accent.Bright,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.security_router_open, gateway),
                style = MaterialTheme.typography.labelLarge,
                color = Accent.Bright,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun NameChip(text: String, tint: Color) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Ink.Raised)
            .border(1.dp, Ink.Stroke, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
