package com.jegly.www.util

import android.net.Uri

object LinkSanitizer {
    /**
     * Query parameters stripped from navigations. "ref" is deliberately included even though it is
     * occasionally load-bearing on forums — the per-domain settings screen is the escape hatch for
     * a site where stripping it breaks something.
     */
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_name", "utm_cid", "utm_reader", "utm_social",
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid",
        "mc_eid", "mc_cid", "originalSub", "ref", "ref_src", "ref_url",
        "igshid", "twclid", "ttclid", "li_fat_id", "vero_id", "s_kwcid",
        "_openstat", "oly_anon_id", "oly_enc_id", "hsCtaTracking", "__hssc", "__hstc"
    )

    /**
     * Strips tracking parameters *and* upgrades http to https.
     *
     * Only for handing URLs to something outside the WebView (an external Custom Tab or another
     * app). Do not use this on in-browser navigations: the forced scheme rewrite would override the
     * user's HTTPS-only preference and, because the rewritten URL re-enters navigation handling,
     * could bounce a plain-HTTP-only site indefinitely.
     */
    fun sanitize(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val secureUri = if (uri.scheme.equals("http", ignoreCase = true)) {
                uri.buildUpon().scheme("https").build()
            } else {
                uri
            }
            stripQuery(secureUri)
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Strips tracking parameters only, leaving the scheme untouched.
     *
     * This is the one used for in-browser navigation. Returning the input unchanged when there is
     * nothing to strip matters: the caller compares the result against the original to decide
     * whether to re-issue the load, so a gratuitous re-encoding would cause an infinite reload.
     */
    fun sanitizeQueryOnly(url: String): String {
        return try {
            val uri = Uri.parse(url)
            if (uri.query == null) return url
            if (uri.queryParameterNames.none { it.lowercase() in TRACKING_PARAMS }) return url
            stripQuery(uri)
        } catch (e: Exception) {
            url
        }
    }

    private fun stripQuery(uri: Uri): String {
        if (uri.query == null) return uri.toString()
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { name ->
            if (name.lowercase() !in TRACKING_PARAMS) {
                uri.getQueryParameters(name).forEach { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        return builder.build().toString()
    }
}
