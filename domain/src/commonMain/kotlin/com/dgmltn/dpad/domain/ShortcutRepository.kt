package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.Flow

interface ShortcutRepository {
    val shortcuts: Flow<List<Shortcut>>           // ordered
    suspend fun add(shortcut: Shortcut)
    suspend fun remove(id: String)
    suspend fun reorder(orderedIds: List<String>)
}
