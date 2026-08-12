package com.dgmltn.dpad.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import pairing.PairingMessage
import pairing.PairingRequest
import remote.RemoteKeyCode
import remote.RemoteKeyInject
import remote.RemoteDirection
import remote.RemoteMessage

class ProtoRoundTripTest {
    @Test fun pairingRequestRoundTrips() {
        val msg = PairingMessage(
            protocol_version = 2,
            status = PairingMessage.Status.STATUS_OK,
            pairing_request = PairingRequest(client_name = "Dpad", service_name = "com.dgmltn.dpad"),
        )
        val decoded = PairingMessage.ADAPTER.decode(msg.encode())
        assertEquals(msg, decoded)
    }

    @Test fun keyInjectRoundTrips() {
        val msg = RemoteMessage(
            remote_key_inject = RemoteKeyInject(
                key_code = RemoteKeyCode.KEYCODE_DPAD_UP,
                direction = RemoteDirection.SHORT,
            ),
        )
        assertEquals(msg, RemoteMessage.ADAPTER.decode(msg.encode()))
    }
}
