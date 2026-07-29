package com.jegly.www.presentation.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jegly.www.util.UrlUtils

/**
 * The whole top bar, address field and navigation actions together in one row — matching Chrome
 * and Firefox's compact single-row toolbar rather than the two-row layout this had before.
 *
 * The omnibox is a `BasicTextField` in a fixed-height `Box`, not Material3's `TextField` — the
 * convenience `TextField` composable carries generous internal content padding intended for a
 * labelled form field, sized well past what a single-line address bar needs, and that padding
 * isn't something the public API lets you override directly. `BasicTextField` has no built-in
 * chrome at all, so the height below is the actual rendered height, not a floor under default
 * padding. There is no placeholder text either — an empty field is just empty.
 *
 * No background or status-bar inset handling here: BrowserScreen's Surface owns the background
 * colour and the status-bar inset for this whole bar.
 */
@Composable
fun UrlBar(
    tab: TabState,
    tabCount: Int,
    isBookmarked: Boolean,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onToggleBookmark: (currentlyBookmarked: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDomainSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var editingText by remember(tab.id) { mutableStateOf(tab.url) }
    var menuOpen by remember { mutableStateOf(false) }
    var showSiteInfo by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // While the user is typing, don't let a background navigation overwrite the field under them.
    LaunchedEffect(tab.url, isFocused) {
        if (!isFocused) editingText = tab.url
    }

    val displayValue = if (isFocused) editingText else UrlUtils.displayOrigin(tab.url)
    val iconSize = Modifier.size(36.dp) // smaller than IconButton's 48dp default: 8 elements share this row

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Literal chevrons rather than the Material arrow glyphs — the arrows read as heavy
            // next to the rest of this row, and a bare < / > is the same affordance at a fraction
            // of the visual weight. Alpha (not colour) carries the disabled state so the two match
            // the icon buttons around them, which tint the same way.
            IconButton(onClick = onBack, enabled = tab.canGoBack, modifier = iconSize) {
                Text(
                    "<",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalContentColor.current
                )
            }
            IconButton(onClick = onForward, enabled = tab.canGoForward, modifier = iconSize) {
                Text(
                    ">",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalContentColor.current
                )
            }

            // Nothing security-relevant to show on a blank tab — a static, non-tappable icon there
            // was pure decoration with no function, so it's omitted rather than shown.
            if (tab.url.isNotBlank()) {
                SecurityIndicator(tab = tab, onClick = { showSiteInfo = true })
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = displayValue,
                    onValueChange = { editingText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused && !isFocused) editingText = tab.url
                            isFocused = state.isFocused
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onGo = { onNavigate(editingText) })
                )
            }

            when {
                isFocused && editingText.isNotEmpty() ->
                    IconButton(onClick = { editingText = "" }, modifier = iconSize) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                tab.isLoading ->
                    IconButton(onClick = onStop, modifier = iconSize) {
                        MinimalStop()
                    }
                tab.url.isNotBlank() ->
                    IconButton(onClick = onReload, modifier = iconSize) {
                        FigureEightReload()
                    }
            }

            IconButton(onClick = onNewTab, modifier = iconSize) {
                // A literal drawn "+", matching the chevrons rather than the heavier Material Add
                // glyph — same minimal, text-weight language as the rest of this row now.
                Text("+", style = MaterialTheme.typography.titleLarge, color = LocalContentColor.current)
            }
            IconButton(onClick = onShowTabs, modifier = iconSize) {
                TabCountBadge(count = tabCount)
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = iconSize) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove bookmark" else "Save page") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null
                            )
                        },
                        enabled = tab.url.isNotBlank(),
                        onClick = { menuOpen = false; onToggleBookmark(isBookmarked) }
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenBookmarks() }
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenHistory() }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenSettings() }
                    )
                }
            }
        }

        if (showSiteInfo) {
            SiteInfoDialog(
                tab = tab,
                onDismiss = { showSiteInfo = false },
                onOpenDomainSettings = onOpenDomainSettings
            )
        }

        if (tab.isLoading && tab.progress in 1..99) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

/** Chrome-style boxed tab count. Caps the label at 99 so the box never changes width. */
@Composable
private fun TabCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99" else count.toString(),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The reload affordance: a small open-ended arc, drawn rather than taken from the Material set,
 * whose circular refresh glyph sat visually heavier than everything else sharing this row.
 *
 * Deliberately smaller and lighter than the figure-eight this replaced: three-quarters of a circle
 * left open at the top, thin stroke, no arrowhead — a minimal reload cue rather than a literal
 * rotation icon.
 */
@Composable
private fun FigureEightReload() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(11.dp)) {
        val w = size.width
        val h = size.height
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.84f, h * 0.84f),
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        )
    }
}

/**
 * The stop-loading affordance: a small filled square, the same shape used for "stop" on physical
 * media controls, replacing the Material "X" — an X reads as dismiss/cancel/close, not stop, and it
 * shared its glyph with the adjacent clear-text-field button, which made the row ambiguous at a
 * glance while a page was loading.
 */
@Composable
private fun MinimalStop() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(10.dp)) {
        drawRoundRect(color = color, cornerRadius = CornerRadius(size.width * 0.15f))
    }
}

/**
 * Three real states — the blank-tab magnifying glass is gone (the caller only renders this when
 * `tab.url` is non-blank now), but all three security states below are still reachable and stay:
 * a cert-error warning, a lock for HTTPS, and an open lock for HTTP. That last one is not
 * theoretical — "Force HTTPS Only" is a user-facing Settings toggle, and turning it off makes
 * `BrowserViewModel.resolveInput()` hand back genuine, sustained `http://` destinations (see
 * `UrlUtils.parse`'s `defaultToHttps` parameter); nothing upgrades that scheme afterwards.
 */
@Composable
private fun SecurityIndicator(tab: TabState, onClick: () -> Unit) {
    val (filled, tint, exclaim, description) = if (tab.hasSslError) {
        Quad(true, MaterialTheme.colorScheme.error, true, "Certificate invalid — page blocked")
    } else if (UrlUtils.isSecure(tab.url)) {
        Quad(true, MaterialTheme.colorScheme.primary, false, "Connection is encrypted")
    } else {
        Quad(false, MaterialTheme.colorScheme.error, false, "Connection is not encrypted")
    }

    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        MinimalShield(
            filled = filled,
            tint = tint,
            showExclamation = exclaim,
            contentDescription = description
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * A minimal shield, drawn rather than taken from the Material set, standing in for lock/open-lock/
 * warning. Solid fill reads as "protected" (HTTPS); an outline-only shield reads as "not protected"
 * (HTTP) — the same semantic the padlock used (closed vs open), translated to fill vs hollow instead
 * of two different silhouettes, so both states share one shape. The cert-error case layers a small
 * exclamation on top rather than getting a fourth shape, since it is the encrypted case gone wrong,
 * not an unrelated third state.
 */
@Composable
private fun MinimalShield(
    filled: Boolean,
    tint: Color,
    showExclamation: Boolean,
    contentDescription: String
) {
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            cubicTo(w * 0.5f, h * 0.05f, w * 0.78f, h * 0.16f, w * 0.86f, h * 0.18f)
            lineTo(w * 0.86f, h * 0.46f)
            cubicTo(w * 0.86f, h * 0.72f, w * 0.68f, h * 0.88f, w * 0.5f, h * 0.96f)
            cubicTo(w * 0.32f, h * 0.88f, w * 0.14f, h * 0.72f, w * 0.14f, h * 0.46f)
            lineTo(w * 0.14f, h * 0.18f)
            cubicTo(w * 0.22f, h * 0.16f, w * 0.5f, h * 0.05f, w * 0.5f, h * 0.05f)
            close()
        }
        if (filled) {
            drawPath(path, color = tint)
        } else {
            drawPath(path, color = tint, style = Stroke(width = w * 0.1f, join = StrokeJoin.Round))
        }
        if (showExclamation) {
            val markColor = if (filled) Color.White else tint
            drawLine(
                color = markColor,
                start = Offset(w * 0.5f, h * 0.32f),
                end = Offset(w * 0.5f, h * 0.56f),
                strokeWidth = w * 0.1f,
                cap = StrokeCap.Round
            )
            drawCircle(color = markColor, radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.7f))
        }
    }
}
