package com.jegly.www

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.jegly.www.presentation.navigation.NavGraph
import com.jegly.www.util.WebViewPool
import com.jegly.www.presentation.theme.WwwTheme
import com.jegly.www.presentation.theme.paperBaseArgb
import com.jegly.www.security.BiometricAuthManager
import com.jegly.www.security.EncryptionManager
import com.jegly.www.security.IntegrityChecker
import com.jegly.www.security.PassphraseGate
import com.jegly.www.security.PasscodeManager
import com.jegly.www.presentation.lock.UnlockScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var biometricAuthManager: BiometricAuthManager
    @Inject lateinit var encryptionManager: EncryptionManager
    @Inject lateinit var passphraseGate: PassphraseGate
    @Inject lateinit var passcodeManager: PasscodeManager

    // elapsedRealtime: monotonic, immune to NTP / user clock changes, counts during sleep.
    private var lastBackgroundElapsed: Long = 0
    private var isAuthenticated = false

    private var suspiciousAccessibilityPackages by mutableStateOf<List<String>>(emptyList())
    private var integrityFailed by mutableStateOf(false)

    /** URL handed in by a VIEW or SEND intent, consumed once by the browser screen. */
    private var pendingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateWindowBackgroundForTheme()
        enableEdgeToEdge()

        updateScreenshotProtection()
        window.decorView.filterTouchesWhenObscured = true

        suspiciousAccessibilityPackages = scanAccessibilityServices()
        integrityFailed = !IntegrityChecker.verifySignature(this)
        pendingUrl = extractUrl(intent)
    }

    /**
     * launchMode is singleTask, so a link tapped while www is already running arrives here rather
     * than through onCreate. Without this, the second and every subsequent external link would be
     * silently dropped and the user would just see whatever tab was already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUrl(intent)?.let { pendingUrl = it }
    }

    /**
     * Only http/https are accepted from external callers. Any other scheme — file:, content:,
     * javascript:, or an app-specific one — is dropped: honouring those would let any installed
     * app steer the browser at local files or run script in whatever origin was loaded.
     */
    private fun extractUrl(intent: Intent?): String? {
        val raw = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            else -> null
        } ?: return null

        val scheme = runCatching { android.net.Uri.parse(raw).scheme?.lowercase() }.getOrNull()
        return if (scheme == "http" || scheme == "https") raw else null
    }

    private fun updateScreenshotProtection() {
        val isProtected = encryptionManager.getBoolean("screenshot_protection", true)
        if (isProtected) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Overrides the window background to match whichever theme is actually selected, rather than
     * the hardcoded black in themes.xml.
     *
     * EncryptionManager is safe to read here, before any auth: it's backed by a Keystore MasterKey
     * that the OS unlocks transparently for this app's own process, which is a completely separate
     * secret from PassphraseGate's SQLCipher database passphrase (the one the biometric/PIN prompt
     * actually gates). Reading "theme_mode" here needs no user interaction — it's the same reason
     * updateScreenshotProtection() above can read "screenshot_protection" before auth too.
     *
     * The "dark" derivation mirrors WwwTheme/ThemeColors.kt/PtyxisThemes.kt exactly: Catppuccin is
     * dark for every flavour except Latte; Dracula and Ptyxis have no light variant at all — every
     * one of their palettes resolves through darkColorScheme() regardless of which accent/palette
     * is chosen. The paper themes are handled before that split entirely: their grounds are tinted
     * (off-white, cream, parchment), and flashing plain white in front of one is the same artefact
     * this whole method exists to avoid, so they supply their own colour.
     *
     * This closes the gap for every frame after onCreate/onResume run. It cannot touch the OS's own
     * "starting window" splash drawn before any of our code executes — values-night/themes.xml is
     * the only lever for that sliver, and it can only follow the system setting, not this in-app
     * pick. That's a hard platform limitation, not something left unfinished here.
     */
    private fun updateWindowBackgroundForTheme() {
        val themeMode = encryptionManager.getString("theme_mode") ?: "system"

        paperBaseArgb(themeMode)?.let { argb ->
            window.setBackgroundDrawable(ColorDrawable(argb))
            return
        }

        val isDark = when (themeMode) {
            "catppuccin" -> (encryptionManager.getString("catppuccin_flavor") ?: "mocha") != "latte"
            "dracula", "ptyxis" -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(ColorDrawable(if (isDark) Color.BLACK else Color.WHITE))
    }

    private fun scanAccessibilityServices(): List<String> {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .map { it.resolveInfo.serviceInfo.packageName }
            .filterNot { it.startsWith("com.google.") || it.startsWith("com.android.") }
    }

    override fun onPause() {
        super.onPause()
        lastBackgroundElapsed = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        updateWindowBackgroundForTheme()
        updateScreenshotProtection()

        val useBiometrics = encryptionManager.getBoolean("use_biometrics", false)
        val now = SystemClock.elapsedRealtime()
        val shouldReauth = lastBackgroundElapsed != 0L && (now - lastBackgroundElapsed > REAUTH_GRACE_MS)

        if (passcodeManager.isEnabled() && (shouldReauth || !isAuthenticated)) {
            // A knowledge factor is the strongest thing configured, so it gates first. Biometric,
            // if also enabled, is offered from within the unlock screen as a shortcut.
            isAuthenticated = false
            showUnlockScreen()
        } else if (useBiometrics && (shouldReauth || !isAuthenticated)) {
            isAuthenticated = false
            authenticateUser()
        } else {
            // Non-biometric path: supply plain passphrase to the gate (idempotent) and render.
            if (!passphraseGate.isOpen()) {
                passphraseGate.supply(passphraseGate.loadOrCreatePlainPassphrase())
            }
            isAuthenticated = true
            renderApp()
        }
    }

    private fun authenticateUser() {
        if (!biometricAuthManager.isBiometricAvailable()) {
            // Biometric requested but unavailable — fall back to plain (better than locking out).
            if (!passphraseGate.isOpen()) {
                passphraseGate.supply(passphraseGate.loadOrCreatePlainPassphrase())
            }
            isAuthenticated = true
            renderApp()
            return
        }

        // Crypto-bound flow: only if a biometric-wrapped passphrase has been enrolled.
        if (passphraseGate.hasBiometricWrappedPassphrase()) {
            val cipher = try {
                passphraseGate.makeDecryptionCipher()
            } catch (e: KeyPermanentlyInvalidatedException) {
                // The Keystore key was permanently invalidated (biometric enrolment changed).
                // Roll back to plain mode so the user isn't locked out, but require an explicit
                // tap acknowledging the reset rather than silently granting access.
                passphraseGate.clearBiometricWrap()
                encryptionManager.saveBoolean("use_biometrics", false)
                showBiometricResetPrompt()
                return
            }
            if (cipher == null) {
                // Unexpected: a wrapped passphrase blob exists but its IV is missing/corrupt.
                // Fail closed rather than silently granting access with no auth factor.
                showAuthFailedPrompt("Could not verify biometric protection. Please restart the app.")
                return
            }
            biometricAuthManager.showCryptoPrompt(
                activity = this,
                cipher = cipher,
                onSuccess = { unlockedCipher ->
                    val passphrase = passphraseGate.unwrapPlainPassphraseWithCipher(unlockedCipher)
                    if (passphrase == null) { finish(); return@showCryptoPrompt }
                    if (!passphraseGate.isOpen()) passphraseGate.supply(passphrase)
                    isAuthenticated = true
                    renderApp()
                },
                onError = { finish() }
            )
        } else {
            // Biometric is enabled in settings but no wrapped passphrase exists yet — UI-only prompt
            // to enforce the user-presence gate, then load the plain passphrase.
            biometricAuthManager.showBiometricPrompt(this,
                onSuccess = {
                    if (!passphraseGate.isOpen()) {
                        passphraseGate.supply(passphraseGate.loadOrCreatePlainPassphrase())
                    }
                    isAuthenticated = true
                    renderApp()
                },
                onError = { finish() }
            )
        }
    }

    /**
     * Passcode unlock. PBKDF2 runs on Dispatchers.Default — at 210k iterations it is far too slow
     * for the main thread, and blocking it here would also stall the very UI showing the spinner.
     */
    private fun showUnlockScreen() {
        setContent {
            WwwTheme {
                var error by remember { mutableStateOf<String?>(null) }
                var verifying by remember { mutableStateOf(false) }
                var lockoutSeconds by remember { mutableLongStateOf(0L) }
                val scope = rememberCoroutineScope()

                // Tick down any active lock-out so the UI unblocks on its own.
                LaunchedEffect(lockoutSeconds) {
                    if (lockoutSeconds > 0) {
                        kotlinx.coroutines.delay(1000)
                        lockoutSeconds -= 1
                    }
                }

                LaunchedEffect(Unit) {
                    val attempts = passcodeManager.failedAttempts()
                    lockoutSeconds = passcodeManager.lockoutMillisFor(attempts) / 1000
                }

                UnlockScreen(
                    kind = passcodeManager.kind(),
                    errorMessage = error,
                    lockedOutSeconds = lockoutSeconds,
                    isVerifying = verifying,
                    onSubmit = { code ->
                        verifying = true
                        error = null
                        scope.launch {
                            val passphrase = withContext(Dispatchers.Default) {
                                passcodeManager.unwrap(code)
                            }
                            verifying = false
                            if (passphrase != null) {
                                passcodeManager.resetFailures()
                                if (!passphraseGate.isOpen()) passphraseGate.supply(passphrase)
                                isAuthenticated = true
                                renderApp()
                            } else {
                                val attempts = passcodeManager.recordFailure()
                                val lockMs = passcodeManager.lockoutMillisFor(attempts)
                                lockoutSeconds = lockMs / 1000
                                error = if (lockMs > 0) null else "Incorrect — try again"
                            }
                        }
                    },
                    onUseBiometric = if (encryptionManager.getBoolean("use_biometrics", false) &&
                        passphraseGate.hasBiometricWrappedPassphrase()
                    ) {
                        { authenticateUser() }
                    } else null
                )
            }
        }
    }

    private fun showBiometricResetPrompt() {
        setContent {
            WwwTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BiometricResetDialog(
                        onContinue = {
                            if (!passphraseGate.isOpen()) {
                                passphraseGate.supply(passphraseGate.loadOrCreatePlainPassphrase())
                            }
                            isAuthenticated = true
                            renderApp()
                        },
                        onExit = { finish() }
                    )
                }
            }
        }
    }

    private fun showAuthFailedPrompt(message: String) {
        setContent {
            WwwTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AuthFailedDialog(message = message, onDismiss = { finish() })
                }
            }
        }
    }

    private fun renderApp() {
        // Prime the WebView renderer while the user is on the home screen so the first article
        // tap has no Chromium cold-start delay. Posted so it doesn't block setContent.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            WebViewPool.prime(this)
        }
        setContent {
            WwwTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Block the app until the user acknowledges any tamper / accessibility warnings.
                    when {
                        integrityFailed -> IntegrityFailedDialog(onDismiss = { finish() })
                        suspiciousAccessibilityPackages.isNotEmpty() -> AccessibilityWarningDialog(
                            packages = suspiciousAccessibilityPackages,
                            onContinue = { suspiciousAccessibilityPackages = emptyList() },
                            onExit = { finish() }
                        )
                        else -> NavGraph(initialUrl = pendingUrl)
                    }
                }
            }
        }
    }

    companion object {
        private const val REAUTH_GRACE_MS = 30_000L
    }
}

@androidx.compose.runtime.Composable
private fun IntegrityFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tampered installation") },
        text = { Text("This APK was not signed by the expected developer key. Refusing to run.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Exit") } }
    )
}

@androidx.compose.runtime.Composable
private fun BiometricResetDialog(onContinue: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onExit,
        title = { Text("Biometric protection reset") },
        text = {
            Text(
                "Your device's biometric enrolment changed, so this app can no longer use it to " +
                    "unlock your feeds. Tap Continue to unlock with your fallback passphrase, then " +
                    "re-enable biometrics from Settings if you'd like to use it again."
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onExit) { Text("Exit") } }
    )
}

@androidx.compose.runtime.Composable
private fun AuthFailedDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authentication error") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Exit") } }
    )
}

@androidx.compose.runtime.Composable
private fun AccessibilityWarningDialog(
    packages: List<String>,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onExit,
        title = { Text("Security Warning") },
        text = {
            Text(
                "The following accessibility services are active and can read or interact with " +
                    "screen content:\n\n" + packages.joinToString("\n") { "• $it" } +
                    "\n\nDisable them in Settings → Accessibility before continuing if you don't recognise them."
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue anyway") } },
        dismissButton = { TextButton(onClick = onExit) { Text("Exit") } }
    )
}
