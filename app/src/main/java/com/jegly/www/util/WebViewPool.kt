package com.jegly.www.util

import android.content.Context
import android.webkit.WebView

/**
 * Process-wide Chromium warm-up.
 *
 * Deliberately holds no WebView instance. BrowserScreen builds one per tab with an Activity
 * context, so anything cached here would never be consumed and would sit holding a live renderer.
 *
 * Constructing any WebView loads the native library and starts the Chromium renderer process, and
 * that cost is paid once per process. Building a throwaway instance and destroying it immediately
 * pays it while the user is still on the unlock screen, so the first real tab has no cold-start
 * stall. Uses the application context deliberately — this instance never renders anything, so the
 * usual "WebViews need an Activity context" rule doesn't apply, and it must not pin the Activity.
 */
object WebViewPool {

    @Volatile private var warmed = false

    fun prime(context: Context) {
        if (warmed) return
        warmed = true
        runCatching { WebView(context.applicationContext).destroy() }
    }
}
