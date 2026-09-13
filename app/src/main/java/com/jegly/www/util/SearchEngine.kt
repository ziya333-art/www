package com.jegly.www.util

import android.net.Uri

/**
 * Zuzu-aligned search providers. All HTTPS, no Google, no accounts.
 * 4get (sny.sh) is default; zuzu-search is the user's own meta.
 */
enum class SearchEngine(
    val key: String,
    val displayName: String,
    private val queryUrl: String
) {
    FOURGET("4get", "4get", "https://4get.sny.sh/web?s="),
    ZUZU("zuzu", "zuzu search", "https://search.philara.org/web?s="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://www.duckduckgo.com/?q="),
    MOJEEK("mojeek", "Mojeek", "https://www.mojeek.com/search?q="),
    QWANT("qwant", "Qwant", "https://qwant.com/?q="),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q="),
    MARGINALIA("marginalia", "Marginalia", "https://search.marginalia.nu/search?query="),
    SEARXNG("searxng", "SearXNG (eu.priv.au)", "https://eu.priv.au/search?q="),
    PURI("puri", "Puri.li", "https://puri.li/?q="),
    LIBREY("librey", "LibreY", "https://librey.private.coffee/search.php?q=");

    fun searchUrlFor(query: String): String = queryUrl + Uri.encode(query)

    companion object {
        val DEFAULT = FOURGET
        fun fromKey(key: String?): SearchEngine = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
