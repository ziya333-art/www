package com.jegly.www.presentation.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jegly.www.security.PasscodeManager
import com.jegly.www.security.patternToPasscode

/**
 * Two-step setup: enter, then confirm. Nothing is written until both match, so a mistyped
 * passcode can't silently become the thing that locks the user out of their own database — there
 * is no recovery path once the passphrase is wrapped.
 */
@Composable
fun PasscodeSetupDialog(
    kind: PasscodeManager.Kind,
    onDismiss: () -> Unit,
    onConfirmed: (CharArray) -> Unit
) {
    var first by remember { mutableStateOf<CharArray?>(null) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val minLength = when (kind) {
        PasscodeManager.Kind.PIN -> PasscodeManager.MIN_PIN_LENGTH
        PasscodeManager.Kind.PASSWORD -> PasscodeManager.MIN_PASSWORD_LENGTH
        else -> 0
    }

    fun submit(candidate: CharArray) {
        val pending = first
        if (pending == null) {
            first = candidate
            text = ""
            error = null
        } else if (pending.concatToString() == candidate.concatToString()) {
            onConfirmed(candidate)
        } else {
            first = null
            text = ""
            error = "They didn't match — start again"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    first == null -> when (kind) {
                        PasscodeManager.Kind.PIN -> "Set a PIN"
                        PasscodeManager.Kind.PASSWORD -> "Set a password"
                        PasscodeManager.Kind.PATTERN -> "Draw a pattern"
                        else -> ""
                    }
                    else -> "Confirm to continue"
                }
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "If you forget this, your history and bookmarks cannot be recovered — they are " +
                        "encrypted with it, not merely hidden behind it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                when (kind) {
                    PasscodeManager.Kind.PATTERN -> PatternGrid(
                        onComplete = { dots ->
                            if (dots.size < PasscodeManager.MIN_PATTERN_DOTS) {
                                error = "Use at least ${PasscodeManager.MIN_PATTERN_DOTS} dots"
                            } else {
                                submit(patternToPasscode(dots))
                            }
                        },
                        dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    PasscodeManager.Kind.PIN, PasscodeManager.Kind.PASSWORD -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            label = { Text(if (kind == PasscodeManager.Kind.PIN) "PIN" else "Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (kind == PasscodeManager.Kind.PIN) {
                                    KeyboardType.NumberPassword
                                } else {
                                    KeyboardType.Password
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Minimum $minLength characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { submit(text.toCharArray()) },
                            enabled = text.length >= minLength
                        ) {
                            Text(if (first == null) "Next" else "Confirm")
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
