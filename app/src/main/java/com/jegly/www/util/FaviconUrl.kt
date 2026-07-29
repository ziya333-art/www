package com.jegly.www.util

import android.net.Uri

/**
 * Derive a likely favicon URL from a page URL. For "https://example.com/some/article" returns
 * "https://example.com/favicon.ico". Most sites publish a favicon at the root of the host they
 * serve from; if the load fails Coil falls through to a placeholder.
 *
 * Returning null skips the network fetch entirely. Note this only guesses the root path — it does
 * not parse <link rel="icon"> out of the document, so sites that only declare a favicon in markup
 * will fall back to the placeholder.
 */
fun faviconUrlFor(pageUrl: String): String? {
    if (pageUrl.isBlank()) return null
    val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host ?: return null
    if (scheme != "https" && scheme != "http") return null
    // Always request via HTTPS — OkHttp upgrades cleartext anyway, but be explicit so the URL
    // string we hand Coil is the cache key Coil uses.
    return "https://$host/favicon.ico"
}
