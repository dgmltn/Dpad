package com.dgmltn.dpad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dgmltn.dpad.data.store.PairedDeviceDto
import com.dgmltn.dpad.data.store.toDomain
import com.dgmltn.dpad.data.store.toDto
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.PairedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class DeviceRepositoryImpl(private val store: DataStore<Preferences>) : DeviceRepository {
    private val devicesKey = stringPreferencesKey("paired_devices")
    private val lastUsedKey = stringPreferencesKey("last_used_device_id")
    private val json = Json

    override val devices: Flow<List<PairedDevice>> =
        store.data.map { prefs -> decode(prefs[devicesKey]).map { it.toDomain() } }

    override val lastUsedDeviceId: Flow<String?> =
        store.data.map { it[lastUsedKey] }

    override suspend fun upsert(device: PairedDevice) {
        store.edit { prefs ->
            val current = decode(prefs[devicesKey]).associateBy { it.id }.toMutableMap()
            current[device.id] = device.toDto()
            prefs[devicesKey] = json.encodeToString(current.values.toList())
        }
    }

    override suspend fun remove(id: String) {
        store.edit { prefs ->
            prefs[devicesKey] = json.encodeToString(decode(prefs[devicesKey]).filterNot { it.id == id })
            if (prefs[lastUsedKey] == id) prefs.remove(lastUsedKey)
        }
    }

    override suspend fun setLastUsed(id: String) {
        store.edit { it[lastUsedKey] = id }
    }

    override suspend fun get(id: String): PairedDevice? =
        devices.first().firstOrNull { it.id == id }

    private fun decode(raw: String?): List<PairedDeviceDto> =
        if (raw.isNullOrEmpty()) emptyList() else json.decodeFromString(raw)
}
