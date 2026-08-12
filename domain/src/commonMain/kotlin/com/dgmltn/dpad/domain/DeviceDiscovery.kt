package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.Flow

interface DeviceDiscovery {
    /** Live list of TVs seen on the LAN. */
    fun discovered(): Flow<List<DiscoveredDevice>>
}
