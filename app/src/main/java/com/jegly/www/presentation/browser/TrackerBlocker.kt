package com.jegly.www.presentation.browser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Host-level request blocking for `shouldInterceptRequest`.
 *
 * This is a hardcoded host set, not a filter-list engine. It has
 * no rule syntax, no per-site exceptions, and no cosmetic filtering, so it will always be coarser
 * than uBlock Origin or Privacy Browser's EasyList support. Replacing it with a parsed EasyPrivacy
 * list is tracked as a follow-up; the interface here (one host predicate) is what that replacement
 * would slot into.
 */
object TrackerBlocker {

    private val BLOCKED_HOSTS = setOf(
        "google-analytics.com", "googletagmanager.com", "analytics.google.com",
        "doubleclick.net", "googlesyndication.com", "adservice.google.com",
        "connect.facebook.net", "staticxx.facebook.com",
        "ads.twitter.com", "analytics.twitter.com",
        "scorecardresearch.com", "quantserve.com",
        "hotjar.com", "fullstory.com",
        "segment.io", "cdn.segment.com", "api.segment.io",
        "cdn.mxpnl.com", "mixpanel.com",
        "amplitude.com", "api.amplitude.com",
        "heapanalytics.com", "heap.io",
        "newrelic.com", "nr-data.net",
        "adnxs.com", "rubiconproject.com", "openx.net",
        "pubmatic.com", "criteo.com", "criteo.net",
        "outbrain.com", "taboola.com", "moatads.com",
        "branch.io", "adjust.com", "appsflyer.com",
        "bugsnag.com", "sentry.io", "clarity.ms"
    )

    fun isBlocked(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return BLOCKED_HOSTS.any { blocked -> h == blocked || h.endsWith(".$blocked") }
    }

    /**
     * An empty 200 rather than a null/error response. Returning an error makes some scripts retry
     * in a loop or throw uncaught exceptions that break the rest of the page; an empty body reads
     * to them as "loaded, nothing there" and pages degrade more quietly.
     */
    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
