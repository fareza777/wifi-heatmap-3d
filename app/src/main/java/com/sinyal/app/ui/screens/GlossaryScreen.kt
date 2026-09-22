package com.sinyal.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sinyal.app.R
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone

private data class Term(@StringRes val term: Int, @StringRes val definition: Int)

private data class GlossaryGroup(@StringRes val title: Int, val terms: List<Term>)

/**
 * What the abbreviations actually mean, in one line each.
 *
 * Short on purpose: a term someone just met wants its translation, not its
 * history. Anything deeper belongs in the tool that uses it.
 */
@Composable
fun GlossaryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            BackBar(title = stringResource(R.string.glossary_title), onBack = onBack)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.glossary_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Secondary,
            )

            GROUPS.forEach { group ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(group.title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTone.Tertiary,
                )
                Spacer(Modifier.height(8.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    group.terms.forEachIndexed { index, term ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = Ink.Stroke,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                        Text(
                            text = stringResource(term.term),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextTone.Primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(term.definition),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTone.Secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

private val GROUPS = listOf(
    GlossaryGroup(
        title = R.string.glossary_group_signal,
        terms = listOf(
            Term(R.string.gloss_dbm_t, R.string.gloss_dbm_d),
            Term(R.string.gloss_rssi_t, R.string.gloss_rssi_d),
            Term(R.string.gloss_quality_t, R.string.gloss_quality_d),
            Term(R.string.gloss_heatmap_t, R.string.gloss_heatmap_d),
            Term(R.string.gloss_roam_t, R.string.gloss_roam_d),
        ),
    ),
    GlossaryGroup(
        title = R.string.glossary_group_airspace,
        terms = listOf(
            Term(R.string.gloss_ssid_t, R.string.gloss_ssid_d),
            Term(R.string.gloss_bssid_t, R.string.gloss_bssid_d),
            Term(R.string.gloss_band_t, R.string.gloss_band_d),
            Term(R.string.gloss_channel_t, R.string.gloss_channel_d),
            Term(R.string.gloss_dfs_t, R.string.gloss_dfs_d),
            Term(R.string.gloss_width_t, R.string.gloss_width_d),
            Term(R.string.gloss_interference_t, R.string.gloss_interference_d),
            Term(R.string.gloss_generation_t, R.string.gloss_generation_d),
        ),
    ),
    GlossaryGroup(
        title = R.string.glossary_group_measurement,
        terms = listOf(
            Term(R.string.gloss_latency_t, R.string.gloss_latency_d),
            Term(R.string.gloss_jitter_t, R.string.gloss_jitter_d),
            Term(R.string.gloss_throughput_t, R.string.gloss_throughput_d),
            Term(R.string.gloss_bufferbloat_t, R.string.gloss_bufferbloat_d),
            Term(R.string.gloss_mbps_t, R.string.gloss_mbps_d),
            Term(R.string.gloss_rtt_t, R.string.gloss_rtt_d),
        ),
    ),
    GlossaryGroup(
        title = R.string.glossary_group_network,
        terms = listOf(
            Term(R.string.gloss_ip_t, R.string.gloss_ip_d),
            Term(R.string.gloss_gateway_t, R.string.gloss_gateway_d),
            Term(R.string.gloss_dhcp_t, R.string.gloss_dhcp_d),
            Term(R.string.gloss_dns_t, R.string.gloss_dns_d),
            Term(R.string.gloss_mdns_t, R.string.gloss_mdns_d),
            Term(R.string.gloss_upnp_t, R.string.gloss_upnp_d),
            Term(R.string.gloss_mesh_t, R.string.gloss_mesh_d),
        ),
    ),
)
