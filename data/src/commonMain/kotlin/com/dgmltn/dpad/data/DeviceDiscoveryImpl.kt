package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.mapping.toDomain
import com.dgmltn.dpad.domain.DeviceDiscovery
import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceDiscoveryImpl(private val mdns: MdnsBrowser) : DeviceDiscovery {
    override fun discovered(): Flow<List<DiscoveredDevice>> =
        mdns.discovered().map { list -> list.map { it.toDomain() } }
}
