package com.dgmltn.dpad.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgmltn.dpad.domain.DeviceDiscovery
import com.dgmltn.dpad.domain.DevicePairer
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.PairingProgress
import com.dgmltn.dpad.domain.RemoteController
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val deviceRepository: DeviceRepository,
    discovery: DeviceDiscovery,
    private val pairer: DevicePairer,
    private val controller: RemoteController,
) : ViewModel() {

    private val pairingProgress = MutableStateFlow<PairingProgress?>(null)
    private var pairingJob: Job? = null

    val state: StateFlow<DevicesUiState> = combine(
        deviceRepository.devices,
        deviceRepository.lastUsedDeviceId,
        discovery.discovered(),
        pairingProgress,
    ) { paired, lastUsedId, discovered, pairing ->
        val pairedServiceNames = paired.mapTo(HashSet()) { it.serviceName }
        DevicesUiState(
            paired = paired.toImmutableList(),
            discovered = discovered.filterNot { it.name in pairedServiceNames }.toImmutableList(),
            lastUsedId = lastUsedId,
            pairing = pairing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicesUiState())

    fun onSelect(device: PairedDevice) {
        viewModelScope.launch {
            deviceRepository.setLastUsed(device.id)
        }
        controller.connect(device)
    }

    fun onUnpair(id: String) {
        viewModelScope.launch {
            deviceRepository.remove(id)
        }
    }

    fun onStartPairing(device: DiscoveredDevice) {
        pairingProgress.value = PairingProgress.Connecting
        pairingJob = viewModelScope.launch {
            pairer.progress.collect { pairingProgress.value = it }
        }
        viewModelScope.launch {
            pairer.start(device)
        }
    }

    fun onSubmitCode(code: String) {
        viewModelScope.launch {
            pairer.submitCode(code)
        }
    }

    fun onCancelPairing() {
        pairer.cancel()
        pairingJob?.cancel()
        pairingJob = null
        pairingProgress.value = null
    }
}
