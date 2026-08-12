package com.dgmltn.dpad.protocol.discovery

import kotlinx.coroutines.flow.Flow

data class DiscoveredTv(val name: String, val host: String, val port: Int)

/**
 * Platform mDNS browse+resolve for _androidtvremote2._tcp.
 * Flow emits the full current set on every change.
 *
 * Note: constructors are platform-specific due to Android Context requirement;
 * actual class MdnsBrowser(private val context: Context) on Android,
 * actual class MdnsBrowser() on JVM/iOS.
 * Koin wires the right constructor in Plan 2.
 */
expect class MdnsBrowser {
    fun discovered(): Flow<List<DiscoveredTv>>
}
