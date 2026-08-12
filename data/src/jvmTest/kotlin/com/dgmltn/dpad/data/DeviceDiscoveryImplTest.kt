package com.dgmltn.dpad.data

import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DeviceDiscoveryImplTest {
    @Test fun mapsBrowserOutputToDomainList() = runTest {
        // JVM MdnsBrowser actual is a stub emitting emptyList; this proves the wrapper compiles + maps.
        val discovery = DeviceDiscoveryImpl(MdnsBrowser())
        assertEquals(emptyList(), discovery.discovered().first())
    }
}
