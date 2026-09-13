package com.jegly.www.presentation.browser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

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

    private val RUNTIME_HOSTS = HashSet<String>()

    // Precompiled ONCE — never rebuild regexes in the hot loop.
    private val WS = Regex("\\s+")
    private val IP_ONLY = Regex("[0-9.]+")

    fun isBlocked(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        if (h.isEmpty()) return false
        if (inSet(h)) return true
        var dot = h.indexOf('.')
        while (dot in 0 until h.length - 1) {
            if (inSet(h.substring(dot + 1))) return true
            dot = h.indexOf('.', dot + 1)
        }
        return false
    }

    private fun inSet(h: String): Boolean = h in BLOCKED_HOSTS || h in RUNTIME_HOSTS

    private fun tokenize(line: String?): List<String>? {
        val l = line?.trim() ?: return null
        if (l.isEmpty() || l.startsWith("#") || l.startsWith("!") || l.startsWith("[")) return null
        return WS.split(l)
            .map { it.trim().removePrefix("||").removeSuffix("^").removePrefix("*.").removePrefix(".") }
            .filter { t -> t.contains('.') && !t.contains('/') && !IP_ONLY.matches(t) && !t.startsWith("address=") }
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
