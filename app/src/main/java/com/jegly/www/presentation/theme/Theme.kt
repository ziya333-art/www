package com.jegly.www.presentation.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jegly.www.presentation.settings.SettingsViewModel

// Material You fallback colours (used only when dynamic colour is unavailable and theme = system).
private val FallbackDark = darkColorScheme(
    primary   = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary  = Color(0xFFEFB8C8)
)
private val FallbackLight = lightColorScheme(
    primary   = Color(0xFF6650A4),
    secondary = Color(0xFF625B71),
    tertiary  = Color(0xFF7D5260)
)

@Composable
fun WwwTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val fontSize        by settingsViewModel.fontSize.collectAsState()
    val fontFamily      by settingsViewModel.fontFamily.collectAsState()
    val themeMode       by settingsViewModel.themeMode.collectAsState()
    val catppuccinAccent by settingsViewModel.catppuccinAccent.collectAsState()
    val catppuccinFlavor by settingsViewModel.catppuccinFlavor.collectAsState()
    val draculaAccent   by settingsViewModel.draculaAccent.collectAsState()
    val ptyxisPalette   by settingsViewModel.ptyxisPalette.collectAsState()
    val paperAccents    by settingsViewModel.paperAccents.collectAsState()

    val colorScheme = when {
        themeMode == "catppuccin" -> catppuccinColorScheme(catppuccinFlavor, catppuccinAccent)
        themeMode == "dracula"    -> draculaColorScheme(draculaAccent)
        themeMode == "ptyxis"     -> ptyxisColorScheme(ptyxisPalette)
        // Rosé Pine / Everforest / Kanagawa. Light-only by design — darkTheme is deliberately not
        // consulted here, exactly as the three dark themes above ignore it in the other direction.
        isPaperTheme(themeMode) -> paperColorScheme(
            themeMode,
            paperAccents[themeMode] ?: paperDefaultAccent(themeMode)
        )
        else -> {
            // System / Material You
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) FallbackDark else FallbackLight
            }
        }
    }

    /*
     * System-bar icon colour.
     *
     * enableEdgeToEdge() in MainActivity picks this from the *device's* dark-mode setting, which is
     * the wrong signal the moment the in-app theme disagrees with it: a light theme on a device in
     * dark mode gets white status-bar icons drawn over a near-white bar, i.e. an invisible clock and
     * battery. That was already reachable with Catppuccin Latte and becomes the common case now that
     * three light themes exist, so the flag follows the resolved scheme instead of the OS.
     *
     * Keyed off the scheme's own background luminance rather than a theme-name list, so it stays
     * correct for Material You (whose lightness is the system's to decide) and for any palette added
     * later without anyone having to remember this code exists.
     */
    val view = LocalView.current
    val lightBars = colorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
                ?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = getTypography(fontSize, fontFamily),
        content     = content
    )
}
