package com.jegly.www.presentation.browser

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.jegly.www.util.UrlUtils
import kotlinx.coroutines.launch

/**
 * The long-press menu for a link or an image.
 *
 * A bottom sheet rather than a dropdown anchored at the touch point: the press can land anywhere,
 * including the very bottom of a tall page, and a dropdown there either flips into the finger or
 * runs off screen. This is also where Chrome and Firefox put the same menu on Android, so the
 * gesture lands where the muscle memory expects.
 *
 * Only http/https targets are offered as "open" actions. `javascript:` is the whole reason for that
 * guard — a page can put one behind an innocuous-looking link, and opening it in a new tab would
 * execute page-authored script under the URL bar's authority. The same rule already governs the
 * omnibox (see UrlUtils.BLOCKED_INPUT_SCHEMES) and external intents; this is the third entry point
 * into the same decision. Non-web links can still be copied, which is inert.
 */
@Composable
fun LinkContextSheet(
    target: LinkTarget,
    onOpenInNewTab: (String) -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    val link = target.linkUrl?.takeIf { it.isNotBlank() }
    val image = target.imageUrl?.takeIf { it.isNotBlank() }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Run the action, then let the sheet slide out before the host drops it from composition.
    // Calling onDismiss straight from a row would delete the sheet mid-frame and the exit
    // animation would never play.
    fun choose(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        /*
         * A modal sheet renders in its own window, so its screenshot protection is decided by this
         * policy rather than by the Activity's FLAG_SECURE directly. The default, Inherit, would
         * mirror the Activity — including when the user turns the screenshot-protection setting
         * off. SecureOn instead: the sheet prints the full target URL, and it is on screen for one
         * tap, so protecting it unconditionally costs nothing the user would notice.
         */
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            SheetHeader(link ?: image.orEmpty())

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (link != null && isWebUrl(link)) {
                SheetAction(
                    icon = Icons.Filled.OpenInNew,
                    label = "Open in new tab",
                    onClick = { choose { onOpenInNewTab(link) } }
                )
            }
            if (link != null) {
                SheetAction(
                    icon = Icons.Filled.ContentCopy,
                    label = "Copy link address",
                    onClick = { choose { onCopy("Link", link) } }
                )
            }
            if (image != null && isWebUrl(image)) {
                SheetAction(
                    icon = Icons.Filled.Image,
                    label = "Open image in new tab",
                    onClick = { choose { onOpenInNewTab(image) } }
                )
            }
            if (image != null) {
                SheetAction(
                    icon = Icons.Filled.ContentCopy,
                    label = "Copy image address",
                    onClick = { choose { onCopy("Image", image) } }
                )
            }
        }
    }
}

/**
 * Origin on top, full URL underneath — the same split the URL bar uses, and for the same reason:
 * the origin is the part that carries security meaning, so it gets read first and never truncated,
 * while the path is context and may be elided.
 */
@Composable
private fun SheetHeader(url: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        val origin = UrlUtils.displayOrigin(url)
        Text(
            text = origin,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (url != origin) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun isWebUrl(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}
