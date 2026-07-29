package com.jegly.www.presentation.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jegly.www.security.PasscodeManager

/**
 * The unlock screen shown before the encrypted database is opened.
 *
 * All three input styles submit the same thing — a CharArray handed to PasscodeManager — so the
 * choice between PIN, password and pattern is purely how the secret is entered, not how it is
 * protected. There is no "forgot passcode" path by design: the passphrase is wrapped under the
 * passcode, so losing it means the browsing data is genuinely unrecoverable rather than merely
 * inaccessible.
 */
@Composable
fun UnlockScreen(
    kind: PasscodeManager.Kind,
    errorMessage: String?,
    lockedOutSeconds: Long,
    isVerifying: Boolean,
    onSubmit: (CharArray) -> Unit,
    onUseBiometric: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                "www is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Text(
                when (kind) {
                    PasscodeManager.Kind.PIN -> "Enter your PIN"
                    PasscodeManager.Kind.PASSWORD -> "Enter your password"
                    PasscodeManager.Kind.PATTERN -> "Draw your pattern"
                    PasscodeManager.Kind.NONE -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (lockedOutSeconds > 0) {
                Text(
                    "Too many attempts. Try again in ${formatDuration(lockedOutSeconds)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else if (errorMessage != null) {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Box(modifier = Modifier.padding(top = 24.dp), contentAlignment = Alignment.Center) {
                if (isVerifying) {
                    // PBKDF2 at 210k iterations is deliberately slow; without this the UI would
                    // look frozen for a beat on every attempt.
                    CircularProgressIndicator()
                } else {
                    val enabled = lockedOutSeconds <= 0L
                    when (kind) {
                        PasscodeManager.Kind.PIN -> PinPad(enabled = enabled, onSubmit = onSubmit)
                        PasscodeManager.Kind.PASSWORD -> PasswordEntry(enabled = enabled, onSubmit = onSubmit)
                        PasscodeManager.Kind.PATTERN -> PatternGrid(
                            enabled = enabled,
                            onComplete = { dots -> onSubmit(com.jegly.www.security.patternToPasscode(dots)) },
                            dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            activeColor = MaterialTheme.colorScheme.primary
                        )
                        PasscodeManager.Kind.NONE -> Unit
                    }
                }
            }

            if (onUseBiometric != null && lockedOutSeconds <= 0L && !isVerifying) {
                TextButton(onClick = onUseBiometric, modifier = Modifier.padding(top = 20.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Text("Use biometric", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onSubmit: (CharArray) -> Unit) {
    var entry by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "•".repeat(entry.length).ifEmpty { " " },
            style = MaterialTheme.typography.headlineMedium,
            letterSpacing = 8.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        listOf(listOf('1','2','3'), listOf('4','5','6'), listOf('7','8','9')).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit ->
                    PinKey(digit.toString(), enabled) { entry += digit }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
                enabled = enabled && entry.isNotEmpty(),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
            }
            PinKey("0", enabled) { entry += '0' }
            IconButton(
                onClick = {
                    val code = entry.toCharArray()
                    entry = ""
                    onSubmit(code)
                },
                enabled = enabled && entry.length >= PasscodeManager.MIN_PIN_LENGTH,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Unlock")
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier
            .padding(4.dp)
            .size(64.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PasswordEntry(enabled: Boolean, onSubmit: (CharArray) -> Unit) {
    var value by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            enabled = enabled,
            singleLine = true,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onGo = {
                val code = value.toCharArray()
                value = ""
                onSubmit(code)
            }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val code = value.toCharArray()
                value = ""
                onSubmit(code)
            },
            enabled = enabled && value.length >= PasscodeManager.MIN_PASSWORD_LENGTH,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Unlock")
        }
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
