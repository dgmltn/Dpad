package com.dgmltn.dpad.ui.devices

import app.cash.turbine.test
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.ui.remote.awaitCondition
import com.dgmltn.dpad.ui.remote.awaitItemUntil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DevicesViewModelTest {
    // Hand-written fakes — the contracts are small interfaces.
    private class FakeDeviceRepo(devices: List<PairedDevice>, last: String?) : DeviceRepository {
        override val devices = MutableStateFlow(devices)
        override val lastUsedDeviceId = MutableStateFlow(last)
        val setLastUsedCalls = mutableListOf<String>()
        override suspend fun upsert(device: PairedDevice) {}
        override suspend fun remove(id: String) {}
        override suspend fun setLastUsed(id: String) {
            setLastUsedCalls += id
            lastUsedDeviceId.value = id
        }
        override suspend fun get(id: String) = devices.value.firstOrNull { it.id == id }
    }
    private class FakeDiscovery : DeviceDiscovery {
        val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override fun discovered() = _discovered.asStateFlow()
    }
    private class FakePairer : DevicePairer {
        val _progress = MutableStateFlow<PairingProgress>(PairingProgress.Connecting)
        override val progress = _progress.asStateFlow()
        var started: DiscoveredDevice? = null
        val submittedCodes = mutableListOf<String>()
        var cancelled = false
        override suspend fun start(device: DiscoveredDevice) { started = device }
        override suspend fun submitCode(code: String) { submittedCodes += code }
        override fun cancel() { cancelled = true }
    }
    private class FakeController : RemoteController {
        val _conn = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val _vol = MutableStateFlow<Volume?>(null)
        override val connection = _conn.asStateFlow()
        override val volume = _vol.asStateFlow()
        var connectedTo: PairedDevice? = null
        override fun connect(device: PairedDevice) { connectedTo = device; _conn.value = ConnectionState.Connecting }
        override fun disconnect() { _conn.value = ConnectionState.Disconnected }
        override fun press(key: RemoteKey) {}
        override fun launchApp(appLinkUrl: String) {}
        override fun sendText(text: String) {}
    }

    @Test fun discoveredExcludesAlreadyPaired() = runTest {
        val paired = PairedDevice(id = "1", name = "Den", host = "10.0.0.1", serviceName = "den")
        val deviceRepo = FakeDeviceRepo(listOf(paired), last = "1")
        val discovery = FakeDiscovery()
        val vm = DevicesViewModel(deviceRepo, discovery, FakePairer(), FakeController())
        vm.state.test {
            awaitItem() // initial

            discovery._discovered.value = listOf(
                DiscoveredDevice(name = "den", host = "10.0.0.1", port = 6466),
                DiscoveredDevice(name = "bedroom", host = "10.0.0.2", port = 6466),
            )

            val s = awaitItemUntil { it.discovered.isNotEmpty() }
            assertEquals(listOf("bedroom"), s.discovered.map { it.name })
        }
    }

    @Test fun onSelectPersistsLastUsedAndConnects() = runTest {
        val device = PairedDevice(id = "1", name = "Den", host = "10.0.0.1", serviceName = "den")
        val deviceRepo = FakeDeviceRepo(listOf(device), last = null)
        val controller = FakeController()
        val vm = DevicesViewModel(deviceRepo, FakeDiscovery(), FakePairer(), controller)

        vm.onSelect(device)

        awaitCondition { deviceRepo.setLastUsedCalls == listOf("1") && controller.connectedTo?.id == "1" }
    }

    @Test fun onStartPairingSurfacesProgressIntoState() = runTest {
        val discovered = DiscoveredDevice(name = "den", host = "10.0.0.1", port = 6466)
        val pairer = FakePairer()
        val vm = DevicesViewModel(FakeDeviceRepo(emptyList(), null), FakeDiscovery(), pairer, FakeController())

        vm.state.test {
            awaitItem() // initial

            vm.onStartPairing(discovered)
            pairer._progress.value = PairingProgress.AwaitingCode

            val s = awaitItemUntil { it.pairing == PairingProgress.AwaitingCode }
            assertEquals(PairingProgress.AwaitingCode, s.pairing)
            assertEquals(discovered, pairer.started)
        }
    }
}
