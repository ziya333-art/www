package com.jegly.www.presentation.navigation

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jegly.www.presentation.browser.BookmarksScreen
import com.jegly.www.presentation.browser.BrowserScreen
import com.jegly.www.presentation.browser.BrowserViewModel
import com.jegly.www.presentation.browser.DomainSettingsScreen
import com.jegly.www.presentation.browser.HistoryScreen
import com.jegly.www.presentation.settings.SettingsScreen
import com.jegly.www.presentation.settings.SettingsViewModel

@Composable
fun NavGraph(initialUrl: String? = null) {
    val navController = rememberNavController()

    // Both are hoisted to the NavHost's scope rather than created per-destination:
    // SettingsViewModel must be the same instance WwwTheme resolves so theme changes apply
    // immediately, and BrowserViewModel must outlive navigation to History/Settings — scoping it
    // to the "browser" destination would discard every open tab the moment a menu was opened.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val browserViewModel: BrowserViewModel = hiltViewModel()

    /*
     * MainActivity applies FLAG_SECURE from the encrypted pref in onCreate/onResume — that
     * guards the earliest frames, before this Composable tree exists at all (the biometric and
     * tamper dialogs render pre-auth, outside NavGraph). But toggling the switch in Settings
     * doesn't pause/resume the Activity, so without this effect the flag would only update the
     * next time the app is backgrounded and foregrounded — reported as "the toggle does nothing".
     * This reapplies it the instant the StateFlow changes.
     */
    val screenshotProtection by settingsViewModel.screenshotProtection.collectAsState()
    val activity = LocalContext.current as? Activity
    LaunchedEffect(screenshotProtection, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (screenshotProtection) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    NavHost(navController = navController, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(
                navController = navController,
                initialUrl = initialUrl,
                viewModel = browserViewModel,
                settingsViewModel = settingsViewModel
            )
        }
        composable("settings") {
            SettingsScreen(navController, viewModel = settingsViewModel)
        }
        composable("history") {
            HistoryScreen(navController, viewModel = browserViewModel)
        }
        composable("bookmarks") {
            BookmarksScreen(navController, viewModel = browserViewModel)
        }
        composable("domain_settings") {
            DomainSettingsScreen(navController, viewModel = browserViewModel)
        }
    }
}
