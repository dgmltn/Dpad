package com.dgmltn.dpad.data.store

import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DtosTest {
    private val json = Json

    @Test fun pairedDeviceRoundTripsThroughJsonAndDomain() {
        val device = PairedDevice(id = "1", name = "Den", host = "10.0.0.4", port = 6466, serviceName = "den._androidtvremote2._tcp")
        val encoded = json.encodeToString(PairedDeviceDto.serializer(), device.toDto())
        val decoded = json.decodeFromString(PairedDeviceDto.serializer(), encoded).toDomain()
        assertEquals(device, decoded)
    }

    @Test fun shortcutRoundTrips() {
        val s = Shortcut(id = "s", label = "Plex", appLinkUrl = "https://app.plex.tv")
        assertEquals(s, json.decodeFromString(ShortcutDto.serializer(),
            json.encodeToString(ShortcutDto.serializer(), s.toDto())).toDomain())
    }
}
