package com.dgmltn.dpad.data.mapping

import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.discovery.DiscoveredTv
import com.dgmltn.dpad.protocol.pairing.PairingEvent
import com.dgmltn.dpad.protocol.pairing.PairingFailure
import com.dgmltn.dpad.protocol.session.SessionState
import com.dgmltn.dpad.protocol.session.VolumeState
import remote.RemoteKeyCode
import kotlin.test.*

class ProtocolMappersTest {
    @Test fun everyRemoteKeyMapsToADistinctKeyCode() {
        val codes = RemoteKey.entries.map { it.toKeyCode() }
        assertEquals(RemoteKey.entries.size, codes.toSet().size, "RemoteKey→RemoteKeyCode must be injective")
        // spot-check a few load-bearing ones
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_UP, RemoteKey.DPAD_UP.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_BACK, RemoteKey.BACK.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_VOLUME_MUTE, RemoteKey.MUTE.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE, RemoteKey.MEDIA_PLAY_PAUSE.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_POWER, RemoteKey.POWER.toKeyCode())
    }

    @Test fun sessionStateMapsToConnectionState() {
        assertEquals(ConnectionState.Disconnected, SessionState.Disconnected.toDomain())
        assertEquals(ConnectionState.Connecting, SessionState.Connecting.toDomain())
        assertEquals(ConnectionState.Connected, SessionState.Connected.toDomain())
        assertEquals(ConnectionState.PairingRequired, SessionState.PairingRequired.toDomain())
    }

    @Test fun volumeStateMaps() {
        assertEquals(Volume(7, 100, false), VolumeState(7, 100, false).toDomain())
    }

    @Test fun pairingEventsMapToProgress() {
        assertEquals(PairingProgress.AwaitingCode, PairingEvent.WaitingForCode.toProgress())
        assertEquals(PairingProgress.Paired, PairingEvent.Paired.toProgress())
        assertEquals(PairingProgress.Failed(PairingFailureReason.WRONG_CODE),
            PairingEvent.Failed(PairingFailure.WRONG_CODE).toProgress())
    }

    @Test fun discoveredTvMaps() {
        assertEquals(DiscoveredDevice("Den", "10.0.0.4", 6466), DiscoveredTv("Den", "10.0.0.4", 6466).toDomain())
    }
}
