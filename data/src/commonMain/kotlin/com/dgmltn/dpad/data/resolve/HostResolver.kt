package com.dgmltn.dpad.data.resolve

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.session.HostAddress
import kotlinx.coroutines.withTimeoutOrNull

/** How long a retry will wait on mDNS before falling back to the stored IP. Bounded, never open-ended. */
internal const val MDNS_RETRY_TIMEOUT_MS = 1_500L

object HostResolver {
    /** Prefer the freshest mDNS host matching this device's serviceName; fall back to the stored host. */
    fun resolve(device: PairedDevice, discovered: List<DiscoveredDevice>): HostAddress {
        val fresh = discovered.firstOrNull { it.name == device.serviceName }
        return HostAddress(host = fresh?.host ?: device.host, port = device.port)
    }
}

/**
 * The host to (re)connect to for [attempt] (0-based) of a connect loop.
 *
 * **Attempt 0 uses the device's stored IP immediately — no mDNS.** This is the fix for slow connects:
 * a normal open/resume against a TV that kept its address connects without waiting on a cold mDNS
 * browse (SRV/A resolution via NsdManager takes seconds, and emits nothing at all until the TV
 * re-announces — which a just-woken TV does only after a delay). Only on a *retry* — meaning the
 * stored IP didn't connect, so the TV likely moved or got a new DHCP lease — do we consult mDNS, and
 * even then only for [timeoutMs] before falling back to the stored IP, so a slow or silent mDNS can
 * never hang the connect the way an open-ended `discovered().first()` did.
 */
internal suspend fun resolveHostForAttempt(
    attempt: Int,
    device: PairedDevice,
    discoveredSnapshot: suspend () -> List<DiscoveredDevice>,
    timeoutMs: Long = MDNS_RETRY_TIMEOUT_MS,
): HostAddress {
    if (attempt == 0) return HostAddress(host = device.host, port = device.port)
    val discovered = withTimeoutOrNull(timeoutMs) { discoveredSnapshot() } ?: emptyList()
    return HostResolver.resolve(device, discovered)
}
