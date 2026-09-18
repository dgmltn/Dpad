package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface RemoteController {
    val connection: StateFlow<ConnectionState>
    val volume: StateFlow<Volume?>
    /** TV power as last reported by the TV; `null` when unknown or not connected. */
    val power: StateFlow<TvPower?>
    /** Emits once per user command ([press], [launchApp], [sendText]); drives idle auto-disconnect. */
    val interactions: SharedFlow<Unit>
    /**
     * Connect to [device] and keep reconnecting. A no-op while already targeting [device] (connecting
     * or connected, until [disconnect] or a re-pair is required); switches target if [device] differs.
     */
    fun connect(device: PairedDevice)
    fun disconnect()
    fun press(key: RemoteKey)
    fun launchApp(appLinkUrl: String)
    fun sendText(text: String)          // per-character key events for search fields
}
