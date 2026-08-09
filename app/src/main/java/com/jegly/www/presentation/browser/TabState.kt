package com.jegly.www.presentation.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Per-tab observable state. Deliberately holds no WebView reference.
 *
 * The WebView instances live in BrowserScreen's composition (see `webViews` there) because they
 * must be constructed with an Activity context — a WebView built from the application context
 * cannot show a fullscreen video, a file chooser, or a JS dialog. Keeping them out of the
 * ViewModel is what stops the ViewModel from outliving and leaking the Activity.
 *
 * Browsing state is deliberately never serialised. WebView.saveState() is not called and no
 * back/forward stack is written anywhere — not to a Bundle, not to disk. A tab that loses its
 * renderer (killed under memory pressure, or a crash on hostile content) comes back at its current
 * URL with an empty history, and a killed process comes back with no tabs at all.
 *
 * That is the intended behaviour for this browser, not an unfinished feature: serialised history is
 * browsing history, and the only copy of it that exists here lives in the encrypted database the
 * user can clear, never in a Bundle the platform may write out.
 */
class TabState(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = ""
) {
    /** Committed URL of the current page. Updated on navigation, not on every redirect hop. */
    var url by mutableStateOf(initialUrl)

    /**
     * Host of the current main-frame document, kept separately from [url] because
     * shouldInterceptRequest runs on a background thread for every subresource and must not be
     * re-parsing the URL string on each call.
     */
    var mainFrameHost: String = ""

    /**
     * [mainFrameHost] reduced to its registrable suffix, cached for the same reason the host is:
     * the third-party check runs on every subresource request and this side of the comparison only
     * changes when the main frame navigates. See [ThirdPartyPolicy.isThirdParty].
     */
    var mainFrameSuffix: String = ""

    var title by mutableStateOf("")

    /** 0..100. Drives the thin progress line under the URL bar. */
    var progress by mutableIntStateOf(0)

    var isLoading by mutableStateOf(false)

    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /**
     * Set when the page's certificate failed validation. The page is blocked either way; this only
     * decides whether the URL bar shows the broken-security state instead of a plain lock.
     */
    var hasSslError by mutableStateOf(false)

    /** Count of requests dropped by the tracker blocker, shown in the shield popup. */
    var blockedCount by mutableIntStateOf(0)

    /**
     * TLS certificate for the committed page, captured from WebView.getCertificate() when the load
     * finishes. Drives the site-info panel. Null for plain HTTP, about:blank, or a refused
     * certificate — in the refused case [hasSslError] is what carries the meaning.
     */
    var certificate by mutableStateOf<android.net.http.SslCertificate?>(null)

    /**
     * The committed page's own background colour, read from the rendered document at first paint
     * and again once the load finishes. Null whenever it can't be trusted — no page yet, a fully
     * transparent computed background (where the page is deferring to whatever is underneath rather
     * than declaring one), JavaScript disabled so there is nothing to read it with, or algorithmic
     * darkening repainting a light page dark behind a computed style that still says it's light.
     *
     * Exists so app chrome drawn against the page (the top bar's rounded corners) can match what the
     * page is actually painting there instead of a fixed theme colour, which is what makes those
     * corners read as transparent without having to slide page content underneath the bar.
     */
    var pageBackgroundColor by mutableStateOf<androidx.compose.ui.graphics.Color?>(null)

    /** Per-page JS dialog budget counter; reset on each navigation. See BrowserWebView. */
    var jsDialogCount: Int = 0

    /** elapsedRealtime at onPageStarted; used to report load duration. Monotonic, so a clock change mid-load can't skew it. */
    var loadStartedAt: Long = 0L

    /** Duration of the last completed load, in milliseconds. 0 until a page finishes. */
    var lastLoadMillis by mutableLongStateOf(0L)

    /**
     * Incremented when the renderer process dies. The host keys its WebView container on this, so a
     * bump tears down the dead view and builds a fresh one.
     *
     * A counter rather than a boolean flag: a flag would have to be reset once the rebuild happened,
     * and the only place to do that is inside composition — mutating state during composition is
     * what causes recomposition loops. A monotonic counter needs no reset.
     */
    var rebuildGeneration by mutableIntStateOf(0)

    /** What the tab strip shows before a title arrives. */
    val displayLabel: String
        get() = title.ifBlank { url.ifBlank { "New tab" } }
}
