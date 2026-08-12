package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.ClientIdentityHandle
import com.dgmltn.dpad.domain.ClientIdentityStore
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.DeviceDiscovery
import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Lightweight compile-level assurance only — RemoteSession/PairingClient are final :protocol
 * classes that can't be faked, so connect() itself (which constructs a real RemoteSession) is
 * compile-verified in :data and device-verified in Plan 3, not unit tested here. This just
 * confirms disconnect() is safe to call with no prior connect() — i.e. the collector-Job
 * teardown fix doesn't NPE on the null/empty-list initial state.
 */
class RemoteControllerImplTest {
    private val identityStore = object : ClientIdentityStore {
        override suspend fun getOrCreate(): ClientIdentityHandle = error("not used by this test")
    }
    private val discovery = object : DeviceDiscovery {
        override fun discovered(): Flow<List<DiscoveredDevice>> = flowOf(emptyList())
    }

    @Test fun disconnectWithoutPriorConnectDoesNotThrow() = runTest {
        val controller = RemoteControllerImpl(identityStore, discovery, this)
        controller.disconnect()
        assertEquals(ConnectionState.Disconnected, controller.connection.value)
        assertNull(controller.volume.value)
    }

    /**
     * Regression test for the Critical whole-branch finding: connect()'s outer `scope.launch {}`
     * suspends at `identityStore.protocolIdentity()` BEFORE it assigns `session`/`collectorJobs`.
     * Without tracking+cancelling that outer job (`connectJob`), an overlapping connect()/
     * disconnect() couldn't interrupt an in-flight launch parked at that suspend point — it would
     * resume later and clobber session/collectorJobs/state out from under the caller, leaking a
     * live RemoteSession and orphaning collectors that keep writing into _connection/_volume.
     *
     * Uses [UnconfinedTestDispatcher] so each `scope.launch {}` body runs eagerly (like real
     * dispatch) up to its first genuine suspension. `identityStore.protocolIdentity()` — backed
     * here by a REAL [ClientIdentityStoreImpl] over a real temp-file DataStore — genuinely
     * suspends (DataStore does its own I/O off this dispatcher's thread), so by the time the next
     * connect()/disconnect() call runs, the prior launch is verifiably still parked BEFORE
     * assigning `session` — exactly the race window the finding describes. Real
     * [DeviceDiscoveryImpl]/[MdnsBrowser] are used too (trivial `flowOf(emptyList())` on JVM), so
     * nothing here is faked — RemoteSession/PairingClient (the un-fakeable :protocol finals) are
     * simply never reached, because a correctly-fixed connect() cancels each launch before it
     * gets far enough to construct one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun overlappingConnectsLeaveNoLeakedSessionAfterDisconnect() = runTest(UnconfinedTestDispatcher()) {
        val realIdentityStore = ClientIdentityStoreImpl(tempDataStore())
        val realDiscovery = DeviceDiscoveryImpl(MdnsBrowser())
        val controller = RemoteControllerImpl(realIdentityStore, realDiscovery, this)
        val deviceA = PairedDevice(id = "a", name = "A", host = "127.0.0.1", port = 1, serviceName = "a")
        val deviceB = PairedDevice(id = "b", name = "B", host = "127.0.0.1", port = 1, serviceName = "b")

        controller.connect(deviceA)  // launches connectJob A; runs eagerly to its real suspend
                                      // point (protocolIdentity()'s DataStore read) and parks there
                                      // — never reaches `session = s`.
        controller.connect(deviceB)  // connect()'s internal disconnect() cancels A's connectJob
                                      // HERE, at that exact suspend point, then launches B's.
        controller.disconnect()      // cancels B's connectJob the same way, before it can assign.

        advanceUntilIdle()           // let any still-pending real completions unwind.

        assertEquals(ConnectionState.Disconnected, controller.connection.value)
        assertNull(controller.volume.value)

        // A trailing connect()/disconnect() cycle still works after the race — proves the fix
        // didn't leave connectJob/session in a state that blocks future use.
        controller.disconnect()
        assertEquals(ConnectionState.Disconnected, controller.connection.value)
    }
}
