package com.dgmltn.dpad.ui.shortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgmltn.dpad.domain.CatalogApp
import com.dgmltn.dpad.domain.Shortcut
import com.dgmltn.dpad.domain.ShortcutCatalog
import com.dgmltn.dpad.domain.ShortcutRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun defaultId(): String = Uuid.random().toString()

class ShortcutsViewModel(
    private val repository: ShortcutRepository,
    private val newId: () -> String = { defaultId() },
) : ViewModel() {

    // ShortcutCatalog.apps is a constant, so mapping repository.shortcuts alone (rather than
    // combine-ing it with the catalog) is enough to derive both fields.
    val state: StateFlow<ShortcutsUiState> = repository.shortcuts
        .map { shortcuts ->
            val savedUrls = shortcuts.mapTo(HashSet()) { it.appLinkUrl }
            ShortcutsUiState(
                shortcuts = shortcuts.toImmutableList(),
                catalog = ShortcutCatalog.apps.filterNot { it.appLinkUrl in savedUrls }.toImmutableList(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShortcutsUiState())

    fun onAddFromCatalog(app: CatalogApp) {
        viewModelScope.launch {
            repository.add(ShortcutCatalog.toShortcut(app, newId()))
        }
    }

    fun onAddCustom(label: String, appLinkUrl: String) {
        viewModelScope.launch {
            repository.add(Shortcut(newId(), label, appLinkUrl))
        }
    }

    fun onRemove(id: String) {
        viewModelScope.launch {
            repository.remove(id)
        }
    }

    fun onReorder(orderedIds: List<String>) {
        viewModelScope.launch {
            repository.reorder(orderedIds)
        }
    }
}
