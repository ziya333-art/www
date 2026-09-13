package com.jegly.www.presentation.browser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object TrackerBlocker {

    // Always-on baseline regardless of bundled lists.
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

    // Bundled list domains added at runtime from assets (hagezi + nogoogle). Separate from base.
    private val RUNTIME_HOSTS = HashSet<String>()

    fun isBlocked(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        if (h.isEmpty()) return false
        if (inSet(h)) return true
        val lowerHost = h
        var dot = lowerHost.indexOf('.')
        while (dot in 0 until lowerHost.length - 1) {
            if (inSet(lowerHost.substring(dot + 1))) return true
            dot = lowerHost.indexOf('.', dot + 1)
        }
        return false
    }

    private fun inSet(h: String): Boolean = h in BLOCKED_HOSTS || h in RUNTIME_HOSTS

    /**
     * Parse a bundled hosts/wildcard/pihole line into candidate domains.
     * Handles "0.0.0.0 example.com", "*.example.com", "example.com".
     */
    private fun tokenize(line: String?): List<String>? {
        val l = line?.trim() ?: return null
        if (l.isEmpty() || l.startsWith("#") || l.startsWith("!") || l.startsWith("[")) return null
        return l.split(Regex("\\s+"))
            .map { it.trim().removePrefix("*.").removePrefix(".") }
            .filter { t ->
                t.contains('.') && !t.contains('/') &&
                    !t.matches(Regex("[0-9.]+")) && !t.startsWith("address=")
            }
            .distinct()
    }

    fun load(context: Context) {
        if (RUNTIME_HOSTS.isNotEmpty()) return
        val files = context.assets.list("hosts")?.filter { it.endsWith(".txt") }.orEmpty()
        for (name in files) {
            try {
                context.assets.open("hosts/$name").bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        tokenize(line)?.forEach { RUNTIME_HOSTS.add(it) }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
