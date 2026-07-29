package com.jegly.www.presentation.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebStorage
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.jegly.www.util.BrowserUtils
import com.jegly.www.util.LinkSanitizer
import com.jegly.www.util.UrlUtils

/**
 * Callbacks the hosting screen supplies. Kept as a single object so [configureBrowserWebView]'s
 * signature doesn't sprawl as more browser chrome lands.
 */
class BrowserWebViewCallbacks(
    val onNewTab: (url: String) -> Unit,
    val onEnterFullscreen: (view: View, callback: WebChromeClient.CustomViewCallback) -> Unit,
    val onExitFullscreen: () -> Unit,
    val onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    val onSslError: (url: String) -> Unit
)

/** Privacy/security toggles read out of settings at configure time, after domain overrides. */
data class BrowserWebViewPrefs(
    val javaScriptEnabled: Boolean,
    val domStorageEnabled: Boolean,
    val blockTrackers: Boolean,
    val blockThirdPartyRequests: Boolean,
    val stripTrackingQueries: Boolean,
    val cookiePolicy: String,
    val safeBrowsing: Boolean,
    val httpsOnly: Boolean,
    val doNotTrack: Boolean,
    val userAgent: String,
    val textZoomPercent: Int,
    val wideViewport: Boolean,
    val displayImages: Boolean,
    val incognitoMode: Boolean,
    val allowJsDialogs: Boolean,
    val blockAutofill: Boolean,
    val blockDownloads: Boolean,
    /** "system" | "on" | "off" — force-dark for web content. */
    val forceDark: String,
    /**
     * The app theme's background colour. WebView paints its own opaque canvas colour underneath
     * anything the page renders — by default black-ish on most WebView versions for a blank tab —
     * and that has nothing to do with CSS or this app's Compose theme, so a blank/new tab reads as
     * a jarring black rectangle no matter what Catppuccin/Dracula/Ptyxis palette is selected.
     * Applying this makes a blank tab (or any page slow to paint its own background) match the
     * app's theme instead.
     */
    val backgroundColor: Color
)

/**
 * Applies the browser's security posture to a WebView.
 *
 * What is intentionally *unchanged* from the reader: certificate errors and HTTP auth challenges
 * are refused outright rather than prompted. The Chromium shell's habit of letting the user click
 * through a bad certificate is the specific thing its own docs warn about, and a "proceed anyway"
 * button is the single most abused affordance in mobile browsers — so www surfaces the failure
 * (via [BrowserWebViewCallbacks.onSslError]) and stops.
 *
 * What is deliberately *loosened* from the reader, because a reader could refuse and a browser
 * cannot: popups now open tabs instead of vanishing, long-press context menus work, downloads are
 * handled, file inputs work, and video can go fullscreen.
 */
@SuppressLint("SetJavaScriptEnabled")
fun configureBrowserWebView(
    webView: WebView,
    tab: TabState,
    prefsProvider: () -> BrowserWebViewPrefs,
    callbacks: BrowserWebViewCallbacks
) = with(webView) {

    webViewClient = object : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.url = url
            // Set before any subresource request can fire, so third-party comparison has a
            // reference host from the first request onward.
            tab.mainFrameHost = UrlUtils.hostOf(url)
            tab.isLoading = true
            tab.hasSslError = false
            tab.blockedCount = 0
            tab.jsDialogCount = 0
            tab.loadStartedAt = android.os.SystemClock.elapsedRealtime()
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.url = url
            tab.mainFrameHost = UrlUtils.hostOf(url)
            tab.isLoading = false
            tab.progress = 100
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            // Captured here rather than read on demand: getCertificate() is only reliably populated
            // for the committed main-frame document.
            tab.certificate = view.certificate

            if (isDebuggable(view.context)) {
                // WebView.getScale() has no non-deprecated synchronous replacement — the
                // alternative is the async onScaleChanged callback, which doesn't fit a one-line
                // diagnostic log. Suppressed locally rather than dropping the diagnostic.
                @Suppress("DEPRECATION")
                val currentScale = view.scale
                android.util.Log.i(
                    WEBVIEW_LOG_TAG,
                    "webview size ${view.width}x${view.height} scale=$currentScale " +
                        "contentH=${view.contentHeight}"
                )
                view.evaluateJavascript(
                    "JSON.stringify({iw:innerWidth,ih:innerHeight," +
                        "ch:document.documentElement.clientHeight," +
                        "sh:document.documentElement.scrollHeight," +
                        "dpr:devicePixelRatio," +
                        "vv:(window.visualViewport?window.visualViewport.height:-1)})"
                ) { r -> android.util.Log.i(WEBVIEW_LOG_TAG, "viewport $r") }
            }

            /*
             * Read the page's painted background so the top bar's rounded corners can be drawn in
             * it. Body first, falling back to the root element, because a page that styles only
             * <html> leaves body computing as transparent — and a transparent answer here is not a
             * colour, it means "whatever is behind me", so it is rejected rather than used.
             */
            view.evaluateJavascript(
                "(function(){var b=document.body,e=document.documentElement;" +
                    "var c=b?getComputedStyle(b).backgroundColor:'';" +
                    "if(!c||c==='transparent'||c==='rgba(0, 0, 0, 0)')" +
                    "c=e?getComputedStyle(e).backgroundColor:'';return c||'';})()"
            ) { result -> tab.pageBackgroundColor = parseCssColor(result) }

            if (tab.loadStartedAt > 0L) {
                tab.lastLoadMillis = android.os.SystemClock.elapsedRealtime() - tab.loadStartedAt
                if (isDebuggable(view.context)) {
                    android.util.Log.i(
                        WEBVIEW_LOG_TAG,
                        "loaded ${tab.lastLoadMillis}ms blocked=${tab.blockedCount} url=$url"
                    )
                }
            }

            /*
             * Incognito: wipe after every page load rather than on exit, matching Privacy Browser's
             * behaviour. Cookies are cleared *after* the page has loaded, so a login can complete
             * within a single navigation but nothing survives to the next one.
             */
            if (prefsProvider().incognitoMode) {
                view.clearHistory()
                view.clearCache(true)
                CookieManager.getInstance().apply { removeAllCookies(null); flush() }
                WebStorage.getInstance().deleteAllData()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            return when (request.url.scheme?.lowercase()) {
                "https", "http" -> {
                    // Strip utm_*/fbclid/gclid before the request is made. Returning true and
                    // re-loading is the only hook WebView gives us to rewrite a main-frame URL;
                    // the clean URL re-enters here, compares equal, and proceeds normally.
                    // Main-frame only: rewriting subresource URLs here is not possible anyway, and
                    // a redirect chain would otherwise be rewritten on every hop.
                    if (prefsProvider().stripTrackingQueries && request.isForMainFrame) {
                        val clean = LinkSanitizer.sanitizeQueryOnly(target)
                        if (clean != target) {
                            view.loadUrl(clean)
                            return true
                        }
                    }
                    false
                }
                // Rendered by the WebView itself. Must never go to an external VIEW intent —
                // nothing handles data:, and dispatching it crashed the old reader.
                "data", "blob", "about", "javascript", "file", "content" -> false
                // mailto:/tel:/geo: — handed off; unknown and intent: schemes are dropped, which is
                // what stops a hostile page from launching arbitrary apps behind the user's back.
                else -> {
                    BrowserUtils.openExternal(view.context, target)
                    true
                }
            }
        }

        /**
         * Surface network-level failures. Without this, a page that fails to load its scripts
         * shows as a blank screen with no signal anywhere about why — which is exactly how a
         * blocked subresource, a DNS failure and a disabled-JS fallback all look identical.
         * Debug-only: these lines can contain full URLs, which must never reach a release logcat.
         */
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError
        ) {
            if (isDebuggable(view.context)) {
                android.util.Log.e(
                    WEBVIEW_LOG_TAG,
                    "load error ${error.errorCode} ${error.description} " +
                        "mainFrame=${request.isForMainFrame} url=${request.url}"
                )
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (isDebuggable(view.context)) {
                android.util.Log.e(
                    WEBVIEW_LOG_TAG,
                    "http ${errorResponse.statusCode} mainFrame=${request.isForMainFrame} url=${request.url}"
                )
            }
        }

        /** Refuse, always. See the class doc for why there is no "proceed anyway". */
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            if (isDebuggable(view.context)) {
                val kind = when (error.primaryError) {
                    SslError.SSL_UNTRUSTED -> "UNTRUSTED (chain not trusted by device CA store)"
                    SslError.SSL_EXPIRED -> "EXPIRED"
                    SslError.SSL_IDMISMATCH -> "HOSTNAME MISMATCH"
                    SslError.SSL_NOTYETVALID -> "NOT YET VALID"
                    SslError.SSL_DATE_INVALID -> "DATE INVALID"
                    SslError.SSL_INVALID -> "INVALID"
                    else -> "code ${error.primaryError}"
                }
                android.util.Log.e(WEBVIEW_LOG_TAG, "SSL BLOCKED $kind url=${error.url}")
            }
            handler.cancel()
            tab.hasSslError = true
            callbacks.onSslError(error.url ?: tab.url)
        }

        /**
         * The renderer process died — either it crashed on hostile content, or Android killed it
         * under memory pressure.
         *
         * Returning false (the default when this is unimplemented) lets the system kill the *app*.
         * That means any page able to crash the renderer can take the whole browser down with it,
         * losing every other tab. Returning true keeps the app alive; the dead WebView can never be
         * reused, so it is detached and the tab is marked for rebuild.
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail
        ): Boolean {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
            tab.isLoading = false
            tab.rebuildGeneration += 1
            return true
        }

        /** Never answer auth challenges — this is the classic credential-phishing surface. */
        override fun onReceivedHttpAuthRequest(
            view: WebView,
            handler: HttpAuthHandler,
            host: String,
            realm: String
        ) {
            handler.cancel()
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            // The main-frame document itself is never blocked — only its subresources.
            if (request.isForMainFrame) return null

            val requestHost = request.url.host

            val current = prefsProvider()

            if (current.blockThirdPartyRequests &&
                ThirdPartyPolicy.isThirdParty(tab.mainFrameHost, requestHost)
            ) {
                tab.blockedCount += 1
                return TrackerBlocker.blockedResponse()
            }

            if (current.blockTrackers && TrackerBlocker.isBlocked(requestHost)) {
                tab.blockedCount += 1
                return TrackerBlocker.blockedResponse()
            }

            return null
        }
    }

    webChromeClient = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
            tab.isLoading = newProgress < 100
        }

        /** Page console output. Debug-only — pages can log arbitrary user content. */
        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
            // No `view` parameter on this callback — use the WebView this client is attached to.
            if (isDebuggable(webView.context)) {
                android.util.Log.d(
                    WEBVIEW_LOG_TAG,
                    "console ${consoleMessage.messageLevel()} " +
                        "${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                )
            }
            return true
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            tab.title = title.orEmpty()
        }

        /**
         * The reader returned false here, silently killing every `target="_blank"` link. A browser
         * has to honour them, so the request becomes a new tab.
         *
         * `isUserGesture` is the popup-blocker: window.open() fired from a timer or on page load
         * has no gesture behind it and is dropped, while a real tap opens a tab.
         */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            if (!isUserGesture) return false

            // The target URL isn't on the message; it arrives at a transient WebView which reports
            // it through shouldOverrideUrlLoading. Route that first navigation into a real tab.
            val hrefHolder = WebView(view.context)
            hrefHolder.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    callbacks.onNewTab(request.url.toString())
                    v.destroy()
                    return true
                }
            }
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            transport.webView = hrefHolder
            resultMsg.sendToTarget()
            return true
        }

        /**
         * Denied outright: CAMERA and RECORD_AUDIO are not declared in the manifest, so www cannot
         * grant them even if it wanted to. This is a positioning choice — it breaks video calls and
         * camera-based site features, and reversing it means declaring those permissions and adding
         * a per-origin prompt, not just changing this method.
         */
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        /** Geolocation is off at the settings level too; deny without prompting or remembering. */
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: android.webkit.GeolocationPermissions.Callback?
        ) {
            callback?.invoke(origin, false, false)
        }

        /*
         * JavaScript dialog handling.
         *
         * Unimplemented, these fall through to WebView's stock dialogs, and a page running
         * `while(true) alert()` locks the browser with no way out but force-stop. Each page load
         * gets a small budget; past it, dialogs are auto-dismissed for the rest of that page.
         *
         * onJsPrompt is refused outright regardless of budget: a prompt is a text field rendered by
         * the browser rather than the page, which makes it an unusually convincing place to ask for
         * a password, and almost nothing legitimate still uses it.
         */
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult
        ): Boolean = handleJsDialog(result)

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult
        ): Boolean = handleJsDialog(result)

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: android.webkit.JsPromptResult
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsBeforeUnload(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult
        ): Boolean = handleJsDialog(result)

        /** Returns true once the budget is spent, meaning "handled" — i.e. silently dismissed. */
        private fun handleJsDialog(result: android.webkit.JsResult): Boolean {
            if (!prefsProvider().allowJsDialogs) {
                result.cancel()
                return true
            }
            tab.jsDialogCount += 1
            if (tab.jsDialogCount > MAX_JS_DIALOGS_PER_PAGE) {
                result.cancel()
                return true
            }
            return false // let WebView show its standard dialog
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            callbacks.onEnterFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            callbacks.onExitFullscreen()
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean = callbacks.onFileChooser(filePathCallback, fileChooserParams)
    }

    setDownloadListener(makeDownloadListener(context, prefsProvider))

    applyWebViewPrefs(webView, prefsProvider())

    // The reader suppressed long-press to stop text selection on articles. A browser needs it for
    // copy-link / open-in-new-tab / save-image, so the suppression is gone.
    isLongClickable = true
}

/**
 * The half of configuration that is safe to re-apply to a live WebView.
 *
 * Split out from [configureBrowserWebView] so the hosting AndroidView's update block can call it
 * when a setting changes or a per-domain override kicks in. Re-attaching the clients on every
 * recomposition would be wrong — it would drop in-flight callbacks — but re-applying WebSettings
 * is cheap and idempotent.
 */
fun applyWebViewPrefs(webView: WebView, prefs: BrowserWebViewPrefs) = with(webView) {
    /*
     * Autofill exposure. IMPORTANT_FOR_AUTOFILL_NO on its own is not enough — the flag is not
     * inherited, and the fields autofill actually reads are the WebView's virtual descendants, so
     * the EXCLUDE_DESCENDANTS variant is the one that closes it.
     */
    importantForAutofill = if (prefs.blockAutofill) {
        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    } else {
        View.IMPORTANT_FOR_AUTOFILL_AUTO
    }

    if (isDebuggable(webView.context)) {
        android.util.Log.i(
            WEBVIEW_LOG_TAG,
            "applyPrefs js=${prefs.javaScriptEnabled} dom=${prefs.domStorageEnabled} " +
                "images=${prefs.displayImages} 3p=${prefs.blockThirdPartyRequests} " +
                "trackers=${prefs.blockTrackers} wideViewport=${prefs.wideViewport} " +
                "textZoom=${prefs.textZoomPercent} ua='${prefs.userAgent.take(30)}'"
        )
    }
    // See BrowserWebViewPrefs.backgroundColor's doc comment: this is the WebView's own canvas
    // colour, unrelated to CSS, and it's what a blank tab actually shows before any page paints.
    setBackgroundColor(prefs.backgroundColor.toArgb())

    with(settings) {
        javaScriptEnabled = prefs.javaScriptEnabled
        domStorageEnabled = prefs.domStorageEnabled
        allowFileAccess = false
        allowContentAccess = false
        setGeolocationEnabled(false)
        setSafeBrowsingEnabled(prefs.safeBrowsing)
        mixedContentMode =
            if (prefs.httpsOnly) WebSettings.MIXED_CONTENT_NEVER_ALLOW
            else WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        useWideViewPort = prefs.wideViewport
        /*
         * Unconditionally on, matching the stock WebView shell (getLoadWithOverviewMode : true in
         * its own settings dump). This used to track prefs.wideViewport, which meant turning Wide
         * Viewport off ALSO disabled zoom-to-fit — and without zoom-to-fit the layout viewport and
         * the visual viewport diverge instead of the page being scaled down to fit the window.
         * Measured on Speedometer 3.1: innerHeight 1254 laid out into a visual viewport of only
         * 824, i.e. a third of the page rendered off-screen with no way to scroll to it. Overview
         * mode is what makes "not wide" mean "scaled to fit" rather than "silently clipped".
         */
        loadWithOverviewMode = true
        /*
         * Confirmed against the AOSP reference WebView shell (system_webview_shell)'s own source:
         * it sets this explicitly in initializeSettings() alongside the two lines above, and we
         * hadn't been — meaning we were running on whatever WebView's own unstated default is,
         * not on the same layout algorithm the shell uses. TEXT_AUTOSIZING is what boosts font
         * size for readability on a wide/desktop-declared layout and is designed to pair with
         * useWideViewPort + loadWithOverviewMode specifically, which is exactly our combination
         * here. This was the concrete, sourced difference behind a real-device truncation report
         * on a page with a fixed desktop viewport (Speedometer 3.1's `width=850` meta tag) that
         * rendered correctly in the reference shell but not in this app before this line existed.
         */
        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        // Off means images never leave the network — a bandwidth and tracking-pixel control, not
        // just a display toggle.
        loadsImagesAutomatically = prefs.displayImages
        blockNetworkImage = !prefs.displayImages
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        // Autoplay stays gated on a user gesture. This is a deliberate divergence from Chrome.
        mediaPlaybackRequiresUserGesture = true
        textZoom = prefs.textZoomPercent
        setSupportMultipleWindows(true)
        if (prefs.userAgent.isNotEmpty()) userAgentString = prefs.userAgent
    }

    applyCookiePolicy(this, prefs.cookiePolicy)
    applyForceDark(this, prefs.forceDark)

    /*
     * useWideViewPort/textZoom/forceDark/userAgent/javaScriptEnabled/domStorageEnabled only affect
     * how a page is laid out — WebSettings takes the new value immediately, but the page already
     * on screen was laid out under the old value and does not repaint itself just because a
     * setting object changed underneath it. Without forcing a reload here, toggling e.g. Wide
     * Viewport in Settings visibly does nothing until the user separately navigates or hits
     * refresh, which reads as the toggle being broken rather than merely needing a reload.
     */
    val reflowSignature = listOf(
        prefs.wideViewport,
        prefs.textZoomPercent,
        prefs.forceDark,
        prefs.userAgent,
        prefs.javaScriptEnabled,
        prefs.domStorageEnabled
    ).toString()
    val previousSignature = tag as? String
    if (previousSignature != null && previousSignature != reflowSignature && !url.isNullOrBlank()) {
        reload()
    }
    tag = reflowSignature
}

/**
 * "first_party" is the default: a site's own login and consent cookies persist, cross-site
 * tracking cookies do not. Blocking cookies entirely makes consent walls unclickable, which in
 * practice pushes users to turn protection off altogether.
 */
private fun applyCookiePolicy(webView: WebView, policy: String) {
    val manager = CookieManager.getInstance()
    when (policy) {
        "block" -> {
            manager.setAcceptCookie(false)
            manager.setAcceptThirdPartyCookies(webView, false)
        }
        "all" -> {
            manager.setAcceptCookie(true)
            manager.setAcceptThirdPartyCookies(webView, true)
        }
        else -> {
            manager.setAcceptCookie(true)
            manager.setAcceptThirdPartyCookies(webView, false)
        }
    }
}

/**
 * Force-dark for web content.
 *
 * Uses androidx.webkit's algorithmic darkening, which is the only supported path on modern
 * WebView — the old `setForceDark` was deprecated and is a no-op on newer providers. Every call is
 * gated on a runtime feature check because the engine version is whatever the device happens to
 * have; on a provider without the feature this silently does nothing, which is the correct
 * degradation.
 *
 * "system" defers to the app/system dark setting. "on" darkens regardless. "off" never darkens —
 * worth having because algorithmic darkening mangles sites that already ship a dark theme.
 */
private fun applyForceDark(webView: WebView, mode: String) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
    val allow = when (mode) {
        "on" -> true
        "off" -> false
        else -> {
            val uiMode = webView.context.resources.configuration.uiMode
            (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
    runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, allow) }
}

/**
 * Downloads go through the system DownloadManager: it gets notification UI, retry, and pause for
 * free, and writing to the public Downloads collection needs no storage permission.
 *
 * Cookies are forwarded so downloads behind a login work; the UA is forwarded so servers that
 * vary on it return the same file the page linked to.
 *
 * Reads [prefsProvider] on every download attempt rather than once at construction — same reason
 * the request-blocking checks elsewhere do — so toggling "Block Downloads" in Settings takes effect
 * on the live tab without rebuilding the WebView.
 */
private fun makeDownloadListener(context: Context, prefsProvider: () -> BrowserWebViewPrefs) =
    DownloadListener { url, contentDisposition, mimeType, _, _ ->
        val prefs = prefsProvider()
        if (prefs.blockDownloads) {
            Toast.makeText(context, "Download blocked", Toast.LENGTH_SHORT).show()
            return@DownloadListener
        }
        // Only ever hand http(s) to DownloadManager — it will happily be pointed at other schemes.
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@DownloadListener

        val userAgent = prefs.userAgent
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent.ifEmpty { WebSettings.getDefaultUserAgent(context) })
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setTitle(fileName)
            setDescription(UrlUtils.displayOrigin(url))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }.onSuccess {
            Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

/**
 * How many JavaScript dialogs one page load may show before the rest are dismissed silently.
 * Three is enough for legitimate confirm-then-alert flows and far short of a usable lock-up.
 */
private const val MAX_JS_DIALOGS_PER_PAGE = 3

/** Logcat tag for the debug-only WebView diagnostics above. */
private const val WEBVIEW_LOG_TAG = "wwwWebView"

/**
 * True only for a debuggable build. Gates the WebView diagnostics above, which can contain full
 * URLs and page console output — neither belongs in a release logcat, where any app holding
 * READ_LOGS could read a user's browsing.
 */
/**
 * Turns the `rgb()` / `rgba()` string that getComputedStyle always normalises to into a Compose
 * colour. Returns null for anything not fully opaque — including the transparent default — since a
 * partly transparent page background is not a colour that chrome can be painted in.
 *
 * The value arrives via evaluateJavascript, so it is a JSON string literal wrapped in quotes and
 * comes from the page. It is only ever parsed into four numbers here and never executed.
 */
private fun parseCssColor(raw: String?): androidx.compose.ui.graphics.Color? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val match = Regex("rgba?\\(([^)]*)\\)").find(raw.trim('"')) ?: return null
    val parts = match.groupValues[1].split(',').map { it.trim() }
    if (parts.size < 3) return null
    val r = parts[0].toFloatOrNull()?.toInt() ?: return null
    val g = parts[1].toFloatOrNull()?.toInt() ?: return null
    val b = parts[2].toFloatOrNull()?.toInt() ?: return null
    val a = if (parts.size >= 4) parts[3].toFloatOrNull() ?: 1f else 1f
    if (a < 0.99f) return null
    if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
    return androidx.compose.ui.graphics.Color(r, g, b)
}

private fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

/** Main-frame privacy headers. WebView only lets us attach these to top-level navigations. */
fun navigationHeaders(doNotTrack: Boolean): Map<String, String> =
    if (doNotTrack) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()
