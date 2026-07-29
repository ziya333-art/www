package com.jegly.www.security

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Advanced Protection Mode (AAPM) is a device-wide opt-in security posture, added in API
 * 36 — this app's minSdk is 33, so [android.security.advancedprotection.AdvancedProtectionManager]
 * must never be referenced outside an SDK_INT-guarded call, including as a field type: a field
 * typed with a class absent on the running OS risks the verifier resolving it eagerly at class
 * load, not just at first use, on devices below API 36. [registerOnApi36] keeps every reference
 * to that type local to a guarded method for exactly this reason.
 *
 * This is read-only awareness, not control: setAdvancedProtectionEnabled() is `@SystemApi`,
 * reserved for the Settings app — a third-party app can observe AAPM but never turn it on. What
 * this app does with that awareness (locking specific Settings toggles) lives in
 * SettingsViewModel, not here.
 */
@Singleton
class AdvancedProtectionGate @Inject constructor(@ApplicationContext context: Context) {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= 36) {
            registerOnApi36(context)
        }
    }

    private fun registerOnApi36(context: Context) {
        // QUERY_ADVANCED_PROTECTION_MODE may not be grantable to a third-party app at all on some
        // builds; if so this throws or getSystemService returns null, and staying at the false
        // default is the correct, safe outcome either way.
        runCatching {
            val manager = context.getSystemService(
                android.security.advancedprotection.AdvancedProtectionManager::class.java
            ) ?: return@runCatching
            // The callback fires once immediately with the current state on registration, so no
            // separate initial read is needed.
            manager.registerAdvancedProtectionCallback(
                context.mainExecutor,
                android.security.advancedprotection.AdvancedProtectionManager.Callback { isEnabled ->
                    _enabled.value = isEnabled
                }
            )
        }
    }
}
