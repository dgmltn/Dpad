package com.dgmltn.dpad.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.domain.RemoteKey
import com.dgmltn.dpad.domain.ShortcutRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemoteViewModel(
    private val controller: RemoteController,
    private val deviceRepository: DeviceRepository,
    shortcutRepository: ShortcutRepository,
) : ViewModel() {

    val state: StateFlow<RemoteUiState> = combine(
        combine(controller.connection, controller.power, controller.volume, ::Triple),
        deviceRepository.devices,
        deviceRepository.lastUsedDeviceId,
        shortcutRepository.shortcuts,
    ) { (connection, power, volume), devices, lastUsedDeviceId, shortcuts ->
        RemoteUiState(
            deviceName = devices.firstOrNull { it.id == lastUsedDeviceId }?.name,
            connection = connection,
            power = power,
            volume = volume,
            shortcuts = shortcuts.toImmutableList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteUiState())

    fun onKey(key: RemoteKey) {
        controller.press(key)
    }

    fun onLaunch(appLinkUrl: String) {
        controller.launchApp(appLinkUrl)
    }

    fun onText(text: String) {
        controller.sendText(text)
    }

    /**
     * Called by [RemoteScreen] on every ON_START. Safe to repeat: [RemoteController.connect] is a
     * no-op for the device it's already targeting.
     */
    fun onConnectLastUsed() {
        viewModelScope.launch {
            val id = deviceRepository.lastUsedDeviceId.first() ?: return@launch
            val device = deviceRepository.get(id) ?: return@launch
            controller.connect(device)
        }
    }
}
