package com.jegly.www.util

import android.net.Uri
import android.util.Patterns

/**
 * Omnibox input handling: deciding whether what the user typed is a destination or a search.
 *
 * Getting this wrong is user-visible in both directions — treating "how do i tie a tie" as a
 * hostname produces a DNS error, and treating "router.local" as a search leaks an intranet
 * hostname to a search engine. The bias here is deliberate: anything ambiguous goes to search,
 * because a needless search is recoverable and a leaked hostname is not.
 */
object UrlUtils {

    /** Schemes we hand straight to the WebView rather than reinterpreting. */
    private val WEB_SCHEMES = setOf("http", "https")

    /** Non-web schemes that are still destinations, routed out to the system by BrowserUtils. */
    private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "mms", "geo")

    /** Schemes the WebView renders itself; never rewritten and never sent to an intent. */
    private val INTERNAL_SCHEMES = setOf("blob", "about", "content")

    /**
     * Schemes that must never be reachable from the omnibox.
     *
     * `javascript:` is the self-XSS vector — the "paste this into your address bar" scam. Typed or
     * pasted into the URL bar it executes in whatever origin is currently loaded, so it can read
     * the session of the site the user is on. Browsers universally strip it from pasted input.
     *
     * `file:` is blocked because allowFileAccess is false anyway, and letting it through would only
     * produce a confusing failure while advertising that local files are a thing to try.
     *
     * `data:` is the same scam wearing a different hat — "paste this into your address bar" ending
     * in a page that renders whatever the attacker wrote, in an origin the URL bar cannot describe
     * (there is no host, so displayOrigin falls back to printing the raw URL). Page-initiated
     * top-level data: navigation is refused in BrowserWebView's shouldOverrideUrlLoading for the
     * same reason; this closes the typed/pasted half of it. data: as a subresource — an inline
     * image, a font — is untouched by either.
     */
    private val BLOCKED_INPUT_SCHEMES = setOf("javascript", "data", "file", "jar", "intent", "android-app")

    /**
     * Hostnames with no dot that are still real destinations. Without this, "localhost:8080" would
     * be searched for — which is exactly the wrong answer for anyone developing against a local
     * server, and one of the more common complaints about mobile browsers.
     */
    private val DOTLESS_HOSTS = setOf("localhost")

    sealed interface Destination {
        /** Load directly in the WebView. */
        data class Web(val url: String) : Destination
        /** Hand to the system (mailto:, tel:, …). */
        data class External(val url: String) : Destination
        /** Render in place; never leaves the WebView. */
        data class Internal(val url: String) : Destination
        /** Not a location — run it through the configured search engine. */
        data class Search(val query: String) : Destination
        /** Refused outright. The omnibox shows why rather than silently doing nothing. */
        data class Blocked(val reason: String) : Destination
    }

    /**
     * Classify raw omnibox text. [defaultToHttps] controls the scheme added to a bare hostname;
     * callers pass the user's HTTPS-only preference so a scheme-less "example.com" honours it.
     */
    fun parse(rawInput: String, defaultToHttps: Boolean = true): Destination {
        val input = rawInput.trim()
        if (input.isEmpty()) return Destination.Search("")

        val scheme = runCatching { Uri.parse(input).scheme?.lowercase() }.getOrNull()

        when (scheme) {
            in BLOCKED_INPUT_SCHEMES -> return Destination.Blocked(
                "$scheme: links can't be opened from the address bar"
            )
            in WEB_SCHEMES -> return Destination.Web(input)
            in EXTERNAL_SCHEMES -> return Destination.External(input)
            in INTERNAL_SCHEMES -> return Destination.Internal(input)
        }

        // Whitespace anywhere means it can't be a bare hostname. Checked before the host heuristics
        // so "site.com and other stuff" searches instead of trying to resolve a host.
        if (input.any { it.isWhitespace() }) return Destination.Search(input)

        if (looksLikeHost(input)) {
            val prefix = if (defaultToHttps) "https://" else "http://"
            return Destination.Web(prefix + input)
        }

        return Destination.Search(input)
    }

    /**
     * Heuristic host test for scheme-less input. Accepts IP literals, localhost, and anything with
     * a plausible dotted TLD; rejects the rest so it falls through to search.
     */
    private fun looksLikeHost(input: String): Boolean {
        // Strip a port and path before testing the authority: "localhost:8080/api" -> "localhost".
        val authority = input.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringBeforeLast(':', authority).ifEmpty { return false }

        if (host.lowercase() in DOTLESS_HOSTS) return true
        // InetAddresses.isNumericAddress, not the deprecated Patterns.IP_ADDRESS regex: the
        // latter has known false-positive/negative edge cases on malformed dotted-quads that
        // this replacement doesn't share, and it covers IPv6 literals too.
        if (runCatching { android.net.InetAddresses.isNumericAddress(host) }.getOrDefault(false)) return true
        if (host.startsWith("[") && host.endsWith("]")) return true // bracketed IPv6

        if (!host.contains('.')) return false
        if (host.startsWith('.') || host.endsWith('.')) return false

        // Require a TLD that is at least two characters and entirely alphabetic. This is what keeps
        // "3.5" or "1.2.3" (version numbers people paste) from being resolved as hostnames.
        val tld = host.substringAfterLast('.')
        if (tld.length < 2 || !tld.all { it.isLetter() }) return false

        return Patterns.WEB_URL.matcher("http://$host").matches()
    }

    /** Registrable host for display and for grouping history rows. Empty when there isn't one. */
    fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host?.lowercase().orEmpty() }.getOrDefault("")

    /**
     * What the URL bar shows when not focused. Chrome-style: the host, minus a leading "www.",
     * because the origin is the only part that carries security meaning and a full URL just
     * pushes it off-screen on a phone.
     *
     * Hosts that look like a homograph attack are rendered as punycode instead — see [isSpoofable].
     * Without this the origin display is actively misleading, which is worse than showing nothing:
     * "аpple.com" with a Cyrillic а is visually identical to the real thing.
     */
    fun displayOrigin(url: String): String {
        val host = hostOf(url)
        if (host.isEmpty()) return url
        val safe = if (isSpoofable(host)) toPunycode(host) else host
        return safe.removePrefix("www.")
    }

    /**
     * True when a hostname mixes Unicode scripts within a single label, or mixes a non-Latin script
     * with ASCII anywhere.
     *
     * This is the cheap version of what Chrome does. Single-script non-Latin hostnames are left
     * readable, because a wholly Cyrillic or wholly CJK domain is legitimate and punycoding it
     * would make the browser unusable in those languages. What gets caught is the dangerous case:
     * scripts *mixed* inside one label, which is how a homograph is built.
     */
    fun isSpoofable(host: String): Boolean {
        if (host.all { it.code < 128 }) return false // pure ASCII, nothing to confuse

        for (label in host.split('.')) {
            if (label.isEmpty()) continue
            val scripts = label
                .filter { Character.isLetter(it) }
                .map { Character.UnicodeScript.of(it.code) }
                .filter { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }
                .toSet()
            // More than one script inside a single label is the homograph signature.
            if (scripts.size > 1) return true
            // Latin mixed with any non-Latin script in the same label.
            if (scripts.size == 1 &&
                scripts.first() != Character.UnicodeScript.LATIN &&
                label.any { it.code < 128 && Character.isLetter(it) }
            ) return true
        }
        return false
    }

    /** ASCII-compatible form of an internationalised host. Falls back to the input on failure. */
    fun toPunycode(host: String): String =
        runCatching { java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)

    /** True when the URL is served over TLS — drives the lock icon in the URL bar. */
    fun isSecure(url: String): Boolean =
        runCatching { Uri.parse(url).scheme?.lowercase() == "https" }.getOrDefault(false)
}
