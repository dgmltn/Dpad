package com.dgmltn.dpad.ui.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgmltn.dpad.design.DpadTheme
import com.dgmltn.dpad.domain.CatalogApp
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: collects [ShortcutsViewModel.state] and wires its actions to [ShortcutsContent]. */
@Composable
fun ShortcutsScreen(
    onBack: () -> Unit,
    vm: ShortcutsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    ShortcutsContent(
        state = state,
        onAddFromCatalog = vm::onAddFromCatalog,
        onAddCustom = vm::onAddCustom,
        onRemove = vm::onRemove,
        onReorder = vm::onReorder,
        onBack = onBack,
    )
}

/** Shortcut list editor + catalog + custom-add dialog. Stateless: pure inputs -> UI. This is the preview surface. */
@Composable
fun ShortcutsContent(
    state: ShortcutsUiState,
    onAddFromCatalog: (CatalogApp) -> Unit,
    onAddCustom: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    var showAddCustomDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ShortcutsTopBar(onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                item(key = "shortcuts-header") { SectionHeader(title = "Your shortcuts") }
                if (state.shortcuts.isEmpty()) {
                    item(key = "shortcuts-empty") { EmptyShortcutsMessage() }
                } else {
                    val ids = state.shortcuts.map { it.id }
                    itemsIndexed(state.shortcuts, key = { _, shortcut -> shortcut.id }) { index, shortcut ->
                        ShortcutEditRow(
                            label = shortcut.label,
                            canMoveUp = index > 0,
                            canMoveDown = index < ids.lastIndex,
                            onMoveUp = { onReorder(swapped(ids, index, index - 1)) },
                            onMoveDown = { onReorder(swapped(ids, index, index + 1)) },
                            onRemove = { onRemove(shortcut.id) },
                        )
                    }
                }

                item(key = "catalog-header") { SectionHeader(title = "Add from catalog") }
                items(state.catalog, key = { it.key }) { app ->
                    CatalogAppRow(
                        label = app.label,
                        onClick = { onAddFromCatalog(app) },
                    )
                }

                item(key = "add-custom") {
                    AddCustomRow(onClick = { showAddCustomDialog = true })
                }
            }
        }
    }

    if (showAddCustomDialog) {
        AddCustomShortcutDialog(
            onDismiss = { showAddCustomDialog = false },
            onConfirm = { label, appLinkUrl ->
                onAddCustom(label, appLinkUrl)
                showAddCustomDialog = false
            },
        )
    }
}

/** Returns [ids] with the elements at [from] and [to] swapped. Both indices must be valid. */
private fun swapped(ids: List<String>, from: Int, to: Int): List<String> =
    ids.toMutableList().apply {
        val tmp = this[from]
        this[from] = this[to]
        this[to] = tmp
    }

@Composable
private fun ShortcutsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = "Shortcuts",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyShortcutsMessage() {
    Text(
        text = "No shortcuts yet. Add one from the catalog below.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** A single saved shortcut, editable in place. Lean params: primitives only, not the whole [Shortcut]. */
@Composable
fun ShortcutEditRow(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move up",
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move down",
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A single catalog entry, not yet added. Lean params: primitives only, not the whole [CatalogApp]. */
@Composable
fun CatalogAppRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AddCustomRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Add custom…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AddCustomShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var appLinkUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                TextField(
                    value = appLinkUrl,
                    onValueChange = { appLinkUrl = it },
                    label = { Text("App link URL") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, appLinkUrl) },
                enabled = label.isNotBlank() && appLinkUrl.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Preview
@Composable
private fun Preview_ShortcutsContent_Empty() {
    DpadTheme {
        ShortcutsContent(
            state = ShortcutsUiState(
                shortcuts = persistentListOf(),
                catalog = persistentListOf(
                    CatalogApp("netflix", "Netflix", "https://www.netflix.com/title"),
                    CatalogApp("youtube", "YouTube", "https://www.youtube.com"),
                ),
            ),
            onAddFromCatalog = {},
            onAddCustom = { _, _ -> },
            onRemove = {},
            onReorder = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun Preview_ShortcutsContent_Populated() {
    DpadTheme {
        ShortcutsContent(
            state = ShortcutsUiState(
                shortcuts = persistentListOf(
                    Shortcut(id = "1", label = "Netflix", appLinkUrl = "https://www.netflix.com/title"),
                    Shortcut(id = "2", label = "YouTube", appLinkUrl = "https://www.youtube.com"),
                    Shortcut(id = "3", label = "Plex", appLinkUrl = "https://app.plex.tv"),
                ),
                catalog = persistentListOf(
                    CatalogApp("disneyplus", "Disney+", "https://www.disneyplus.com"),
                    CatalogApp("spotify", "Spotify", "https://open.spotify.com"),
                ),
            ),
            onAddFromCatalog = {},
            onAddCustom = { _, _ -> },
            onRemove = {},
            onReorder = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun Preview_ShortcutEditRow() {
    DpadTheme {
        ShortcutEditRow(
            label = "Netflix",
            canMoveUp = false,
            canMoveDown = true,
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {},
        )
    }
}

@Preview
@Composable
private fun Preview_CatalogAppRow() {
    DpadTheme {
        CatalogAppRow(label = "Disney+", onClick = {})
    }
}
