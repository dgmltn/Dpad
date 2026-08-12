package com.dgmltn.dpad.ui.remote

import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.Shortcut
import com.dgmltn.dpad.domain.Volume
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class RemoteUiState(
    val deviceName: String? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val volume: Volume? = null,
    val shortcuts: ImmutableList<Shortcut> = persistentListOf(),
)
