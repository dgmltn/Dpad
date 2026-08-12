package com.dgmltn.dpad.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EntityModelsTest {
    @Test fun pairedDeviceDefaultsToSessionPort() {
        val d = PairedDevice(id = "a", name = "Living Room", host = "192.168.1.5", serviceName = "living._androidtvremote2._tcp")
        assertEquals(6466, d.port)
    }

    @Test fun discoveredDeviceCarriesHostAndPort() {
        val d = DiscoveredDevice(name = "Bedroom", host = "192.168.1.9", port = 6466)
        assertEquals("192.168.1.9", d.host)
    }

    @Test fun shortcutHoldsLabelAndUrl() {
        val s = Shortcut(id = "s1", label = "Netflix", appLinkUrl = "https://www.netflix.com/title")
        assertEquals("Netflix", s.label)
    }
}
