package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.StateFlow

interface RemoteController {
    val connection: StateFlow<ConnectionState>
    val volume: StateFlow<Volume?>
    /** Connect to [device] (idempotent; auto-reconnects). Switches target if already connected elsewhere. */
    fun connect(device: PairedDevice)
    fun disconnect()
    fun press(key: RemoteKey)
    fun launchApp(appLinkUrl: String)
    fun sendText(text: String)          // per-character key events for search fields
}
