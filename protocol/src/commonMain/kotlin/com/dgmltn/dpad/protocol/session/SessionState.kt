package com.dgmltn.dpad.protocol.session

/** Lifecycle of a [RemoteSession]'s connection to an Android TV. */
sealed interface SessionState {
    data object Disconnected : SessionState
    data object Connecting : SessionState
    data object Connected : SessionState

    /** TLS rejected our client cert repeatedly — see [RemoteSession]'s handshake-rejection threshold. */
    data object PairingRequired : SessionState
}
