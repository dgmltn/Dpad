package com.dgmltn.dpad.data.store

import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.serialization.Serializable

@Serializable
data class PairedDeviceDto(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val serviceName: String,
)

@Serializable
data class ShortcutDto(
    val id: String,
    val label: String,
    val appLinkUrl: String,
)

fun PairedDeviceDto.toDomain(): PairedDevice = PairedDevice(
    id = id,
    name = name,
    host = host,
    port = port,
    serviceName = serviceName,
)

fun PairedDevice.toDto(): PairedDeviceDto = PairedDeviceDto(
    id = id,
    name = name,
    host = host,
    port = port,
    serviceName = serviceName,
)

fun ShortcutDto.toDomain(): Shortcut = Shortcut(
    id = id,
    label = label,
    appLinkUrl = appLinkUrl,
)

fun Shortcut.toDto(): ShortcutDto = ShortcutDto(
    id = id,
    label = label,
    appLinkUrl = appLinkUrl,
)
