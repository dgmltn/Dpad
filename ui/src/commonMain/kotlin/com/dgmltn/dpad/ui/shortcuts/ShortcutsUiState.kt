package com.dgmltn.dpad.ui.shortcuts

import com.dgmltn.dpad.domain.CatalogApp
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ShortcutsUiState(
    val shortcuts: ImmutableList<Shortcut> = persistentListOf(),
    // Catalog apps not yet added — those whose appLinkUrl isn't already a saved shortcut's.
    val catalog: ImmutableList<CatalogApp> = persistentListOf(),
)
