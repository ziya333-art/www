package com.jegly.www.presentation.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.jegly.www.util.UrlUtils
import java.text.DateFormat

/**
 * Page security details, opened by tapping the lock / warning icon in the URL bar — the same
 * affordance every desktop browser has.
 *
 * For a browser that refuses bad certificates outright rather than offering a click-through, this
 * panel is where the refusal gets explained: without it, a blocked page is just a blank screen and
 * an icon the user can't interrogate.
 */
@Composable
fun SiteInfoDialog(
    tab: TabState,
    onDismiss: () -> Unit,
    onOpenDomainSettings: () -> Unit
) {
    val host = UrlUtils.hostOf(tab.url)
    val secure = UrlUtils.isSecure(tab.url)
    val cert = tab.certificate

    val (icon, tint, headline) = when {
        tab.hasSslError -> Triple(
            Icons.Filled.Warning,
            MaterialTheme.colorScheme.error,
            "Certificate rejected"
        )
        secure -> Triple(
            Icons.Filled.Lock,
            MaterialTheme.colorScheme.primary,
            "Connection is encrypted"
        )
        else -> Triple(
            Icons.Filled.LockOpen,
            MaterialTheme.colorScheme.error,
            "Connection is not encrypted"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp)) },
        title = { Text(headline) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                InfoRow("Site", host.ifBlank { "—" })

                // Punycode is shown alongside the display form when they differ, so a homograph
                // domain can't hide behind a lookalike rendering in this panel either.
                if (host.isNotBlank() && UrlUtils.isSpoofable(host)) {
                    InfoRow("Punycode", UrlUtils.toPunycode(host))
                    Text(
                        "This hostname mixes character sets and may be imitating another site.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Text(
                    text = when {
                        tab.hasSslError ->
                            "The certificate could not be validated, so the page was blocked. " +
                                "www does not offer a way to continue past this."
                        secure -> "Traffic to and from this site is encrypted with TLS."
                        else -> "This page was loaded over plain HTTP. Anyone on the network path " +
                            "can read or modify it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                if (cert != null) {
                    HorizontalDivider()
                    Text(
                        "Certificate",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    val issuedTo = cert.issuedTo
                    val issuedBy = cert.issuedBy
                    if (issuedTo != null) {
                        InfoRow("Issued to", issuedTo.cName.ifBlank { "—" })
                        if (issuedTo.oName.isNotBlank()) InfoRow("Organisation", issuedTo.oName)
                    }
                    if (issuedBy != null) {
                        InfoRow("Issued by", issuedBy.cName.ifBlank { issuedBy.oName.ifBlank { "—" } })
                    }
                    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    cert.validNotBeforeDate?.let { InfoRow("Valid from", df.format(it)) }
                    cert.validNotAfterDate?.let { date ->
                        InfoRow("Expires", df.format(date))
                        if (date.before(java.util.Date())) {
                            Text(
                                "This certificate has expired.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else if (secure && !tab.hasSslError) {
                    // Genuinely possible: a restored tab, or a page served from cache, can be
                    // encrypted while WebView reports no certificate for the current document.
                    Text(
                        "Certificate details are not available for this page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                InfoRow(
                    "Requests blocked",
                    if (tab.blockedCount == 0) "None on this page" else tab.blockedCount.toString()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onOpenDomainSettings() }) { Text("Site settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        /*
         * A dialog renders in its own window, so its screenshot protection is decided by this
         * policy rather than by the Activity's FLAG_SECURE directly. The default, Inherit, would
         * mirror the Activity — which means it would also follow the user turning the
         * screenshot-protection setting off.
         *
         * SecureOn instead: this panel is the densest concentration of identifying data in the app
         * — the hostname, the certificate's subject and issuing organisation, its validity dates —
         * and it is a deliberate, transient tap rather than something the user reads for long, so
         * protecting it unconditionally costs nothing they'd notice. Matches LinkContextSheet.
         */
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
