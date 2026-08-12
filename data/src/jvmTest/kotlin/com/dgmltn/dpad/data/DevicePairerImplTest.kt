package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.ClientIdentityHandle
import com.dgmltn.dpad.domain.ClientIdentityStore
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.PairingProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Lightweight compile-level assurance only — PairingClient is a final :protocol class that can't
 * be faked, so start() itself (which constructs a real PairingClient) is compile-verified in
 * :data and device-verified in Plan 3, not unit tested here. This just confirms cancel() is safe
 * to call with no prior start() — i.e. the eventsJob teardown fix doesn't NPE on the null
 * initial state.
 */
class DevicePairerImplTest {
    private val identityStore = object : ClientIdentityStore {
        override suspend fun getOrCreate(): ClientIdentityHandle = error("not used by this test")
    }
    private val deviceRepository = object : DeviceRepository {
        override val devices: Flow<List<PairedDevice>> = flowOf(emptyList())
        override val lastUsedDeviceId: Flow<String?> = flowOf(null)
        override suspend fun upsert(device: PairedDevice) {}
        override suspend fun remove(id: String) {}
        override suspend fun setLastUsed(id: String) {}
        override suspend fun get(id: String): PairedDevice? = null
    }

    @Test fun cancelWithoutPriorStartDoesNotThrow() = runTest {
        val pairer = DevicePairerImpl(identityStore, deviceRepository, this)
        pairer.cancel()
    }

    /**
     * Regression test for the Critical whole-branch finding: start()'s pre-assignment suspend
     * work (`identityStore.protocolIdentity()`) — now run inside a tracked `startJob` — happens
     * BEFORE `client`/`eventsJob` are assigned. Without tracking+cancelling that job, an
     * overlapping start()/cancel() couldn't interrupt an in-flight start() parked at that suspend
     * point — it would resume later and clobber client/eventsJob out from under the caller,
     * leaking a live PairingClient connection and an orphaned collector still writing into
     * _progress.
     *
     * Uses [UnconfinedTestDispatcher] so each launched coroutine runs eagerly up to its first
     * genuine suspension. `identityStore.protocolIdentity()` — backed here by a REAL
     * [ClientIdentityStoreImpl] over a real temp-file DataStore — genuinely suspends (DataStore
     * does its own I/O off this dispatcher's thread), so by the time the next start()/cancel()
     * call runs, the prior start() is verifiably still parked BEFORE assigning `client` —
     * exactly the race window the finding describes. start() is itself `suspend`, so each
     * overlapping call is driven from its own `launch {}` here (mirroring how two concurrent
     * callers — e.g. rapid device-switching in the UI — would race in practice); PairingClient
     * (the un-fakeable :protocol final) is never reached, because a correctly-fixed start()
     * cancels the prior attempt before it gets far enough to construct one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun overlappingStartsLeaveNoLeakedClientAfterCancel() = runTest(UnconfinedTestDispatcher()) {
        val realIdentityStore = ClientIdentityStoreImpl(tempDataStore())
        val pairer = DevicePairerImpl(realIdentityStore, deviceRepository, this)
        val deviceA = DiscoveredDevice(name = "A", host = "127.0.0.1", port = 1)
        val deviceB = DiscoveredDevice(name = "B", host = "127.0.0.1", port = 1)

        val jobA = launch { pairer.start(deviceA) }  // runs eagerly to its real suspend point
                                                       // (protocolIdentity()'s DataStore read) and
                                                       // parks there — never reaches `client = c`.
        val jobB = launch { pairer.start(deviceB) }   // start()'s own startJob?.cancel() cancels
                                                       // A's in-flight startJob HERE, at that exact
                                                       // suspend point, then launches B's.
        pairer.cancel()                                // cancels B's startJob the same way.

        jobA.join(); jobB.join()
        advanceUntilIdle()

        // Neither overlapping start() ever got far enough to construct a PairingClient/emit an
        // event, so progress never left its initial Connecting value — no leaked collector fired.
        assertEquals(PairingProgress.Connecting, pairer.progress.first())

        // cancel() remains safe to call again after the race unwinds.
        pairer.cancel()
    }
}
