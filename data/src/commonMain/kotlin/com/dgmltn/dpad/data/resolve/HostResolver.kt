package com.dgmltn.dpad.data.resolve

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.session.HostAddress

object HostResolver {
    /** Prefer the freshest mDNS host matching this device's serviceName; fall back to the stored host. */
    fun resolve(device: PairedDevice, discovered: List<DiscoveredDevice>): HostAddress {
        val fresh = discovered.firstOrNull { it.name == device.serviceName }
        return HostAddress(host = fresh?.host ?: device.host, port = device.port)
    }
}
