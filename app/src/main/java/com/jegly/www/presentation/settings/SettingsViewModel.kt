package com.jegly.www.presentation.settings

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import com.jegly.www.data.local.BookmarkDao
import com.jegly.www.data.local.HistoryDao
import com.jegly.www.network.DohProvider
import com.jegly.www.security.AdvancedProtectionGate
import com.jegly.www.security.BiometricAuthManager
import com.jegly.www.security.EncryptionManager
import com.jegly.www.security.PassphraseGate
import com.jegly.www.security.PasscodeManager
import com.jegly.www.util.SearchEngine
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The DAOs are injected as [Lazy] deliberately, and must stay that way.
 *
 * WwwTheme resolves this ViewModel via hiltViewModel(), and MainActivity renders WwwTheme in its
 * biometric-reset and auth-failure dialogs — both of which run *before* PassphraseGate is opened.
 * A direct DAO injection would construct AppDatabase during that composition, which blocks on
 * `runBlocking { gate.await() }` on the main thread and deadlocks into a blank screen with no
 * way out. Lazy defers the database open until a clear* method actually calls .get(), which only
 * happens post-auth and off the main thread.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val encryptionManager: EncryptionManager,
    private val passphraseGate: PassphraseGate,
    private val biometricAuthManager: BiometricAuthManager,
    private val passcodeManager: PasscodeManager,
    private val advancedProtectionGate: AdvancedProtectionGate,
    private val historyDao: Lazy<HistoryDao>,
    private val bookmarkDao: Lazy<BookmarkDao>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * True when Android Advanced Protection Mode is on for the device. This app cannot turn AAPM
     * on or off — see AdvancedProtectionGate — only react to it, by forcing specific settings
     * below to report true regardless of the user's stored preference for as long as it's active.
     */
    val advancedProtectionEnabled: StateFlow<Boolean> = advancedProtectionGate.enabled

    /**
     * Combines a stored preference with AAPM state rather than overwriting the stored value when
     * AAPM turns on: if AAPM later turns back off, this restores whatever the user had chosen
     * before, instead of leaving every locked toggle stuck on.
     */
    private fun lockedByAdvancedProtection(base: StateFlow<Boolean>): StateFlow<Boolean> =
        combine(base, advancedProtectionGate.enabled) { value, locked -> value || locked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, base.value)

    private val _dohProvider = MutableStateFlow(DohProvider.fromKey(encryptionManager.getString("doh_provider")))
    val dohProvider: StateFlow<DohProvider> = _dohProvider.asStateFlow()

    fun setDohProvider(provider: DohProvider) {
        encryptionManager.saveString("doh_provider", provider.key)
        _dohProvider.value = provider
    }

    private val _useBiometrics = MutableStateFlow(encryptionManager.getBoolean("use_biometrics", false))
    val useBiometrics: StateFlow<Boolean> = _useBiometrics.asStateFlow()

    private val _fontSize = MutableStateFlow(encryptionManager.getFloat("font_size", 16f))
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _fontFamily = MutableStateFlow(encryptionManager.getString("font_family") ?: "Default")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _screenshotProtection = MutableStateFlow(encryptionManager.getBoolean("screenshot_protection", true))
    val screenshotProtection: StateFlow<Boolean> = lockedByAdvancedProtection(_screenshotProtection)

    private val _themeMode = MutableStateFlow(encryptionManager.getString("theme_mode") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        encryptionManager.saveString("theme_mode", mode)
        _themeMode.value = mode
    }

    private val _catppuccinAccent = MutableStateFlow(encryptionManager.getString("catppuccin_accent") ?: "mauve")
    val catppuccinAccent: StateFlow<String> = _catppuccinAccent.asStateFlow()

    fun setCatppuccinAccent(key: String) {
        encryptionManager.saveString("catppuccin_accent", key)
        _catppuccinAccent.value = key
    }

    private val _catppuccinFlavor = MutableStateFlow(encryptionManager.getString("catppuccin_flavor") ?: "mocha")
    val catppuccinFlavor: StateFlow<String> = _catppuccinFlavor.asStateFlow()

    fun setCatppuccinFlavor(key: String) {
        encryptionManager.saveString("catppuccin_flavor", key)
        _catppuccinFlavor.value = key
    }

    private val _ptyxisPalette = MutableStateFlow(encryptionManager.getString("ptyxis_palette") ?: "nord")
    val ptyxisPalette: StateFlow<String> = _ptyxisPalette.asStateFlow()

    fun setPtyxisPalette(key: String) {
        encryptionManager.saveString("ptyxis_palette", key)
        _ptyxisPalette.value = key
    }

    private val _draculaAccent = MutableStateFlow(encryptionManager.getString("dracula_accent") ?: "purple")
    val draculaAccent: StateFlow<String> = _draculaAccent.asStateFlow()

    fun setDraculaAccent(key: String) {
        encryptionManager.saveString("dracula_accent", key)
        _draculaAccent.value = key
    }

    private val _userAgent = MutableStateFlow(encryptionManager.getString("user_agent") ?: "default")
    val userAgent: StateFlow<String> = _userAgent.asStateFlow()

    fun setUserAgent(key: String) {
        encryptionManager.saveString("user_agent", key)
        _userAgent.value = key
    }

    // --- WebView / Browser privacy ---
    // Cookie policy: "block" | "first_party" | "all". Default first-party only so consent
    // banners and logins work on the page's own domain while cross-site tracking cookies stay blocked.
    private val _cookiePolicy = MutableStateFlow(encryptionManager.getString("cookie_policy") ?: "first_party")
    val cookiePolicy: StateFlow<String> = _cookiePolicy.asStateFlow()

    fun setCookiePolicy(policy: String) {
        encryptionManager.saveString("cookie_policy", policy)
        _cookiePolicy.value = policy
    }

    private val _blockTrackers = MutableStateFlow(encryptionManager.getBoolean("block_trackers", true))
    val blockTrackers: StateFlow<Boolean> = _blockTrackers.asStateFlow()

    fun setBlockTrackers(enabled: Boolean) {
        encryptionManager.saveBoolean("block_trackers", enabled)
        _blockTrackers.value = enabled
    }

    private val _webViewJavaScript = MutableStateFlow(encryptionManager.getBoolean("webview_javascript", true))
    val webViewJavaScript: StateFlow<Boolean> = _webViewJavaScript.asStateFlow()

    fun setWebViewJavaScript(enabled: Boolean) {
        encryptionManager.saveBoolean("webview_javascript", enabled)
        _webViewJavaScript.value = enabled
    }

    private val _webViewDomStorage = MutableStateFlow(encryptionManager.getBoolean("webview_dom_storage", true))
    val webViewDomStorage: StateFlow<Boolean> = _webViewDomStorage.asStateFlow()

    fun setWebViewDomStorage(enabled: Boolean) {
        encryptionManager.saveBoolean("webview_dom_storage", enabled)
        _webViewDomStorage.value = enabled
    }

    /**
     * Master switch for clear-on-exit. When on, the individual clear_* toggles below decide what
     * gets wiped; when off, nothing is cleared on exit regardless of their values. Keeping the
     * sub-toggles' own values intact while the master is off means turning it back on restores the
     * user's previous selection instead of silently resetting to "everything".
     */
    private val _clearOnExit = MutableStateFlow(encryptionManager.getBoolean("clear_everything", false))
    val clearOnExit: StateFlow<Boolean> = _clearOnExit.asStateFlow()

    fun setClearOnExit(enabled: Boolean) {
        encryptionManager.saveBoolean("clear_everything", enabled)
        _clearOnExit.value = enabled
    }

    private val _clearCookies = MutableStateFlow(encryptionManager.getBoolean("clear_cookies", true))
    val clearCookies: StateFlow<Boolean> = _clearCookies.asStateFlow()

    fun setClearCookies(enabled: Boolean) {
        encryptionManager.saveBoolean("clear_cookies", enabled)
        _clearCookies.value = enabled
    }

    private val _clearDomStorage = MutableStateFlow(encryptionManager.getBoolean("clear_dom_storage", true))
    val clearDomStorage: StateFlow<Boolean> = _clearDomStorage.asStateFlow()

    fun setClearDomStorage(enabled: Boolean) {
        encryptionManager.saveBoolean("clear_dom_storage", enabled)
        _clearDomStorage.value = enabled
    }

    private val _clearCache = MutableStateFlow(encryptionManager.getBoolean("clear_cache", true))
    val clearCache: StateFlow<Boolean> = _clearCache.asStateFlow()

    fun setClearCache(enabled: Boolean) {
        encryptionManager.saveBoolean("clear_cache", enabled)
        _clearCache.value = enabled
    }

    /**
     * Logcat can contain URLs and console output from pages. It is readable by anything holding
     * READ_LOGS and survives an app data wipe, so clearing it is a real gap-closer rather than
     * theatre — but it only clears this app's own buffer.
     */
    private val _clearLogcat = MutableStateFlow(encryptionManager.getBoolean("clear_logcat", true))
    val clearLogcat: StateFlow<Boolean> = _clearLogcat.asStateFlow()

    fun setClearLogcat(enabled: Boolean) {
        encryptionManager.saveBoolean("clear_logcat", enabled)
        _clearLogcat.value = enabled
    }

    /** Blocks every subresource whose registrable domain differs from the page's. Aggressive. */
    private val _blockThirdPartyRequests =
        MutableStateFlow(encryptionManager.getBoolean("block_all_third_party_requests", false))
    val blockThirdPartyRequests: StateFlow<Boolean> = _blockThirdPartyRequests.asStateFlow()

    fun setBlockThirdPartyRequests(enabled: Boolean) {
        encryptionManager.saveBoolean("block_all_third_party_requests", enabled)
        _blockThirdPartyRequests.value = enabled
    }

    private val _stripTrackingQueries =
        MutableStateFlow(encryptionManager.getBoolean("tracking_queries", true))
    val stripTrackingQueries: StateFlow<Boolean> = _stripTrackingQueries.asStateFlow()

    fun setStripTrackingQueries(enabled: Boolean) {
        encryptionManager.saveBoolean("tracking_queries", enabled)
        _stripTrackingQueries.value = enabled
    }

    /**
     * Stock WebView JS dialogs. Even when on, BrowserWebView caps how many one page load may show,
     * so turning this off is about refusing them entirely rather than about spam.
     */
    private val _allowJsDialogs = MutableStateFlow(encryptionManager.getBoolean("allow_js_dialogs", true))
    val allowJsDialogs: StateFlow<Boolean> = _allowJsDialogs.asStateFlow()

    fun setAllowJsDialogs(enabled: Boolean) {
        encryptionManager.saveBoolean("allow_js_dialogs", enabled)
        _allowJsDialogs.value = enabled
    }

    /**
     * Hides page content from Android's Autofill framework. Without this, any autofill service the
     * user has enabled — a password manager, or anything else holding that role — is offered the
     * contents of form fields on every page the browser renders.
     */
    private val _blockAutofill = MutableStateFlow(encryptionManager.getBoolean("block_autofill", true))
    val blockAutofill: StateFlow<Boolean> = _blockAutofill.asStateFlow()

    fun setBlockAutofill(enabled: Boolean) {
        encryptionManager.saveBoolean("block_autofill", enabled)
        _blockAutofill.value = enabled
    }

    /**
     * Refuses every download attempt outright rather than routing it to DownloadManager. Matches
     * the AOSP reference WebView shell, which has no download path implemented at all — this is
     * the same posture as an explicit toggle instead.
     */
    private val _blockDownloads = MutableStateFlow(encryptionManager.getBoolean("block_downloads", false))
    val blockDownloads: StateFlow<Boolean> = lockedByAdvancedProtection(_blockDownloads)

    fun setBlockDownloads(enabled: Boolean) {
        encryptionManager.saveBoolean("block_downloads", enabled)
        _blockDownloads.value = enabled
    }

    private val _incognitoMode = MutableStateFlow(encryptionManager.getBoolean("incognito_mode", false))
    val incognitoMode: StateFlow<Boolean> = _incognitoMode.asStateFlow()

    fun setIncognitoMode(enabled: Boolean) {
        encryptionManager.saveBoolean("incognito_mode", enabled)
        _incognitoMode.value = enabled
    }

    /** "system" | "on" | "off" */
    private val _forceDark = MutableStateFlow(encryptionManager.getString("webview_theme") ?: "system")
    val forceDark: StateFlow<String> = _forceDark.asStateFlow()

    fun setForceDark(mode: String) {
        encryptionManager.saveString("webview_theme", mode)
        _forceDark.value = mode
    }

    private val _wideViewport = MutableStateFlow(encryptionManager.getBoolean("wide_viewport", true))
    val wideViewport: StateFlow<Boolean> = _wideViewport.asStateFlow()

    fun setWideViewport(enabled: Boolean) {
        encryptionManager.saveBoolean("wide_viewport", enabled)
        _wideViewport.value = enabled
    }

    private val _displayImages = MutableStateFlow(encryptionManager.getBoolean("display_webpage_images", true))
    val displayImages: StateFlow<Boolean> = _displayImages.asStateFlow()

    fun setDisplayImages(enabled: Boolean) {
        encryptionManager.saveBoolean("display_webpage_images", enabled)
        _displayImages.value = enabled
    }

    private val _swipeToRefresh = MutableStateFlow(encryptionManager.getBoolean("swipe_to_refresh", true))
    val swipeToRefresh: StateFlow<Boolean> = _swipeToRefresh.asStateFlow()

    fun setSwipeToRefresh(enabled: Boolean) {
        encryptionManager.saveBoolean("swipe_to_refresh", enabled)
        _swipeToRefresh.value = enabled
    }

    private val _openIntentsInNewTab =
        MutableStateFlow(encryptionManager.getBoolean("open_intents_in_new_tab", true))
    val openIntentsInNewTab: StateFlow<Boolean> = _openIntentsInNewTab.asStateFlow()

    fun setOpenIntentsInNewTab(enabled: Boolean) {
        encryptionManager.saveBoolean("open_intents_in_new_tab", enabled)
        _openIntentsInNewTab.value = enabled
    }

    /**
     * Off by default. Letting page content paint into the cutout area puts attacker-controlled
     * pixels next to the status bar, which is the wrong default for a browser that is trying to
     * keep the origin display trustworthy.
     */
    private val _displayUnderCutouts =
        MutableStateFlow(encryptionManager.getBoolean("display_under_cutouts", false))
    val displayUnderCutouts: StateFlow<Boolean> = _displayUnderCutouts.asStateFlow()

    fun setDisplayUnderCutouts(enabled: Boolean) {
        encryptionManager.saveBoolean("display_under_cutouts", enabled)
        _displayUnderCutouts.value = enabled
    }

    private val _doNotTrack = MutableStateFlow(encryptionManager.getBoolean("do_not_track", true))
    val doNotTrack: StateFlow<Boolean> = _doNotTrack.asStateFlow()

    fun setDoNotTrack(enabled: Boolean) {
        encryptionManager.saveBoolean("do_not_track", enabled)
        _doNotTrack.value = enabled
    }

    private val _safeBrowsing = MutableStateFlow(encryptionManager.getBoolean("safe_browsing", true))
    val safeBrowsing: StateFlow<Boolean> = lockedByAdvancedProtection(_safeBrowsing)

    fun setSafeBrowsing(enabled: Boolean) {
        encryptionManager.saveBoolean("safe_browsing", enabled)
        _safeBrowsing.value = enabled
    }

    private val _httpsOnly = MutableStateFlow(encryptionManager.getBoolean("https_only", true))
    val httpsOnly: StateFlow<Boolean> = lockedByAdvancedProtection(_httpsOnly)

    fun setHttpsOnly(enabled: Boolean) {
        encryptionManager.saveBoolean("https_only", enabled)
        _httpsOnly.value = enabled
    }

    private val _searchEngine = MutableStateFlow(SearchEngine.fromKey(encryptionManager.getString("search_engine")))
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

    fun setSearchEngine(engine: SearchEngine) {
        encryptionManager.saveString("search_engine", engine.key)
        _searchEngine.value = engine
    }

    private val _homepage = MutableStateFlow(encryptionManager.getString("homepage") ?: DEFAULT_HOMEPAGE)
    val homepage: StateFlow<String> = _homepage.asStateFlow()

    fun setHomepage(url: String) {
        val value = url.ifBlank { DEFAULT_HOMEPAGE }
        encryptionManager.saveString("homepage", value)
        _homepage.value = value
    }

    /**
     * When off, nothing is written to the history table at all — this is the switch, not a
     * retention setting. Existing rows are left alone; clearing them is [clearHistory].
     */
    private val _saveHistory = MutableStateFlow(encryptionManager.getBoolean("save_history", true))
    val saveHistory: StateFlow<Boolean> = _saveHistory.asStateFlow()

    fun setSaveHistory(enabled: Boolean) {
        encryptionManager.saveBoolean("save_history", enabled)
        _saveHistory.value = enabled
    }

    private val _passcodeKind = MutableStateFlow(passcodeManager.kind())
    val passcodeKind: StateFlow<PasscodeManager.Kind> = _passcodeKind.asStateFlow()

    /**
     * Wraps the database passphrase under a new PIN/password/pattern. Runs off the main thread —
     * PBKDF2 at 210k iterations takes noticeable time by design.
     */
    fun setPasscode(kind: PasscodeManager.Kind, passcode: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                val plain = passphraseGate.loadOrCreatePlainPassphrase()
                // enable() zeroes the CharArray it is given, so the verification below needs its
                // own copy — taken before the wrap consumes the original.
                val verifyCopy = passcode.copyOf()
                val wrapped = passcodeManager.enable(kind, passcode, plain)
                if (!wrapped) {
                    verifyCopy.fill(' ')
                    return@withContext false
                }

                /*
                 * Read the wrap straight back before deleting the plain copy. Without this the
                 * passcode would be only a UI gate: the database key would still be sitting in
                 * EncryptedSharedPreferences for anyone able to read the app's data directory.
                 * Deleting it is what makes the passcode a real factor — and verifying first is
                 * what stops a silently-failed wrap from destroying the only usable key.
                 */
                val roundTrip = passcodeManager.unwrap(verifyCopy)
                if (roundTrip != null && roundTrip.contentEquals(plain)) {
                    passphraseGate.removePlain()
                    true
                } else {
                    // Wrap unusable — leave the plain copy alone and undo, rather than lock the
                    // user out of their own database.
                    passcodeManager.disable()
                    false
                }
            }
            if (ok) _passcodeKind.value = passcodeManager.kind()
            onResult(ok)
        }
    }

    /**
     * Turning the passcode off has to put the plain copy back, or the next launch would find
     * neither a passcode wrap nor a plain key and mint a fresh passphrase — orphaning the database.
     * The passphrase is already unwrapped in memory at this point because the app is unlocked.
     */
    fun clearPasscode() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                if (!passphraseGate.hasPlainPassphrase()) {
                    val current = runCatching { passphraseGate.await() }.getOrNull()
                    if (current != null) passphraseGate.restorePlain(current)
                }
                passcodeManager.disable()
            }
            _passcodeKind.value = PasscodeManager.Kind.NONE
        }
    }

    val keystoreSecurityLevel: String = encryptionManager.getKeystoreSecurityLevel()

    fun refreshSettings() {
        _useBiometrics.value = encryptionManager.getBoolean("use_biometrics", false)
        _fontSize.value = encryptionManager.getFloat("font_size", 16f)
        _fontFamily.value = encryptionManager.getString("font_family") ?: "Default"
        _screenshotProtection.value = encryptionManager.getBoolean("screenshot_protection", true)
        _themeMode.value = encryptionManager.getString("theme_mode") ?: "system"
        _catppuccinAccent.value = encryptionManager.getString("catppuccin_accent") ?: "mauve"
        _catppuccinFlavor.value = encryptionManager.getString("catppuccin_flavor") ?: "mocha"
        _draculaAccent.value = encryptionManager.getString("dracula_accent") ?: "purple"
        _ptyxisPalette.value = encryptionManager.getString("ptyxis_palette") ?: "nord"
        _userAgent.value = encryptionManager.getString("user_agent") ?: "default"
        _cookiePolicy.value = encryptionManager.getString("cookie_policy") ?: "first_party"
        _blockTrackers.value = encryptionManager.getBoolean("block_trackers", true)
        _webViewJavaScript.value = encryptionManager.getBoolean("webview_javascript", true)
        _webViewDomStorage.value = encryptionManager.getBoolean("webview_dom_storage", true)
        _clearOnExit.value = encryptionManager.getBoolean("clear_everything", false)
        _clearCookies.value = encryptionManager.getBoolean("clear_cookies", true)
        _clearDomStorage.value = encryptionManager.getBoolean("clear_dom_storage", true)
        _clearCache.value = encryptionManager.getBoolean("clear_cache", true)
        _clearLogcat.value = encryptionManager.getBoolean("clear_logcat", true)
        _blockThirdPartyRequests.value = encryptionManager.getBoolean("block_all_third_party_requests", false)
        _stripTrackingQueries.value = encryptionManager.getBoolean("tracking_queries", true)
        _incognitoMode.value = encryptionManager.getBoolean("incognito_mode", false)
        _blockAutofill.value = encryptionManager.getBoolean("block_autofill", true)
        _blockDownloads.value = encryptionManager.getBoolean("block_downloads", false)
        _allowJsDialogs.value = encryptionManager.getBoolean("allow_js_dialogs", true)
        _passcodeKind.value = passcodeManager.kind()
        _forceDark.value = encryptionManager.getString("webview_theme") ?: "system"
        _wideViewport.value = encryptionManager.getBoolean("wide_viewport", true)
        _displayImages.value = encryptionManager.getBoolean("display_webpage_images", true)
        _swipeToRefresh.value = encryptionManager.getBoolean("swipe_to_refresh", true)
        _openIntentsInNewTab.value = encryptionManager.getBoolean("open_intents_in_new_tab", true)
        _displayUnderCutouts.value = encryptionManager.getBoolean("display_under_cutouts", false)
        _doNotTrack.value = encryptionManager.getBoolean("do_not_track", true)
        _safeBrowsing.value = encryptionManager.getBoolean("safe_browsing", true)
        _httpsOnly.value = encryptionManager.getBoolean("https_only", true)
        _searchEngine.value = SearchEngine.fromKey(encryptionManager.getString("search_engine"))
        _homepage.value = encryptionManager.getString("homepage") ?: DEFAULT_HOMEPAGE
        _saveHistory.value = encryptionManager.getBoolean("save_history", true)
    }

    /**
     * Wire the biometric toggle from the UI. When enabling, prompts for biometric and wraps the
     * passphrase under a Keystore key requiring user authentication. When disabling, the wrap is
     * discarded and the (always-present) plain passphrase remains in EncryptedSharedPreferences.
     */
    fun toggleBiometrics(activity: FragmentActivity, enabled: Boolean, onError: (String) -> Unit) {
        if (!enabled) {
            encryptionManager.saveBoolean("use_biometrics", false)
            passphraseGate.clearBiometricWrap()
            _useBiometrics.value = false
            return
        }
        if (!biometricAuthManager.isBiometricAvailable()) {
            onError("No strong biometrics enrolled on this device.")
            return
        }
        val cipher = runCatching { passphraseGate.makeEncryptionCipher() }.getOrNull()
        if (cipher == null) {
            onError("Keystore unavailable.")
            return
        }
        biometricAuthManager.showCryptoPrompt(
            activity = activity,
            cipher = cipher,
            title = "Enable biometric unlock",
            subtitle = "Wrap your database key under your biometric",
            onSuccess = { unlocked ->
                val plain = passphraseGate.loadOrCreatePlainPassphrase()
                passphraseGate.wrapPlainPassphraseUnderBiometric(plain, unlocked)
                encryptionManager.saveBoolean("use_biometrics", true)
                _useBiometrics.value = true
            },
            onError = { msg ->
                encryptionManager.saveBoolean("use_biometrics", false)
                passphraseGate.clearBiometricWrap()
                _useBiometrics.value = false
                onError(msg)
            }
        )
    }

    fun setFontSize(size: Float) {
        encryptionManager.saveFloat("font_size", size)
        _fontSize.value = size
    }

    fun setFontFamily(family: String) {
        encryptionManager.saveString("font_family", family)
        _fontFamily.value = family
    }

    fun setScreenshotProtection(enabled: Boolean) {
        encryptionManager.saveBoolean("screenshot_protection", enabled)
        _screenshotProtection.value = enabled
    }

    /**
     * Cache Deletion: Clears temporary files without removing bookmarks, history, or settings.
     */
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
        }
    }

    /** Wipes cookies, web storage, and the WebView's own HTTP cache. Leaves history/bookmarks. */
    fun clearBrowsingData(onDone: () -> Unit = {}) {
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        WebStorage.getInstance().deleteAllData()
        WebView(context).apply { clearCache(true); destroy() }
        onDone()
    }

    fun clearHistory(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyDao.get().clear() }
            onDone()
        }
    }

    fun clearBookmarks(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { bookmarkDao.get().clear() }
            onDone()
        }
    }

    /**
     * Data Deletion: Wipes all app data including database, preferences, cache, and the
     * biometric-bound Keystore key.
     */
    fun wipeAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            passphraseGate.clearBiometricWrap()
            encryptionManager.securePrefs.edit().clear().commit()
            context.deleteDatabase("www.db")
            context.cacheDir.deleteRecursively()
            refreshSettings()
        }
    }

    companion object {
        /** Blank start page. A remote default would leak a request on every cold start. */
        const val DEFAULT_HOMEPAGE = "about:blank"
    }
}
