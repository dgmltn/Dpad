package com.dgmltn.dpad.data

import app.cash.turbine.test
import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.PairedDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DeviceRepositoryImplTest {
    private fun repo() = DeviceRepositoryImpl(tempDataStore())
    private fun device(id: String, name: String = "TV") =
        PairedDevice(id = id, name = name, host = "10.0.0.$id", serviceName = "$id._androidtvremote2._tcp")

    @Test fun startsEmpty() = runTest {
        assertEquals(emptyList(), repo().devices.first())
    }

    @Test fun upsertAddsThenUpdatesById() = runTest {
        val r = repo()
        r.upsert(device("1", "Den"))
        r.upsert(device("1", "Living Room"))   // same id → update, not duplicate
        val all = r.devices.first()
        assertEquals(1, all.size)
        assertEquals("Living Room", all.single().name)
    }

    @Test fun removeDeletesById() = runTest {
        val r = repo()
        r.upsert(device("1")); r.upsert(device("2"))
        r.remove("1")
        assertEquals(listOf("2"), r.devices.first().map { it.id })
    }

    @Test fun lastUsedTracksAndClearsOnRemove() = runTest {
        val r = repo()
        r.upsert(device("1")); r.setLastUsed("1")
        assertEquals("1", r.lastUsedDeviceId.first())
        r.remove("1")
        assertNull(r.lastUsedDeviceId.first())   // removing the last-used device clears the pointer
    }

    @Test fun getReturnsByIdOrNull() = runTest {
        val r = repo()
        r.upsert(device("1", "Den"))
        assertEquals("Den", r.get("1")?.name)
        assertNull(r.get("nope"))
    }

    @Test fun devicesFlowEmitsOnChange() = runTest {
        val r = repo()
        r.devices.test {
            assertEquals(emptyList(), awaitItem())
            r.upsert(device("1"))
            assertEquals(listOf("1"), awaitItem().map { it.id })
        }
    }
}
