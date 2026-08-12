package com.dgmltn.dpad.protocol.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual class MdnsBrowser {
    actual fun discovered(): Flow<List<DiscoveredTv>> = flowOf(emptyList())
}
