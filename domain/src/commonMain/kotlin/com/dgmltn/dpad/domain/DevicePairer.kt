package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.Flow

interface DevicePairer {
    val progress: Flow<PairingProgress>
    /** Begin pairing with [device]; drives [progress] to AwaitingCode. */
    suspend fun start(device: DiscoveredDevice)
    /** Submit the on-screen code; drives [progress] to Paired or Failed. On Paired, persists a PairedDevice. */
    suspend fun submitCode(code: String)
    fun cancel()
}
