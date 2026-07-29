package com.jegly.www.presentation.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jegly.www.data.local.BookmarkDao
import com.jegly.www.data.local.BookmarkEntity
import com.jegly.www.data.local.DomainSettingDao
import com.jegly.www.data.local.DomainSettingEntity
import com.jegly.www.data.local.HistoryDao
import com.jegly.www.security.EncryptionManager
import com.jegly.www.util.SearchEngine
import com.jegly.www.util.UrlUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the tab list and all persistence side effects of browsing.
 *
 * Unlike SettingsViewModel, this one injects its DAOs directly: it is only ever resolved from
 * NavGraph, which MainActivity composes after PassphraseGate is open, so opening the encrypted
 * database here cannot deadlock. Nothing in the pre-auth dialog path touches it.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val domainSettingDao: DomainSettingDao,
    private val encryptionManager: EncryptionManager
) : ViewModel() {

    val tabs = mutableStateListOf<TabState>()

    var activeTabId by mutableStateOf<String?>(null)
        private set

    /** True while the tab switcher overlay is up. */
    var isTabSwitcherOpen by mutableStateOf(false)

    val activeTab: TabState?
        get() = tabs.firstOrNull { it.id == activeTabId }

    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.all()

    val history = historyDao.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (tabs.isEmpty()) newTab(select = true)
    }

    // ---- tabs ------------------------------------------------------------------------------

    fun newTab(url: String = "", select: Boolean = true): TabState {
        val tab = TabState(initialUrl = url)
        tabs.add(tab)
        if (select) activeTabId = tab.id
        return tab
    }

    fun selectTab(id: String) {
        activeTabId = id
        isTabSwitcherOpen = false
    }

    /**
     * Closing the last tab opens a fresh empty one rather than leaving a void — there is no
     * "no tabs" screen, and an empty tab strip with a dead URL bar is a worse state than a blank
     * new tab.
     */
    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return
        tabs.removeAt(index)

        if (tabs.isEmpty()) {
            newTab(select = true)
            return
        }
        if (activeTabId == id) {
            activeTabId = tabs[index.coerceAtMost(tabs.lastIndex)].id
        }
    }

    fun closeAllTabs() {
        tabs.clear()
        newTab(select = true)
    }

    // ---- omnibox ---------------------------------------------------------------------------

    /**
     * Resolve omnibox text to a loadable URL, or null when it should be handed to the system as an
     * external scheme. Search fallback uses the engine chosen in Settings.
     */
    fun resolveInput(raw: String): UrlUtils.Destination {
        val httpsOnly = encryptionManager.getBoolean("https_only", true)
        return when (val destination = UrlUtils.parse(raw, defaultToHttps = httpsOnly)) {
            is UrlUtils.Destination.Search -> {
                val engine = SearchEngine.fromKey(encryptionManager.getString("search_engine"))
                UrlUtils.Destination.Web(engine.searchUrlFor(destination.query))
            }
            else -> destination
        }
    }

    // ---- history ---------------------------------------------------------------------------

    /**
     * No-ops entirely when history saving is off — the check is here rather than at the query
     * layer so that disabling it means nothing is ever written, not that it is written and hidden.
     */
    fun recordVisit(url: String, title: String) {
        if (!encryptionManager.getBoolean("save_history", true)) return
        // Only real web navigations. about:blank, data: and blob: URLs are not places the user can
        // return to, and a data: URL can be megabytes of inlined content we'd be encrypting to disk.
        if (!isRecordableUrl(url)) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyDao.recordVisit(
                    url = url,
                    title = title,
                    host = UrlUtils.hostOf(url),
                    now = System.currentTimeMillis()
                )
            }
        }
    }

    private fun isRecordableUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    fun updateHistoryTitle(url: String, title: String) {
        if (!encryptionManager.getBoolean("save_history", true)) return
        if (title.isBlank() || !isRecordableUrl(url)) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyDao.updateTitle(url, title) }
        }
    }

    suspend fun searchHistory(query: String) =
        if (query.isBlank()) emptyList()
        else withContext(Dispatchers.IO) { historyDao.search(query) }

    fun deleteHistoryEntry(url: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { historyDao.deleteUrl(url) } }
    }

    // ---- bookmarks -------------------------------------------------------------------------

    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.isBookmarked(url)

    fun toggleBookmark(url: String, title: String, currentlyBookmarked: Boolean) {
        if (url.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (currentlyBookmarked) {
                    bookmarkDao.deleteUrl(url)
                } else {
                    bookmarkDao.insert(
                        BookmarkEntity(
                            url = url,
                            title = title.ifBlank { UrlUtils.displayOrigin(url) },
                            createdAt = System.currentTimeMillis(),
                            position = bookmarkDao.nextPosition(null)
                        )
                    )
                }
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { withContext(Dispatchers.IO) { bookmarkDao.delete(bookmark) } }
    }

    // ---- domain settings -------------------------------------------------------------------

    val domainSettings: Flow<List<DomainSettingEntity>> = domainSettingDao.all()

    /** Most specific matching rule for a host, or null to use globals. See DomainSettingsResolver. */
    suspend fun domainSettingFor(host: String): DomainSettingEntity? {
        if (host.isBlank()) return null
        return withContext(Dispatchers.IO) { domainSettingDao.matching(host).firstOrNull() }
    }

    fun saveDomainSetting(setting: DomainSettingEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val normalised = setting.copy(
                    domain = DomainSettingsResolver.normaliseDomain(setting.domain)
                )
                if (normalised.domain.isBlank()) return@withContext
                // Preserve the existing row id so editing a domain updates rather than duplicating;
                // the unique index would otherwise make REPLACE silently drop the old row's id.
                val existing = domainSettingDao.byDomain(normalised.domain)
                domainSettingDao.insert(
                    if (existing != null) normalised.copy(id = existing.id) else normalised
                )
            }
        }
    }

    fun deleteDomainSetting(setting: DomainSettingEntity) {
        viewModelScope.launch { withContext(Dispatchers.IO) { domainSettingDao.delete(setting) } }
    }
}
