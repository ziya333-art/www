package com.jegly.www.presentation.browser

import com.jegly.www.data.local.DomainSettingEntity
import com.jegly.www.network.UserAgentTemplate

/**
 * Merges a domain override onto the global preferences.
 *
 * Precedence is longest-domain-first, which the DAO query already orders for us: an exact rule for
 * "cdn.example.com" beats a wildcard "*.example.com". Only the first (most specific) match is
 * applied — rules are not layered, because layering makes it impossible for a user to reason about
 * what is actually in effect for a site, which defeats the point of having per-site control.
 */
object DomainSettingsResolver {

    fun apply(global: BrowserWebViewPrefs, match: DomainSettingEntity?): BrowserWebViewPrefs {
        if (match == null) return global
        return global.copy(
            javaScriptEnabled = match.javaScriptEnabled ?: global.javaScriptEnabled,
            domStorageEnabled = match.domStorageEnabled ?: global.domStorageEnabled,
            cookiePolicy = match.cookiePolicy ?: global.cookiePolicy,
            blockThirdPartyRequests = match.blockThirdPartyRequests ?: global.blockThirdPartyRequests,
            blockTrackers = match.blockTrackers ?: global.blockTrackers,
            stripTrackingQueries = match.stripTrackingQueries ?: global.stripTrackingQueries,
            displayImages = match.displayImages ?: global.displayImages,
            // userAgentKey stores the template key, not the UA string itself, so a per-domain rule
            // survives the template list changing underneath it. Resolved here rather than at write
            // time for the same reason.
            userAgent = match.userAgentKey
                ?.let { UserAgentTemplate.fromKey(it).uaString }
                ?: global.userAgent,
            // Kept in step with the string above: the key is what selects the client-hint metadata,
            // so letting them diverge would spoof the UA header of one browser while the Sec-CH-UA
            // headers still described another — the exact mismatch that metadata exists to close.
            userAgentKey = match.userAgentKey ?: global.userAgentKey
        )
    }

    /** Normalises user input into the stored form: lowercase, no scheme, no port, no path. */
    fun normaliseDomain(raw: String): String {
        var value = raw.trim().lowercase()
        value = value.substringAfter("://")
        value = value.substringBefore('/')
        value = value.substringBefore('?')
        // Keep a leading "*." but strip a port from the rest.
        val wildcard = value.startsWith("*.")
        val bare = if (wildcard) value.removePrefix("*.") else value
        val hostOnly = bare.substringBeforeLast(':', bare)
        return if (wildcard) "*.$hostOnly" else hostOnly
    }
}
