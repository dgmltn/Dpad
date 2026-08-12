package com.dgmltn.dpad.domain

sealed interface PairingProgress {
    data object Connecting : PairingProgress
    data object AwaitingCode : PairingProgress      // TV is showing the on-screen code; prompt the user
    data object Paired : PairingProgress
    data class Failed(val reason: PairingFailureReason) : PairingProgress
}

enum class PairingFailureReason { WRONG_CODE, REJECTED, CONNECTION_LOST, TIMEOUT }
