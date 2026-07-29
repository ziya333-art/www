package com.jegly.www.presentation.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jegly.www.data.local.DomainSettingEntity
import com.jegly.www.network.UserAgentTemplate

/**
 * Per-domain overrides.
 *
 * Every control is a tri-state (Default / On / Off) rather than a switch, because "inherit the
 * global setting" has to be distinguishable from "explicitly off" — otherwise changing a global
 * default would silently change behaviour on every domain the user had already tuned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainSettingsScreen(
    navController: NavController,
    viewModel: BrowserViewModel
) {
    val domains by viewModel.domainSettings.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<DomainSettingEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Domain Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add domain")
            }
        }
    ) { padding ->
        if (domains.isEmpty()) {
            EmptyState(
                message = "No domain rules.\nAdd one to override global settings for a site.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(domains, key = { it.id }) { entry ->
                ListItem(
                    modifier = Modifier.clickable {
                        editing = entry
                        showEditor = true
                    },
                    headlineContent = {
                        Text(entry.domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            summarise(entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.deleteDomainSetting(entry) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (showEditor) {
        DomainSettingEditor(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = {
                viewModel.saveDomainSetting(it)
                showEditor = false
            }
        )
    }
}

/** One-line description of which overrides are set, so the list is scannable. */
private fun summarise(entry: DomainSettingEntity): String {
    val parts = buildList {
        entry.javaScriptEnabled?.let { add("JS ${onOff(it)}") }
        entry.domStorageEnabled?.let { add("DOM ${onOff(it)}") }
        entry.cookiePolicy?.let { add("cookies $it") }
        entry.blockThirdPartyRequests?.let { add("3rd-party ${if (it) "blocked" else "allowed"}") }
        entry.blockTrackers?.let { add("trackers ${if (it) "blocked" else "allowed"}") }
        entry.stripTrackingQueries?.let { add("query-strip ${onOff(it)}") }
        entry.displayImages?.let { add("images ${onOff(it)}") }
        entry.userAgentKey?.let { add("UA ${UserAgentTemplate.fromKey(it).displayName}") }
    }
    return if (parts.isEmpty()) "No overrides (inherits all globals)" else parts.joinToString(" · ")
}

private fun onOff(value: Boolean) = if (value) "on" else "off"

@Composable
private fun DomainSettingEditor(
    existing: DomainSettingEntity?,
    onDismiss: () -> Unit,
    onSave: (DomainSettingEntity) -> Unit
) {
    var domain by remember { mutableStateOf(existing?.domain ?: "") }
    var javaScript by remember { mutableStateOf(existing?.javaScriptEnabled) }
    var domStorage by remember { mutableStateOf(existing?.domStorageEnabled) }
    var thirdParty by remember { mutableStateOf(existing?.blockThirdPartyRequests) }
    var trackers by remember { mutableStateOf(existing?.blockTrackers) }
    var stripQueries by remember { mutableStateOf(existing?.stripTrackingQueries) }
    var images by remember { mutableStateOf(existing?.displayImages) }
    var userAgentKey by remember { mutableStateOf(existing?.userAgentKey) }
    var showUaPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add domain rule" else "Edit domain rule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com or *.example.com") },
                    singleLine = true,
                    enabled = existing == null,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "A leading *. also matches subdomains. The most specific rule wins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TriStateRow("JavaScript", javaScript) { javaScript = it }
                TriStateRow("Site data (DOM storage)", domStorage) { domStorage = it }
                TriStateRow("Block third-party requests", thirdParty) { thirdParty = it }
                TriStateRow("Block trackers & ads", trackers) { trackers = it }
                TriStateRow("Strip tracking queries", stripQueries) { stripQueries = it }
                TriStateRow("Display images", images) { images = it }

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("User agent", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showUaPicker = true }) {
                        Text(
                            userAgentKey?.let { UserAgentTemplate.fromKey(it).displayName }
                                ?: "Default (inherit global)"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = domain.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: DomainSettingEntity(domain = domain)).copy(
                            domain = domain,
                            javaScriptEnabled = javaScript,
                            domStorageEnabled = domStorage,
                            blockThirdPartyRequests = thirdParty,
                            blockTrackers = trackers,
                            stripTrackingQueries = stripQueries,
                            displayImages = images,
                            userAgentKey = userAgentKey
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showUaPicker) {
        AlertDialog(
            onDismissRequest = { showUaPicker = false },
            title = { Text("User agent for this site") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Null entry first: "inherit whatever the global setting is", same tri-state
                    // philosophy as the switches above.
                    TextButton(onClick = { userAgentKey = null; showUaPicker = false }) {
                        Text("Default (inherit global)")
                    }
                    UserAgentTemplate.entries.forEach { template ->
                        TextButton(onClick = { userAgentKey = template.key; showUaPicker = false }) {
                            Text(template.displayName)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUaPicker = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Default / On / Off. Null is "Default" — inherit whatever the global setting currently is.
 *
 * Plain FilterChips rather than SegmentedButton/SingleChoiceSegmentedButtonRow: that API was
 * reshaped in Material3 1.4.0 and the old call shape no longer resolves. Chips need no exact
 * overload match and give the same three-way exclusive selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateRow(
    label: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf<Pair<String, Boolean?>>(
                "Default" to null,
                "On" to true,
                "Off" to false
            )
            options.forEach { (text, optionValue) ->
                FilterChip(
                    selected = value == optionValue,
                    onClick = { onChange(optionValue) },
                    label = { Text(text) }
                )
            }
        }
    }
}
