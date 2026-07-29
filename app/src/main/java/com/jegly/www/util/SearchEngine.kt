package com.jegly.www.util

import android.net.Uri

/**
 * Search providers offered in Settings. All are HTTPS and none require an account.
 *
 * DuckDuckGo is the default rather than Google: this browser blocks third-party cookies and sends
 * Sec-GPC by default, and a default engine that ignores both would undercut that.
 */
enum class SearchEngine(
    val key: String,
    val displayName: String,
    private val queryUrl: String
) {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/sp/search?query="),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q="),
    MOJEEK("mojeek", "Mojeek", "https://www.mojeek.com/search?q="),
    SEARXNG("searxng", "SearXNG (searx.be)", "https://searx.be/search?q="),
    WIKIPEDIA("wikipedia", "Wikipedia", "https://en.wikipedia.org/w/index.php?search="),
    GOOGLE("google", "Google", "https://www.google.com/search?q=");

    fun searchUrlFor(query: String): String = queryUrl + Uri.encode(query)

    companion object {
        val DEFAULT = DUCKDUCKGO
        fun fromKey(key: String?): SearchEngine = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
