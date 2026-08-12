package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val devices: Flow<List<PairedDevice>>
    val lastUsedDeviceId: Flow<String?>
    suspend fun upsert(device: PairedDevice)      // add or update by id
    suspend fun remove(id: String)
    suspend fun setLastUsed(id: String)
    suspend fun get(id: String): PairedDevice?
}
