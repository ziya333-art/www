package com.jegly.www.presentation.browser

import android.os.Bundle
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
 * [savedState] is the bridge across that boundary: WebView.saveState() output, used to rebuild a
 * tab's back/forward stack after the Activity is recreated or a background tab is evicted.
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
     * The committed page's own background colour, read from the rendered document once the load
     * finishes. Null whenever it can't be trusted — no page yet, or a fully transparent computed
     * background, where the page is deferring to whatever is underneath rather than declaring one.
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

    var savedState: Bundle? = null

    /** What the tab strip shows before a title arrives. */
    val displayLabel: String
        get() = title.ifBlank { url.ifBlank { "New tab" } }
}
