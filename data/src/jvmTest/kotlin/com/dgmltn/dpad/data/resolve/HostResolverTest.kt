package com.dgmltn.dpad.data.resolve

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.session.HostAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HostResolverTest {
    private val device = PairedDevice(id = "1", name = "Den", host = "10.0.0.4", port = 6466,
        serviceName = "den._androidtvremote2._tcp")

    @Test fun prefersFreshMdnsHostMatchingServiceName() {
        val discovered = listOf(DiscoveredDevice(name = "den._androidtvremote2._tcp", host = "10.0.0.99", port = 6466))
        assertEquals(HostAddress("10.0.0.99", 6466), HostResolver.resolve(device, discovered))
    }

    @Test fun fallsBackToStoredHostWhenNotDiscovered() {
        assertEquals(HostAddress("10.0.0.4", 6466), HostResolver.resolve(device, emptyList()))
    }

    @Test fun ignoresDiscoveredDevicesWithADifferentServiceName() {
        val discovered = listOf(DiscoveredDevice(name = "bedroom._androidtvremote2._tcp", host = "10.0.0.5", port = 6466))
        assertEquals(HostAddress("10.0.0.4", 6466), HostResolver.resolve(device, discovered))
    }

    // --- resolveHostForAttempt: stored-IP-first, mDNS only on retry (fixes slow connect) ---

    @Test fun firstAttemptUsesStoredHostWithoutConsultingMdns() = runTest {
        // The whole point of the fix: attempt 0 must NOT block on a cold mDNS browse — it uses the
        // stored IP immediately, so a normal open/resume against a TV that kept its address is instant.
        var mdnsConsulted = false
        val result = resolveHostForAttempt(
            attempt = 0,
            device = device,
            discoveredSnapshot = { mdnsConsulted = true; emptyList() },
        )
        assertFalse(mdnsConsulted, "attempt 0 must not consult mDNS")
        assertEquals(HostAddress("10.0.0.4", 6466), result)
    }

    @Test fun retryUsesFreshMdnsHostWhenAvailable() = runTest {
        // On a retry (stored IP didn't work — TV moved/got a new lease), mDNS is consulted and its
        // fresh host wins.
        val result = resolveHostForAttempt(
            attempt = 1,
            device = device,
            discoveredSnapshot = { listOf(DiscoveredDevice(device.serviceName, "10.0.0.99", 6466)) },
        )
        assertEquals(HostAddress("10.0.0.99", 6466), result)
    }

    @Test fun retryFallsBackToStoredHostWhenMdnsTimesOut() = runTest {
        // mDNS is bounded by a timeout, not an open-ended first() — a slow/silent mDNS falls back to
        // the stored IP instead of hanging the connect.
        val result = resolveHostForAttempt(
            attempt = 1,
            device = device,
            discoveredSnapshot = { delay(10_000); emptyList() },
            timeoutMs = 50,
        )
        assertEquals(HostAddress("10.0.0.4", 6466), result)
    }
}
