package com.dgmltn.dpad.domain

/** A TV seen on the network via mDNS but not necessarily paired. */
data class DiscoveredDevice(val name: String, val host: String, val port: Int)
