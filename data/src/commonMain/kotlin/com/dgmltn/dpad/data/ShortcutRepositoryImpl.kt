package com.dgmltn.dpad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dgmltn.dpad.data.store.ShortcutDto
import com.dgmltn.dpad.data.store.toDomain
import com.dgmltn.dpad.data.store.toDto
import com.dgmltn.dpad.domain.Shortcut
import com.dgmltn.dpad.domain.ShortcutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ShortcutRepositoryImpl(private val store: DataStore<Preferences>) : ShortcutRepository {
    private val key = stringPreferencesKey("shortcuts")
    private val json = Json

    override val shortcuts: Flow<List<Shortcut>> =
        store.data.map { prefs -> decode(prefs[key]).map { it.toDomain() } }

    override suspend fun add(shortcut: Shortcut) {
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]) + shortcut.toDto())
        }
    }

    override suspend fun remove(id: String) {
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.id == id })
        }
    }

    override suspend fun reorder(orderedIds: List<String>) {
        store.edit { prefs ->
            val byId = decode(prefs[key]).associateBy { it.id }
            val ordered = orderedIds.mapNotNull { byId[it] }               // known ids in requested order
            val omitted = byId.values.filter { it.id !in orderedIds.toSet() }  // anything not listed, retained
            prefs[key] = json.encodeToString(ordered + omitted)
        }
    }

    private fun decode(raw: String?): List<ShortcutDto> =
        if (raw.isNullOrEmpty()) emptyList() else json.decodeFromString(raw)
}
