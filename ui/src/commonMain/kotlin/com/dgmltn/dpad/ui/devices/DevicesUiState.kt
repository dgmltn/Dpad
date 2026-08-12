package com.dgmltn.dpad.ui.devices

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.PairingProgress
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class DevicesUiState(
    val paired: ImmutableList<PairedDevice> = persistentListOf(),
    val discovered: ImmutableList<DiscoveredDevice> = persistentListOf(),
    val lastUsedId: String? = null,
    val pairing: PairingProgress? = null,
)
