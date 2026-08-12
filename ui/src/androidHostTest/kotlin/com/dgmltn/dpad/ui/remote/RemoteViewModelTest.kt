package com.dgmltn.dpad.ui.remote

import app.cash.turbine.test
import com.dgmltn.dpad.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class RemoteViewModelTest {
    // Hand-written fakes — the contracts are small interfaces.
    private class FakeController : RemoteController {
        val _conn = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val _vol = MutableStateFlow<Volume?>(null)
        override val connection = _conn.asStateFlow()
        override val volume = _vol.asStateFlow()
        val pressed = mutableListOf<RemoteKey>()
        var connectedTo: PairedDevice? = null
        override fun connect(device: PairedDevice) { connectedTo = device; _conn.value = ConnectionState.Connecting }
        override fun disconnect() { _conn.value = ConnectionState.Disconnected }
        override fun press(key: RemoteKey) { pressed += key }
        override fun launchApp(appLinkUrl: String) {}
        override fun sendText(text: String) {}
    }
    private class FakeDeviceRepo(devices: List<PairedDevice>, last: String?) : DeviceRepository {
        override val devices = MutableStateFlow(devices)
        override val lastUsedDeviceId = MutableStateFlow(last)
        override suspend fun upsert(device: PairedDevice) {}
        override suspend fun remove(id: String) {}
        override suspend fun setLastUsed(id: String) {}
        override suspend fun get(id: String) = devices.value.firstOrNull { it.id == id }
    }
    private class FakeShortcutRepo(shortcuts: List<Shortcut>) : ShortcutRepository {
        override val shortcuts = MutableStateFlow(shortcuts)
        override suspend fun add(shortcut: Shortcut) {}
        override suspend fun remove(id: String) {}
        override suspend fun reorder(orderedIds: List<String>) {}
    }

    @Test fun stateReflectsConnectionDeviceNameAndShortcuts() = runTest {
        val device = PairedDevice(id="1", name="Living Room", host="10.0.0.5", serviceName="lr")
        val controller = FakeController()
        val vm = RemoteViewModel(
            controller,
            FakeDeviceRepo(listOf(device), last="1"),
            FakeShortcutRepo(listOf(Shortcut("s","Netflix","https://n"))),
        )
        vm.state.test {
            awaitItem() // initial
            val s = awaitItemUntil { it.deviceName == "Living Room" && it.shortcuts.isNotEmpty() }
            assertEquals("Living Room", s.deviceName)
            assertEquals("Netflix", s.shortcuts.single().label)
        }
    }

    @Test fun onKeyForwardsToController() = runTest {
        val controller = FakeController()
        val vm = RemoteViewModel(controller, FakeDeviceRepo(emptyList(), null), FakeShortcutRepo(emptyList()))
        vm.onKey(RemoteKey.DPAD_UP)
        assertEquals(listOf(RemoteKey.DPAD_UP), controller.pressed)
    }

    @Test fun onConnectLastUsedConnectsToStoredDevice() = runTest {
        val device = PairedDevice(id="1", name="Den", host="10.0.0.4", serviceName="den")
        val controller = FakeController()
        val vm = RemoteViewModel(controller, FakeDeviceRepo(listOf(device), last="1"), FakeShortcutRepo(emptyList()))
        vm.onConnectLastUsed()
        awaitCondition { controller.connectedTo?.id == "1" }
    }

    @Test fun constructionAloneTriggersConnectToLastUsedDevice() = runTest {
        val device = PairedDevice(id="1", name="Den", host="10.0.0.4", serviceName="den")
        val controller = FakeController()
        // Deliberately NOT calling onConnectLastUsed() — construction (init{}) must trigger it.
        RemoteViewModel(controller, FakeDeviceRepo(listOf(device), last="1"), FakeShortcutRepo(emptyList()))
        awaitCondition { controller.connectedTo?.id == "1" }
    }
}

// Test helpers — await a StateFlow/Turbine condition without advanceUntilIdle (Room/real-thread safe).
suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemUntil(p: (T) -> Boolean): T {
    while (true) { val i = awaitItem(); if (p(i)) return i }
}
suspend fun awaitCondition(p: () -> Boolean) {
    repeat(1000) {
        if (p()) return
        kotlinx.coroutines.yield()
    }
}
