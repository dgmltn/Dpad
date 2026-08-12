package com.dgmltn.dpad.data.resolve

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.session.HostAddress
import kotlin.test.*

class HostResolverTest {
    private val device = PairedDevice(id = "1", name = "Den", host = "10.0.0.4", port = 6466,
        serviceName = "den._androidtvremote2._tcp")

    @Test fun prefersFreshMdnsHostMatchingServiceName() {
        val discovered = listOf(DiscoveredDevice(name = "den._androidtvremote2._tcp", host = "10.0.0.99", port = 6466))
        assertEquals(HostAddress("10.0.0.99", 6466), HostResolver.resolve(device, discovered))
    }

    @Test fun fallsBackToStoredHostWhenNotDiscovered() {
        assertEquals(HostAddress("10.0.0.4", 6466), HostResolver.resolve(device, emptyList()))
    }

    @Test fun ignoresDiscoveredDevicesWithADifferentServiceName() {
        val discovered = listOf(DiscoveredDevice(name = "bedroom._androidtvremote2._tcp", host = "10.0.0.5", port = 6466))
        assertEquals(HostAddress("10.0.0.4", 6466), HostResolver.resolve(device, discovered))
    }
}
