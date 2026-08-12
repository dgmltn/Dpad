package com.dgmltn.dpad.domain

/** A TV the user has paired with. [id] is app-generated and stable across host changes. */
data class PairedDevice(
    val id: String,
    val name: String,
    val host: String,          // last-known IP; re-resolved via mDNS before each connect
    val port: Int = 6466,
    val serviceName: String,   // mDNS instance name, used to re-resolve host
)
