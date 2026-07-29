package com.jegly.www.presentation.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jegly.www.data.local.DomainSettingEntity
import com.jegly.www.presentation.settings.SettingsViewModel
import com.jegly.www.util.BrowserUtils
import com.jegly.www.util.UrlUtils

/**
 * Radius of the top bar's rounded bottom corners, and equally the distance the page is pulled up
 * behind the bar so those corners have real content to be transparent against. One constant on
 * purpose: if the two ever drift apart, the arcs either clip page content or expose flat colour.
 */
private val URL_BAR_CORNER_RADIUS = 20.dp

/**
 * The browser.
 *
 * WebView instances are held here rather than in BrowserViewModel: they must be constructed with
 * an Activity context (a WebView built from the application context cannot show fullscreen video,
 * a file chooser, or a JS dialog), and holding them in a ViewModel that outlives the Activity
 * would leak it. TabState carries the observable metadata across that boundary instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    navController: NavController,
    initialUrl: String? = null,
    viewModel: BrowserViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    val webViews = remember { mutableMapOf<String, WebView>() }

    // Destroy every WebView when the screen leaves composition; otherwise each closed tab keeps a
    // live Chromium renderer and its Activity reference alive.
    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    val jsEnabled by settingsViewModel.webViewJavaScript.collectAsState()
    val domStorage by settingsViewModel.webViewDomStorage.collectAsState()
    val blockTrackers by settingsViewModel.blockTrackers.collectAsState()
    val cookiePolicy by settingsViewModel.cookiePolicy.collectAsState()
    val safeBrowsing by settingsViewModel.safeBrowsing.collectAsState()
    val httpsOnly by settingsViewModel.httpsOnly.collectAsState()
    val doNotTrack by settingsViewModel.doNotTrack.collectAsState()
    val userAgentKey by settingsViewModel.userAgent.collectAsState()
    val fontSize by settingsViewModel.fontSize.collectAsState()
    val blockThirdParty by settingsViewModel.blockThirdPartyRequests.collectAsState()
    val stripTracking by settingsViewModel.stripTrackingQueries.collectAsState()
    val incognito by settingsViewModel.incognitoMode.collectAsState()
    val allowJsDialogs by settingsViewModel.allowJsDialogs.collectAsState()
    val blockAutofill by settingsViewModel.blockAutofill.collectAsState()
    val blockDownloads by settingsViewModel.blockDownloads.collectAsState()
    val forceDark by settingsViewModel.forceDark.collectAsState()
    val wideViewport by settingsViewModel.wideViewport.collectAsState()
    val displayImages by settingsViewModel.displayImages.collectAsState()
    val swipeToRefresh by settingsViewModel.swipeToRefresh.collectAsState()
    val openIntentsInNewTab by settingsViewModel.openIntentsInNewTab.collectAsState()
    val displayUnderCutouts by settingsViewModel.displayUnderCutouts.collectAsState()
    val clearOnExit by settingsViewModel.clearOnExit.collectAsState()
    val clearCookiesOnExit by settingsViewModel.clearCookies.collectAsState()
    val clearDomStorageOnExit by settingsViewModel.clearDomStorage.collectAsState()
    val clearCacheOnExit by settingsViewModel.clearCache.collectAsState()
    val clearLogcatOnExit by settingsViewModel.clearLogcat.collectAsState()

    val userAgentString = remember(userAgentKey) {
        com.jegly.www.network.UserAgentTemplate.fromKey(userAgentKey).uaString
    }
    // The reader's font-size slider becomes WebView text zoom. 16sp is the baseline = 100%.
    val textZoom = remember(fontSize) { ((fontSize / 16f) * 100f).toInt().coerceIn(50, 200) }

    val globalPrefs = BrowserWebViewPrefs(
        javaScriptEnabled = jsEnabled,
        domStorageEnabled = domStorage,
        blockTrackers = blockTrackers,
        blockThirdPartyRequests = blockThirdParty,
        stripTrackingQueries = stripTracking,
        cookiePolicy = cookiePolicy,
        safeBrowsing = safeBrowsing,
        httpsOnly = httpsOnly,
        doNotTrack = doNotTrack,
        userAgent = userAgentString,
        textZoomPercent = textZoom,
        wideViewport = wideViewport,
        displayImages = displayImages,
        incognitoMode = incognito,
        allowJsDialogs = allowJsDialogs,
        blockAutofill = blockAutofill,
        blockDownloads = blockDownloads,
        forceDark = forceDark,
        backgroundColor = MaterialTheme.colorScheme.background
    )

    val activeTabForPrefs = viewModel.activeTab

    /*
     * Per-domain override for the page currently loaded. Re-queried whenever the active tab's URL
     * changes, so navigating from a site with a rule to one without correctly falls back to globals.
     */
    val domainOverride by produceState<DomainSettingEntity?>(
        initialValue = null,
        activeTabForPrefs?.id,
        activeTabForPrefs?.url
    ) {
        val url = activeTabForPrefs?.url
        value = if (url.isNullOrBlank()) null
        else viewModel.domainSettingFor(UrlUtils.hostOf(url))
    }

    val prefs = remember(globalPrefs, domainOverride) {
        DomainSettingsResolver.apply(globalPrefs, domainOverride)
    }

    /*
     * The WebView clients capture this lambda once, at construction, and call it on every request.
     * Holding the latest prefs in a ref means a settings change or a new domain rule takes effect
     * on the live page without rebuilding the WebView and losing its back/forward stack.
     */
    val prefsRef = remember { mutableStateOf(prefs) }
    prefsRef.value = prefs
    val prefsProvider = remember { { prefsRef.value } }

    /** One-shot snackbar text for security refusals (bad cert, blocked scheme). */
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // ---- fullscreen video ----------------------------------------------------------------
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // ---- <input type=file> ---------------------------------------------------------------
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        // A null result must still be delivered, or the page's file input stays permanently stuck.
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    val callbacks = remember {
        BrowserWebViewCallbacks(
            onNewTab = { url -> viewModel.newTab(url, select = true) },
            onEnterFullscreen = { view, cb ->
                fullscreenView = view
                fullscreenCallback = cb
            },
            onExitFullscreen = {
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
            },
            onFileChooser = { callback, params ->
                pendingFileCallback?.onReceiveValue(null) // release any previous stuck request
                pendingFileCallback = callback
                runCatching { fileChooserLauncher.launch(params.createIntent()) }
                    .onFailure {
                        pendingFileCallback = null
                        callback.onReceiveValue(null)
                    }
                    .isSuccess
            },
            // Deliberately not a "proceed anyway" prompt — just a statement that it was blocked.
            onSslError = { url -> statusMessage = "Blocked ${UrlUtils.displayOrigin(url)} — invalid certificate" }
        )
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        statusMessage = null
    }

    val activeTab = viewModel.activeTab

    /*
     * External VIEW/SEND intent. With "open intents in new tab" on (the default) an incoming link
     * never replaces what the user was reading; with it off it reuses the current tab. A blank
     * current tab is always reused either way — opening a second empty-then-loaded tab beside an
     * existing empty one is just clutter.
     */
    LaunchedEffect(initialUrl) {
        val url = initialUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val tab = viewModel.activeTab
        if (tab != null && (tab.url.isBlank() || !openIntentsInNewTab)) {
            tab.url = url
            webViews[tab.id]?.loadUrl(url, navigationHeaders(doNotTrack))
        } else {
            viewModel.newTab(url, select = true)
        }
    }

    // Record history once a navigation has actually committed and produced a title.
    LaunchedEffect(activeTab?.id) {
        val tab = activeTab ?: return@LaunchedEffect
        snapshotFlow { tab.url to tab.isLoading }
            .collect { (url, loading) ->
                if (!loading && url.isNotBlank()) viewModel.recordVisit(url, tab.title)
            }
    }
    LaunchedEffect(activeTab?.id) {
        val tab = activeTab ?: return@LaunchedEffect
        snapshotFlow { tab.title }
            .collect { title -> viewModel.updateHistoryTitle(tab.url, title) }
    }

    /*
     * Clear-on-exit. Each category is independent so a user can drop cookies but keep the cache,
     * which is the point of splitting this out from the old single toggle.
     */
    DisposableEffect(clearOnExit, clearCookiesOnExit, clearDomStorageOnExit, clearCacheOnExit, clearLogcatOnExit) {
        onDispose {
            if (!clearOnExit) return@onDispose
            if (clearCookiesOnExit) {
                android.webkit.CookieManager.getInstance().apply { removeAllCookies(null); flush() }
            }
            if (clearDomStorageOnExit) {
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
            if (clearCacheOnExit) {
                webViews.values.forEach { it.clearCache(true) }
                context.cacheDir.deleteRecursively()
            }
            if (clearLogcatOnExit) {
                // Clears only this app's buffer. Best-effort: the command fails silently on
                // devices that restrict it, which is preferable to crashing on exit.
                runCatching { Runtime.getRuntime().exec(arrayOf("logcat", "-b", "all", "-c")) }
            }
        }
    }

    BackHandler(enabled = true) {
        val tab = viewModel.activeTab
        val webView = tab?.let { webViews[it.id] }
        when {
            fullscreenView != null -> {
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
            }
            viewModel.isTabSwitcherOpen -> viewModel.isTabSwitcherOpen = false
            webView?.canGoBack() == true -> webView.goBack()
            viewModel.tabs.size > 1 && tab != null -> viewModel.closeTab(tab.id)
            else -> activity?.finish()
        }
    }

    /*
     * Hide the system bars while a video is fullscreen, and put them back afterwards.
     *
     * enableEdgeToEdge() only makes the app draw *behind* the bars — the bars stay visible. Without
     * this the fullscreen video renders under a still-drawn status bar. Edge-to-edge enforcement is
     * not opt-out-able at targetSdk 37, so the controller is the only lever left.
     */
    DisposableEffect(fullscreenView != null) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreenView != null) {
            controller?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Fullscreen video takes over the whole window — no chrome, no scaffold.
    if (fullscreenView != null) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { ctx -> android.widget.FrameLayout(ctx) },
            update = { container ->
                container.setBackgroundColor(android.graphics.Color.BLACK)
                if (container.childCount == 0) {
                    fullscreenView?.let { view ->
                        (view.parent as? ViewGroup)?.removeView(view)
                        container.addView(view)
                    }
                }
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Insets are handled explicitly at the leaf level instead (the topBar Surface below pads
        // itself for the status bar; the content Box already handles the display cutout toggle).
        // Leaving Scaffold's own default contentWindowInsets in place would double-pad on top of
        // that. There is no bottomBar at all now — everything lives in topBar, per request — so
        // the WebView already needs no bottom inset either; it draws full-bleed to the bottom edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        /*
         * The topBar Surface's rounded bottom corners are transparent, and with the WebView flush
         * beneath the bar there is no page content behind them to show through — they expose this
         * container instead. Painting it in the page's own background colour is what makes those
         * arcs read as transparent: the corner and the page next to it are literally the same
         * colour, without sliding page content under the bar (which hid the top of pages that pin
         * content to the very top edge). Falls back to the theme surface before a page has
         * committed, or when the page's background isn't a usable opaque colour.
         */
        containerColor = activeTab?.pageBackgroundColor ?: MaterialTheme.colorScheme.surface,
        topBar = {
            activeTab?.let { tab ->
                // One Surface owning the background + status-bar inset for the whole bar.
                // Rounded bottom corners only (flush against the status bar on top) so the bar
                // reads as a soft, deliberate shape instead of a flat utilitarian slab.
                Surface(
                    shape = RoundedCornerShape(
                        bottomStart = URL_BAR_CORNER_RADIUS,
                        bottomEnd = URL_BAR_CORNER_RADIUS
                    ),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp
                ) {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        // Single-row toolbar: address field and every nav/tab/menu action share
                        // one line, matching Chrome/Firefox's compact layout rather than stacking
                        // a second row underneath.
                        UrlBar(
                            tab = tab,
                            tabCount = viewModel.tabs.size,
                            isBookmarked = viewModel.isBookmarked(tab.url)
                                .collectAsState(initial = false).value,
                            onNavigate = { input ->
                                when (val destination = viewModel.resolveInput(input)) {
                                    is UrlUtils.Destination.Web ->
                                        webViews[tab.id]?.loadUrl(destination.url, navigationHeaders(doNotTrack))
                                    is UrlUtils.Destination.Internal ->
                                        webViews[tab.id]?.loadUrl(destination.url)
                                    is UrlUtils.Destination.External ->
                                        BrowserUtils.openExternal(context, destination.url)
                                    is UrlUtils.Destination.Blocked ->
                                        statusMessage = destination.reason
                                    is UrlUtils.Destination.Search -> Unit // resolveInput never returns this
                                }
                            },
                            onReload = { webViews[tab.id]?.reload() },
                            onStop = { webViews[tab.id]?.stopLoading() },
                            onBack = { webViews[tab.id]?.goBack() },
                            onForward = { webViews[tab.id]?.goForward() },
                            onNewTab = { viewModel.newTab(select = true) },
                            onShowTabs = { viewModel.isTabSwitcherOpen = true },
                            onToggleBookmark = { bookmarked ->
                                viewModel.toggleBookmark(tab.url, tab.title, bookmarked)
                            },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenHistory = { navController.navigate("history") },
                            onOpenBookmarks = { navController.navigate("bookmarks") },
                            onOpenDomainSettings = { navController.navigate("domain_settings") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                /*
                 * Full padding: the page starts below the bar and nothing is hidden by it.
                 *
                 * Tried and reverted: pulling the page up by URL_BAR_CORNER_RADIUS so the bar's
                 * rounded bottom corners had page content behind them to be transparent against.
                 * It worked visually, but transparency there is only achievable by putting content
                 * under the bar, and that clipped the top of any page that pins content to the very
                 * top edge — fast.com's language/Privacy header went behind the bar. Losing real
                 * page content is a worse trade than corners that show the bar's own colour, so the
                 * arcs fall back to the Scaffold's containerColor instead.
                 */
                .padding(padding)
                /*
                 * Horizontal sides only. The top cutout/status-bar region is already covered by the
                 * topBar Surface, which pads itself for WindowInsets.statusBars — and because
                 * Scaffold's contentWindowInsets is zeroed above, nothing is marked consumed, so
                 * applying the whole displayCutout inset here re-applied its *top* component a
                 * second time underneath a bar that had already accounted for it. That produced a
                 * measured 137px dead band between the bar and the WebView and, worse, shrank the
                 * WebView by the same 137px, so pages were laid out into a viewport shorter than
                 * the space actually available to them.
                 */
                .then(
                    if (displayUnderCutouts) Modifier
                    else Modifier.windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    )
                )
        ) {
            // Keyed on the tab id: without this, Compose reuses the same AndroidView node across a
            // tab switch and the factory never re-runs, so switching tabs would leave the previous
            // tab's WebView on screen. The key forces the node to be torn down and rebuilt.
            activeTab?.let { tab ->
                // Evict a dead WebView when the generation bumps. onRenderProcessGone already
                // destroyed it, so keeping it cached would hand back an unusable instance.
                // Done in a side effect, never inline: writing state during composition loops.
                LaunchedEffect(tab.id, tab.rebuildGeneration) {
                    if (tab.rebuildGeneration > 0) webViews.remove(tab.id)
                }
                key(tab.id, tab.rebuildGeneration) {
                    val webViewContent = @Composable {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                webViews.getOrPut(tab.id) {
                                    WebView(ctx).also { webView ->
                                        /*
                                         * AndroidView hands a new child WRAP_CONTENT layout params
                                         * by default. WebView treats a non-fixed height as an
                                         * indefinite viewport and resolves percentage heights
                                         * against zero, while still reporting a correct
                                         * window.innerHeight — so `top: 50%` and `height: 100%`
                                         * silently collapse and any page that centres itself that
                                         * way renders with its top half above the screen. Measured
                                         * on Speedometer 3.1: an injected `position:absolute;
                                         * top:50%` probe resolved to 0px against a 1702px viewport.
                                         * The AOSP reference shell avoids this by hosting its
                                         * WebView in a match_parent container.
                                         */
                                        webView.layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        configureBrowserWebView(webView, tab, prefsProvider, callbacks)
                                        val restored = tab.savedState?.let { webView.restoreState(it) }
                                        if (restored == null && tab.url.isNotBlank()) {
                                            webView.loadUrl(tab.url, navigationHeaders(doNotTrack))
                                        }
                                    }
                                }.also { webView ->
                                    // Re-parent when returning to a cached tab: the WebView is
                                    // still attached to the container it was built in.
                                    (webView.parent as? ViewGroup)?.removeView(webView)
                                }
                            },
                            update = { webView ->
                                // Cheap and idempotent; picks up setting and domain-rule changes
                                // without tearing down the WebView.
                                applyWebViewPrefs(webView, prefs)
                            }
                        )
                    }

                    if (swipeToRefresh) {
                        // Deliberately its own state, not tab.isLoading directly: tab.isLoading
                        // goes true for EVERY navigation — typing a URL, tapping a link, tapping
                        // the reload button — and binding isRefreshing straight to it made the
                        // pull-down spinner animation play on all of those too, not just an actual
                        // pull gesture. Only PullToRefreshBox's own onRefresh (a real completed
                        // pull) sets this true; it's cleared once that particular load finishes.
                        var isPullRefreshing by remember(tab.id) { mutableStateOf(false) }
                        LaunchedEffect(tab.isLoading) {
                            if (!tab.isLoading) isPullRefreshing = false
                        }
                        val refreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = isPullRefreshing,
                            onRefresh = {
                                isPullRefreshing = true
                                webViews[tab.id]?.reload()
                            },
                            state = refreshState,
                            modifier = Modifier.fillMaxSize()
                        ) { webViewContent() }
                    } else {
                        webViewContent()
                    }
                }
            }

            if (viewModel.isTabSwitcherOpen) {
                TabSwitcher(
                    tabs = viewModel.tabs,
                    activeTabId = viewModel.activeTabId,
                    onSelect = { viewModel.selectTab(it) },
                    onClose = { id ->
                        webViews.remove(id)?.destroy()
                        viewModel.closeTab(id)
                    },
                    onCloseAll = {
                        webViews.values.forEach { it.destroy() }
                        webViews.clear()
                        viewModel.closeAllTabs()
                    },
                    onNewTab = { viewModel.newTab(select = true) },
                    onDismiss = { viewModel.isTabSwitcherOpen = false }
                )
            }
        }
    }
}
