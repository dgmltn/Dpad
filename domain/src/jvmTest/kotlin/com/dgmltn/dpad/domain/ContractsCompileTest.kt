package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertNotNull

class ContractsCompileTest {
    @Test fun contractsHaveExpectedSurface() {
        // Anonymous no-op implementations must compile against the interfaces — this pins their shape.
        val discovery = object : DeviceDiscovery {
            override fun discovered(): Flow<List<DiscoveredDevice>> = kotlinx.coroutines.flow.flowOf(emptyList())
        }
        assertNotNull(discovery)
        val handle = ClientIdentityHandle(certificatePem = "c", privateKeyPem = "k")
        assertNotNull(handle.certificatePem)
    }
}
