package com.jegly.www.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Announcement
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jegly.www.BuildConfig
import com.jegly.www.network.DohProvider
import com.jegly.www.network.UserAgentTemplate
import com.jegly.www.util.SearchEngine
import com.jegly.www.presentation.theme.CatppuccinFlavor
import com.jegly.www.presentation.theme.DraculaColors
import com.jegly.www.presentation.theme.PAPER_THEMES
import com.jegly.www.presentation.theme.PTYXIS_THEMES
import com.jegly.www.presentation.theme.catppuccinAccentsFor
import com.jegly.www.presentation.theme.isPaperTheme
import com.jegly.www.presentation.theme.paperAccentsFor
import com.jegly.www.presentation.theme.paperDefaultAccent
import com.jegly.www.presentation.theme.LEGIBILITY_WARNING_FONTS
import com.jegly.www.presentation.theme.fontFamilies
import com.jegly.www.presentation.theme.ptyxisThemeFromKey
import com.jegly.www.util.BrowserUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun cookiePolicyLabel(key: String): String = when (key) {
    "block" -> "Block all cookies"
    "all" -> "Allow all (incl. third-party)"
    else -> "First-party only"
}

/** Tappable category header for a collapsible settings section. */
@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val useBiometrics by viewModel.useBiometrics.collectAsState()
    val advancedProtectionEnabled by viewModel.advancedProtectionEnabled.collectAsState()
    val passcodeKind by viewModel.passcodeKind.collectAsState()
    val blockAutofill by viewModel.blockAutofill.collectAsState()
    val blockDownloads by viewModel.blockDownloads.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val screenshotProtection by viewModel.screenshotProtection.collectAsState()
    val dohProvider by viewModel.dohProvider.collectAsState()
    val userAgentKey by viewModel.userAgent.collectAsState()
    val cookiePolicy by viewModel.cookiePolicy.collectAsState()
    val blockTrackers by viewModel.blockTrackers.collectAsState()
    val webViewJavaScript by viewModel.webViewJavaScript.collectAsState()
    val webViewDomStorage by viewModel.webViewDomStorage.collectAsState()
    val doNotTrack by viewModel.doNotTrack.collectAsState()
    val safeBrowsing by viewModel.safeBrowsing.collectAsState()
    val httpsOnly by viewModel.httpsOnly.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()
    val saveHistory by viewModel.saveHistory.collectAsState()
    val blockThirdPartyRequests by viewModel.blockThirdPartyRequests.collectAsState()
    val stripTrackingQueries by viewModel.stripTrackingQueries.collectAsState()
    val incognitoMode by viewModel.incognitoMode.collectAsState()
    val allowJsDialogs by viewModel.allowJsDialogs.collectAsState()
    val forceDark by viewModel.forceDark.collectAsState()
    val wideViewport by viewModel.wideViewport.collectAsState()
    val displayImages by viewModel.displayImages.collectAsState()
    val swipeToRefresh by viewModel.swipeToRefresh.collectAsState()
    val openIntentsInNewTab by viewModel.openIntentsInNewTab.collectAsState()
    val displayUnderCutouts by viewModel.displayUnderCutouts.collectAsState()
    val clearOnExit by viewModel.clearOnExit.collectAsState()
    val clearCookies by viewModel.clearCookies.collectAsState()
    val clearDomStorage by viewModel.clearDomStorage.collectAsState()
    val clearCache by viewModel.clearCache.collectAsState()
    val clearLogcat by viewModel.clearLogcat.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val catppuccinAccentKey by viewModel.catppuccinAccent.collectAsState()
    val catppuccinFlavorKey by viewModel.catppuccinFlavor.collectAsState()
    val draculaAccentKey by viewModel.draculaAccent.collectAsState()
    val ptyxisPaletteKey by viewModel.ptyxisPalette.collectAsState()
    val paperAccentKeys by viewModel.paperAccents.collectAsState()
    val keystoreLevel = viewModel.keystoreSecurityLevel

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var pendingFontWarning by remember { mutableStateOf<String?>(null) }
    var showPtyxisDialog by remember { mutableStateOf(false) }
    var showDohDialog by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showForceDarkDialog by remember { mutableStateOf(false) }
    var showLockKindDialog by remember { mutableStateOf(false) }
    var pendingLockKind by remember { mutableStateOf<com.jegly.www.security.PasscodeManager.Kind?>(null) }

    // Collapsed/expanded state per settings category. Local UI state only — deliberately not
    // persisted, so the screen always opens the same way rather than remembering a scroll-position
    // quirk from a previous visit.
    //
    // All start collapsed: with ten sections expanded the screen opened as a wall of switches
    // several thousand pixels tall, where finding one setting meant scrolling past every other.
    // Closed, the whole map of the app fits on one screen and one tap gets to any of it.
    var securityExpanded by remember { mutableStateOf(false) }
    var networkingExpanded by remember { mutableStateOf(false) }
    var browserPrivacyExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    var clearOnExitExpanded by remember { mutableStateOf(false) }
    var behaviourExpanded by remember { mutableStateOf(false) }
    var browsingDataExpanded by remember { mutableStateOf(false) }
    var dataManagementExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECURITY ---
            item {
                CollapsibleSectionHeader(
                    title = "Security",
                    expanded = securityExpanded,
                    onToggle = { securityExpanded = !securityExpanded }
                )
                if (!securityExpanded) return@item

                if (advancedProtectionEnabled) {
                    /*
                     * There's no API for a third-party app to turn Advanced Protection on itself —
                     * only to observe it (see AdvancedProtectionGate) — so this banner is purely
                     * informational, explaining why the toggles below are greyed out rather than
                     * letting the user wonder if something's broken.
                     */
                    ListItem(
                        headlineContent = { Text("Advanced Protection is on for this device") },
                        supportingContent = {
                            Text(
                                "Screenshot Protection, Safe Browsing, Force HTTPS Only, and Block " +
                                    "Downloads are locked on while it's active."
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                ListItem(
                    headlineContent = { Text("Hardware Security Level") },
                    supportingContent = { Text(keystoreLevel) },
                    leadingContent = { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary) }
                )

                ListItem(
                    headlineContent = { Text("Biometric Authentication") },
                    supportingContent = { Text("Require fingerprint to decrypt the database") },
                    leadingContent = { Icon(Icons.Default.Fingerprint, null) },
                    trailingContent = {
                        Switch(checked = useBiometrics, onCheckedChange = { enabled ->
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                scope.launch { snackbarHostState.showSnackbar("Cannot toggle biometric: activity context missing") }
                                return@Switch
                            }
                            viewModel.toggleBiometrics(activity, enabled) { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        })
                    }
                )
                ListItem(
                    headlineContent = { Text("App Lock") },
                    supportingContent = {
                        Text(
                            when {
                                passcodeKind == com.jegly.www.security.PasscodeManager.Kind.PIN -> "PIN"
                                passcodeKind == com.jegly.www.security.PasscodeManager.Kind.PASSWORD -> "Password"
                                passcodeKind == com.jegly.www.security.PasscodeManager.Kind.PATTERN -> "Pattern"
                                // Unlike the toggles below, this can't be forced on — setting a
                                // passcode needs the user to actually enter one — so Advanced
                                // Protection can only be a nudge here, not a lock.
                                advancedProtectionEnabled ->
                                    "Off — Advanced Protection is on; a PIN, password, or pattern is strongly recommended"
                                else -> "Off — biometric only, or no lock"
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Password, null) },
                    modifier = Modifier.clickable { showLockKindDialog = true }
                )

                ListItem(
                    headlineContent = { Text("Screenshot Protection") },
                    supportingContent = {
                        Text(
                            if (advancedProtectionEnabled) "Locked on by Android Advanced Protection"
                            else "Prevent screenshots and screen recording"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.NoEncryption, null) },
                    trailingContent = {
                        Switch(
                            checked = screenshotProtection,
                            enabled = !advancedProtectionEnabled,
                            onCheckedChange = { viewModel.setScreenshotProtection(it) }
                        )
                    }
                )
            }

            // --- NETWORKING ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Networking & Privacy",
                    expanded = networkingExpanded,
                    onToggle = { networkingExpanded = !networkingExpanded }
                )
                if (!networkingExpanded) return@item
                ListItem(
                    headlineContent = { Text("Search Engine") },
                    supportingContent = { Text(searchEngine.displayName) },
                    leadingContent = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.clickable { showSearchEngineDialog = true }
                )
                ListItem(
                    headlineContent = { Text("Save History") },
                    supportingContent = { Text("Record visited pages in the encrypted database") },
                    leadingContent = { Icon(Icons.Default.History, null) },
                    trailingContent = {
                        Switch(checked = saveHistory, onCheckedChange = { viewModel.setSaveHistory(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("DNS-over-HTTPS Resolver") },
                    supportingContent = { Text(dohProvider.displayName) },
                    leadingContent = { Icon(Icons.Default.Dns, null) },
                    modifier = Modifier.clickable { showDohDialog = true }
                )
                ListItem(
                    headlineContent = { Text("WebView User Agent") },
                    supportingContent = { Text(UserAgentTemplate.fromKey(userAgentKey).displayName) },
                    leadingContent = { Icon(Icons.Default.Language, null) },
                    modifier = Modifier.clickable { showUserAgentDialog = true }
                )
            }

            // --- BROWSER PRIVACY ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Browser Privacy",
                    expanded = browserPrivacyExpanded,
                    onToggle = { browserPrivacyExpanded = !browserPrivacyExpanded }
                )
                if (!browserPrivacyExpanded) return@item
                ListItem(
                    headlineContent = { Text("Cookie Policy") },
                    supportingContent = { Text(cookiePolicyLabel(cookiePolicy)) },
                    leadingContent = { Icon(Icons.Default.Cookie, null) },
                    modifier = Modifier.clickable { showCookieDialog = true }
                )
                ListItem(
                    headlineContent = { Text("Block Trackers & Ads") },
                    supportingContent = { Text("Block known tracker and ad domains in the reader") },
                    leadingContent = { Icon(Icons.Default.Block, null) },
                    trailingContent = {
                        Switch(checked = blockTrackers, onCheckedChange = { viewModel.setBlockTrackers(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("App Identifier") },
                    supportingContent = {
                        Text(
                            "Every request sends a blanked X-Requested-With header instead of this " +
                                "app's package name. Always on, no site is exempt."
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Fingerprint, null) }
                )
                ListItem(
                    headlineContent = { Text("JavaScript") },
                    supportingContent = { Text("Disabling improves privacy but breaks many sites") },
                    leadingContent = { Icon(Icons.Default.Javascript, null) },
                    trailingContent = {
                        Switch(checked = webViewJavaScript, onCheckedChange = { viewModel.setWebViewJavaScript(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Site Data (DOM Storage)") },
                    supportingContent = { Text("Allow pages to use local/session storage") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    trailingContent = {
                        Switch(checked = webViewDomStorage, onCheckedChange = { viewModel.setWebViewDomStorage(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Block All Third-Party Requests") },
                    supportingContent = {
                        Text("Blocks every subresource from a different domain than the page. Very strong, but breaks sites that use a CDN for scripts, fonts or images.")
                    },
                    leadingContent = { Icon(Icons.Default.Block, null) },
                    trailingContent = {
                        Switch(
                            checked = blockThirdPartyRequests,
                            onCheckedChange = { viewModel.setBlockThirdPartyRequests(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Strip Tracking Parameters") },
                    supportingContent = { Text("Remove utm_*, fbclid, gclid and similar from URLs before loading") },
                    leadingContent = { Icon(Icons.Default.LinkOff, null) },
                    trailingContent = {
                        Switch(
                            checked = stripTrackingQueries,
                            onCheckedChange = { viewModel.setStripTrackingQueries(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Block Autofill") },
                    supportingContent = {
                        Text("Hide page fields from Android autofill and password managers")
                    },
                    leadingContent = { Icon(Icons.Default.EditOff, null) },
                    trailingContent = {
                        Switch(checked = blockAutofill, onCheckedChange = { viewModel.setBlockAutofill(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Block Downloads") },
                    supportingContent = {
                        Text(
                            if (advancedProtectionEnabled) "Locked on by Android Advanced Protection"
                            else "Refuse all file downloads, matching the AOSP reference WebView shell"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.DownloadForOffline, null) },
                    trailingContent = {
                        Switch(
                            checked = blockDownloads,
                            enabled = !advancedProtectionEnabled,
                            onCheckedChange = { viewModel.setBlockDownloads(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Incognito Mode") },
                    supportingContent = { Text("Wipe cookies, storage and cache after every page load") },
                    leadingContent = { Icon(Icons.Default.VisibilityOff, null) },
                    trailingContent = {
                        Switch(checked = incognitoMode, onCheckedChange = { viewModel.setIncognitoMode(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Allow JavaScript Dialogs") },
                    supportingContent = {
                        Text("alert() and confirm() popups. Capped at 3 per page either way, so a page can't lock the browser.")
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Announcement, null) },
                    trailingContent = {
                        Switch(checked = allowJsDialogs, onCheckedChange = { viewModel.setAllowJsDialogs(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Display Images") },
                    supportingContent = { Text("When off, images are never requested at all") },
                    leadingContent = { Icon(Icons.Default.Image, null) },
                    trailingContent = {
                        Switch(checked = displayImages, onCheckedChange = { viewModel.setDisplayImages(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Domain Settings") },
                    supportingContent = { Text("Per-site overrides for JavaScript, cookies, blocking and images") },
                    leadingContent = { Icon(Icons.Default.Tune, null) },
                    modifier = Modifier.clickable { navController.navigate("domain_settings") }
                )
                ListItem(
                    headlineContent = { Text("Send \"Do Not Track\"") },
                    supportingContent = { Text("Add DNT and Global Privacy Control request signals") },
                    leadingContent = { Icon(Icons.Default.PrivacyTip, null) },
                    trailingContent = {
                        Switch(checked = doNotTrack, onCheckedChange = { viewModel.setDoNotTrack(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Safe Browsing") },
                    supportingContent = {
                        Text(
                            if (advancedProtectionEnabled) "Locked on by Android Advanced Protection"
                            else "Warn about known malware and phishing pages"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.GppGood, null) },
                    trailingContent = {
                        Switch(
                            checked = safeBrowsing,
                            enabled = !advancedProtectionEnabled,
                            onCheckedChange = { viewModel.setSafeBrowsing(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Force HTTPS Only") },
                    supportingContent = {
                        Text(
                            if (advancedProtectionEnabled) "Locked on by Android Advanced Protection"
                            else "Block insecure HTTP page loads and mixed content"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Lock, null) },
                    trailingContent = {
                        Switch(
                            checked = httpsOnly,
                            enabled = !advancedProtectionEnabled,
                            onCheckedChange = { viewModel.setHttpsOnly(it) }
                        )
                    }
                )
            }

            // --- APPEARANCE ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Appearance",
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded }
                )
                if (!appearanceExpanded) return@item

                Spacer(Modifier.height(12.dp))

                // Theme selector
                Text(
                    "Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                /*
                 * Wrapping chips rather than the segmented row this used to be. A segmented row
                 * divides the width evenly between its items, so at seven themes "Catppuccin" and
                 * "Everforest" would each get roughly a two-character slot before ellipsing. Chips
                 * size to their label and wrap, and they match the flavour picker directly below.
                 */
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val themeChoices = buildList {
                        add("system" to "System")
                        add("catppuccin" to "Catppuccin")
                        add("dracula" to "Dracula")
                        add("ptyxis" to "Ptyxis")
                        addAll(PAPER_THEMES)
                    }
                    themeChoices.forEach { (key, label) ->
                        FilterChip(
                            selected = themeMode == key,
                            onClick = { viewModel.setThemeMode(key) },
                            label = { Text(label) }
                        )
                    }
                }

                // Catppuccin flavour picker (Latte/Frappé/Macchiato/Mocha)
                if (themeMode == "catppuccin") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Flavour",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CatppuccinFlavor.entries.forEach { flavor ->
                            FilterChip(
                                selected = catppuccinFlavorKey == flavor.key,
                                onClick = { viewModel.setCatppuccinFlavor(flavor.key) },
                                label = { Text(flavor.displayName) }
                            )
                        }
                    }
                }

                // Accent swatches — every theme except System (no accent of its own) and Ptyxis
                // (44 whole palettes, picked from the dialog below rather than by accent).
                if (themeMode == "catppuccin" || themeMode == "dracula" || isPaperTheme(themeMode)) {
                    Spacer(Modifier.height(12.dp))
                    val accents: Map<String, Pair<String, Color>>
                    val currentAccent: String
                    val setAccent: (String) -> Unit
                    if (themeMode == "catppuccin") {
                        accents = catppuccinAccentsFor(catppuccinFlavorKey)
                        currentAccent = catppuccinAccentKey
                        setAccent = { viewModel.setCatppuccinAccent(it) }
                    } else if (themeMode == "dracula") {
                        accents = DraculaColors.accents
                        currentAccent = draculaAccentKey
                        setAccent = { viewModel.setDraculaAccent(it) }
                    } else {
                        accents = paperAccentsFor(themeMode)
                        currentAccent = paperAccentKeys[themeMode] ?: paperDefaultAccent(themeMode)
                        setAccent = { viewModel.setPaperAccent(themeMode, it) }
                    }
                    Text(
                        "Accent colour",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        accents.forEach { (key, pair) ->
                            val (_, color) = pair
                            val isSelected = key == currentAccent
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                    .clickable { setAccent(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        // Follows the swatch: the dark-theme palettes are all pale
                                        // accents, but the light ones are saturated and dark, and a
                                        // black tick on Rosé Pine's Pine is unreadable.
                                        tint = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.65f)
                                        else Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Ptyxis palette picker — 44 named palettes, too many for swatches, so a list dialog.
                if (themeMode == "ptyxis") {
                    Spacer(Modifier.height(12.dp))
                    ListItem(
                        headlineContent = { Text("Palette") },
                        supportingContent = { Text(ptyxisThemeFromKey(ptyxisPaletteKey).displayName) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(ptyxisThemeFromKey(ptyxisPaletteKey).primary))
                            )
                        },
                        modifier = Modifier.clickable { showPtyxisDialog = true }
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(4.dp))
            }

            // --- TEXT ---
            // Its own section rather than two loose rows hanging off the end of Appearance: these
            // two are the only settings in the screen that were reachable with every section
            // collapsed, which reads as a rendering bug once everything else folds away.
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Text",
                    expanded = textExpanded,
                    onToggle = { textExpanded = !textExpanded }
                )
                if (!textExpanded) return@item

                ListItem(
                    headlineContent = { Text("Font Family") },
                    supportingContent = { Text(fontFamily) },
                    leadingContent = { Icon(Icons.Default.FontDownload, null) },
                    modifier = Modifier.clickable { showFontDialog = true }
                )
                ListItem(
                    headlineContent = { Text("Text Size") },
                    supportingContent = { Text("${fontSize.roundToInt()} sp · page text zoom ${((fontSize / 16f) * 100).roundToInt()}%") },
                    leadingContent = { Icon(Icons.Default.FormatSize, null) }
                )
                Slider(
                    value = fontSize,
                    onValueChange = { viewModel.setFontSize(it) },
                    valueRange = 12f..24f,
                    steps = 6,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // --- CLEAR ON EXIT ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Clear on Exit",
                    expanded = clearOnExitExpanded,
                    onToggle = { clearOnExitExpanded = !clearOnExitExpanded }
                )
                if (!clearOnExitExpanded) return@item
                ListItem(
                    headlineContent = { Text("Clear on Exit") },
                    supportingContent = { Text("Wipe the selected categories when www closes") },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, null) },
                    trailingContent = {
                        Switch(checked = clearOnExit, onCheckedChange = { viewModel.setClearOnExit(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Cookies") },
                    leadingContent = { Icon(Icons.Default.Cookie, null) },
                    trailingContent = {
                        Switch(
                            checked = clearCookies,
                            enabled = clearOnExit,
                            onCheckedChange = { viewModel.setClearCookies(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Site Data") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    trailingContent = {
                        Switch(
                            checked = clearDomStorage,
                            enabled = clearOnExit,
                            onCheckedChange = { viewModel.setClearDomStorage(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Cache") },
                    leadingContent = { Icon(Icons.Default.Cached, null) },
                    trailingContent = {
                        Switch(
                            checked = clearCache,
                            enabled = clearOnExit,
                            onCheckedChange = { viewModel.setClearCache(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Logcat") },
                    supportingContent = { Text("This app's own log buffer, which can contain visited URLs") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Article, null) },
                    trailingContent = {
                        Switch(
                            checked = clearLogcat,
                            enabled = clearOnExit,
                            onCheckedChange = { viewModel.setClearLogcat(it) }
                        )
                    }
                )
            }

            // --- BEHAVIOUR ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Behaviour",
                    expanded = behaviourExpanded,
                    onToggle = { behaviourExpanded = !behaviourExpanded }
                )
                if (!behaviourExpanded) return@item
                ListItem(
                    headlineContent = { Text("Dark Web Content") },
                    supportingContent = {
                        Text(
                            when (forceDark) {
                                "on" -> "Always darken pages"
                                "off" -> "Never darken pages"
                                else -> "Follow the system dark theme"
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.DarkMode, null) },
                    modifier = Modifier.clickable { showForceDarkDialog = true }
                )
                ListItem(
                    headlineContent = { Text("Wide Viewport") },
                    supportingContent = { Text("Render pages at their intended desktop width and zoom out to fit") },
                    leadingContent = { Icon(Icons.Default.Fullscreen, null) },
                    trailingContent = {
                        Switch(checked = wideViewport, onCheckedChange = { viewModel.setWideViewport(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Swipe to Refresh") },
                    leadingContent = { Icon(Icons.Default.Refresh, null) },
                    trailingContent = {
                        Switch(checked = swipeToRefresh, onCheckedChange = { viewModel.setSwipeToRefresh(it) })
                    }
                )
                ListItem(
                    headlineContent = { Text("Open Links from Other Apps in a New Tab") },
                    supportingContent = { Text("When off, an incoming link replaces the current tab") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    trailingContent = {
                        Switch(
                            checked = openIntentsInNewTab,
                            onCheckedChange = { viewModel.setOpenIntentsInNewTab(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Draw Under Display Cutout") },
                    supportingContent = { Text("Let page content extend into the notch area") },
                    leadingContent = { Icon(Icons.Default.CropFree, null) },
                    trailingContent = {
                        Switch(
                            checked = displayUnderCutouts,
                            onCheckedChange = { viewModel.setDisplayUnderCutouts(it) }
                        )
                    }
                )
            }

            // --- BROWSING DATA ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Browsing Data",
                    expanded = browsingDataExpanded,
                    onToggle = { browsingDataExpanded = !browsingDataExpanded }
                )
                if (!browsingDataExpanded) return@item

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearBrowsingData {
                            scope.launch { snackbarHostState.showSnackbar("Cookies and site data cleared") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Cookie, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Cookies & Site Data")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearHistory {
                            scope.launch { snackbarHostState.showSnackbar("History cleared") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.History, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear History")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearBookmarks {
                            scope.launch { snackbarHostState.showSnackbar("Bookmarks cleared") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.BookmarkBorder, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Bookmarks")
                }
            }

            // --- DATA MANAGEMENT ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "Data Management",
                    expanded = dataManagementExpanded,
                    onToggle = { dataManagementExpanded = !dataManagementExpanded },
                    color = MaterialTheme.colorScheme.error
                )
                if (!dataManagementExpanded) return@item

                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearCache()
                        scope.launch {
                            snackbarHostState.showSnackbar("Cache cleared successfully")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Cached, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Cache")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wipe All App Data")
                }
            }

            // --- ABOUT ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CollapsibleSectionHeader(
                    title = "About",
                    expanded = aboutExpanded,
                    onToggle = { aboutExpanded = !aboutExpanded }
                )
                if (!aboutExpanded) return@item

                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                    leadingContent = { Icon(Icons.Default.Info, null) }
                )

                ListItem(
                    headlineContent = { Text("GitHub") },
                    supportingContent = { Text("github.com/jegly") },
                    leadingContent = { Icon(Icons.Default.Code, null) },
                    modifier = Modifier.clickable { 
                        BrowserUtils.openSanitizedUrl(context, "https://github.com/jegly")
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Website") },
                    supportingContent = { Text("jegly.xyz") },
                    leadingContent = { Icon(Icons.Default.Language, null) },
                    modifier = Modifier.clickable { 
                        BrowserUtils.openSanitizedUrl(context, "https://www.jegly.xyz")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "made with love by Jegly",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Select Font Family") },
            text = {
                val fontList = fontFamilies.keys.toList()
                LazyColumn {
                    items(items = fontList) { family ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (family != fontFamily && family in LEGIBILITY_WARNING_FONTS) {
                                    pendingFontWarning = family
                                } else {
                                    viewModel.setFontFamily(family)
                                    showFontDialog = false
                                }
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = fontFamily == family, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = family,
                                fontFamily = fontFamilies[family] ?: FontFamily.Default,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingFontWarning?.let { family ->
        AlertDialog(
            onDismissRequest = { pendingFontWarning = null },
            title = { Text("Hard-to-read font") },
            text = { Text("\"$family\" can be hard to read as body text. Use it anyway?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFontFamily(family)
                    pendingFontWarning = null
                    showFontDialog = false
                }) { Text("Use anyway") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFontWarning = null }) { Text("Cancel") }
            }
        )
    }

    if (showPtyxisDialog) {
        AlertDialog(
            onDismissRequest = { showPtyxisDialog = false },
            title = { Text("Select Ptyxis Palette") },
            text = {
                LazyColumn {
                    items(items = PTYXIS_THEMES) { palette ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.setPtyxisPalette(palette.key)
                                showPtyxisDialog = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = ptyxisPaletteKey == palette.key, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 28.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(Color(palette.background)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aa",
                                    color = Color(palette.primary),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(text = palette.displayName, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPtyxisDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLockKindDialog) {
        AlertDialog(
            onDismissRequest = { showLockKindDialog = false },
            title = { Text("App Lock") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        com.jegly.www.security.PasscodeManager.Kind.NONE to "Off",
                        com.jegly.www.security.PasscodeManager.Kind.PIN to "PIN",
                        com.jegly.www.security.PasscodeManager.Kind.PASSWORD to "Password",
                        com.jegly.www.security.PasscodeManager.Kind.PATTERN to "Pattern"
                    ).forEach { (kind, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLockKindDialog = false
                                    if (kind == com.jegly.www.security.PasscodeManager.Kind.NONE) {
                                        viewModel.clearPasscode()
                                    } else {
                                        pendingLockKind = kind
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = passcodeKind == kind, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLockKindDialog = false }) { Text("Cancel") } }
        )
    }

    pendingLockKind?.let { kind ->
        com.jegly.www.presentation.lock.PasscodeSetupDialog(
            kind = kind,
            onDismiss = { pendingLockKind = null },
            onConfirmed = { code ->
                viewModel.setPasscode(kind, code) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (ok) "App lock enabled" else "Could not set app lock"
                        )
                    }
                }
                pendingLockKind = null
            }
        )
    }

    if (showForceDarkDialog) {
        AlertDialog(
            onDismissRequest = { showForceDarkDialog = false },
            title = { Text("Dark Web Content") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        "system" to "Follow system theme",
                        "on" to "Always on",
                        "off" to "Always off"
                    ).forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setForceDark(key)
                                    showForceDarkDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = forceDark == key, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showForceDarkDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Search Engine") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSearchEngine(engine)
                                    showSearchEngineDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = searchEngine == engine, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(engine.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDohDialog) {
        AlertDialog(
            onDismissRequest = { showDohDialog = false },
            title = { Text("DNS Resolver") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DohProvider.values().forEach { provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDohProvider(provider)
                                    showDohDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = dohProvider == provider, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(provider.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDohDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showUserAgentDialog) {
        AlertDialog(
            onDismissRequest = { showUserAgentDialog = false },
            title = { Text("WebView User Agent") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    UserAgentTemplate.entries.forEach { template ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setUserAgent(template.key)
                                    showUserAgentDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = userAgentKey == template.key, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(template.displayName)
                                if (template.uaString.isNotEmpty()) {
                                    Text(
                                        template.uaString.take(50) + "…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserAgentDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCookieDialog) {
        val options = listOf(
            "block" to "Block all cookies",
            "first_party" to "First-party only",
            "all" to "Allow all (incl. third-party)"
        )
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("Cookie Policy") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (key, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCookiePolicy(key)
                                    showCookieDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cookiePolicy == key, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCookieDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Wipe All Data?") },
            text = { Text("This will permanently delete your history, bookmarks, settings, and cached data. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.wipeAllData()
                        showDeleteConfirm = false
                        navController.navigate("home") {
                            popUpTo(0)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
