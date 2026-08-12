package com.dgmltn.dpad.protocol.crypto

expect fun sha256(data: ByteArray): ByteArray

fun ByteArray.stripLeadingZeros(): ByteArray {
    val first = indexOfFirst { it != 0.toByte() }
    return if (first <= 0) (if (first == 0) this else byteArrayOf()) else copyOfRange(first, size)
}
