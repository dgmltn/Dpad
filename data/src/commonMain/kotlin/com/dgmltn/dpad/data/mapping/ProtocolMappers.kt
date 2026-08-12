package com.dgmltn.dpad.data.mapping

import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.discovery.DiscoveredTv
import com.dgmltn.dpad.protocol.pairing.PairingEvent
import com.dgmltn.dpad.protocol.pairing.PairingFailure
import com.dgmltn.dpad.protocol.session.SessionState
import com.dgmltn.dpad.protocol.session.VolumeState
import remote.RemoteKeyCode

fun RemoteKey.toKeyCode(): RemoteKeyCode = when (this) {
    RemoteKey.DPAD_UP -> RemoteKeyCode.KEYCODE_DPAD_UP
    RemoteKey.DPAD_DOWN -> RemoteKeyCode.KEYCODE_DPAD_DOWN
    RemoteKey.DPAD_LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
    RemoteKey.DPAD_RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
    RemoteKey.DPAD_CENTER -> RemoteKeyCode.KEYCODE_DPAD_CENTER
    RemoteKey.BACK -> RemoteKeyCode.KEYCODE_BACK
    RemoteKey.HOME -> RemoteKeyCode.KEYCODE_HOME
    RemoteKey.VOLUME_UP -> RemoteKeyCode.KEYCODE_VOLUME_UP
    RemoteKey.VOLUME_DOWN -> RemoteKeyCode.KEYCODE_VOLUME_DOWN
    RemoteKey.MUTE -> RemoteKeyCode.KEYCODE_VOLUME_MUTE
    RemoteKey.MEDIA_PLAY_PAUSE -> RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE
    RemoteKey.MEDIA_REWIND -> RemoteKeyCode.KEYCODE_MEDIA_REWIND
    RemoteKey.MEDIA_FAST_FORWARD -> RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD
    RemoteKey.POWER -> RemoteKeyCode.KEYCODE_POWER
}

fun SessionState.toDomain(): ConnectionState = when (this) {
    SessionState.Disconnected -> ConnectionState.Disconnected
    SessionState.Connecting -> ConnectionState.Connecting
    SessionState.Connected -> ConnectionState.Connected
    SessionState.PairingRequired -> ConnectionState.PairingRequired
}

fun VolumeState.toDomain(): Volume = Volume(level = level, max = max, muted = muted)

fun PairingFailure.toReason(): PairingFailureReason = when (this) {
    PairingFailure.WRONG_CODE -> PairingFailureReason.WRONG_CODE
    PairingFailure.REJECTED -> PairingFailureReason.REJECTED
    PairingFailure.CONNECTION_LOST -> PairingFailureReason.CONNECTION_LOST
    PairingFailure.TIMEOUT -> PairingFailureReason.TIMEOUT
}

fun PairingEvent.toProgress(): PairingProgress = when (this) {
    PairingEvent.WaitingForCode -> PairingProgress.AwaitingCode
    PairingEvent.Paired -> PairingProgress.Paired
    is PairingEvent.Failed -> PairingProgress.Failed(reason.toReason())
}

fun DiscoveredTv.toDomain(): DiscoveredDevice = DiscoveredDevice(name = name, host = host, port = port)
