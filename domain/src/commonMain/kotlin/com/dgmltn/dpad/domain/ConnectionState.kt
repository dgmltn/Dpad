package com.dgmltn.dpad.domain

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object PairingRequired : ConnectionState   // stored client cert no longer trusted; re-pair
}

data class Volume(val level: Int, val max: Int, val muted: Boolean)
