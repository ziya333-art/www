package com.jegly.www.presentation.browser

/**
 * Decides whether a subresource request is "third party" relative to the page loading it.
 *
 * Honest limitation: this compares the last two labels of each host rather than the true
 * registrable domain, because computing eTLD+1 correctly requires the Public Suffix List and
 * WebView exposes no API for it. The practical consequence is under-blocking on multi-label
 * suffixes — under "co.uk", "a.co.uk" and "b.co.uk" both reduce to "co.uk" and are treated as
 * same-party. It never over-blocks a genuine first-party request, which is the failure mode that
 * would break pages, so the error is in the safe direction for usability and the unsafe direction
 * for privacy. Shipping the PSL would fix it.
 */
object ThirdPartyPolicy {

    fun isThirdParty(mainFrameHost: String?, requestHost: String?): Boolean =
        isThirdParty(
            mainFrameHost = mainFrameHost,
            mainFrameSuffix = registrableSuffix(mainFrameHost?.lowercase().orEmpty()),
            requestHost = requestHost
        )

    /**
     * Same decision, but with the page's registrable suffix supplied by the caller.
     *
     * This runs inside `shouldInterceptRequest`, i.e. once per subresource on the network thread,
     * and the page side of the comparison only changes when the main frame navigates. Passing it in
     * from [TabState.mainFrameSuffix] — computed once in `onPageStarted` — removes a `split('.')`,
     * a list filter and a `joinToString` from every single request; only the request host still has
     * to be reduced.
     */
    fun isThirdParty(mainFrameHost: String?, mainFrameSuffix: String, requestHost: String?): Boolean {
        val page = mainFrameHost?.lowercase().orEmpty()
        val request = requestHost?.lowercase().orEmpty()

        // No main-frame host yet (about:blank, first paint) — nothing to compare against, so this
        // must not block, or the very first page load would have its resources dropped.
        if (page.isEmpty() || request.isEmpty()) return false
        if (page == request) return false

        return mainFrameSuffix != registrableSuffix(request)
    }

    fun registrableSuffix(host: String): String {
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return host
        return labels.takeLast(2).joinToString(".")
    }
}
