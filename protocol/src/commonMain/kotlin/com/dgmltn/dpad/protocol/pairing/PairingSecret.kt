package com.dgmltn.dpad.protocol.pairing

import com.dgmltn.dpad.protocol.crypto.sha256
import com.dgmltn.dpad.protocol.crypto.stripLeadingZeros

data class RsaPublicParams(val modulus: ByteArray, val exponent: ByteArray)

fun computePairingSecret(client: RsaPublicParams, server: RsaPublicParams, code: String): ByteArray? {
    if (code.length != 6) return null
    val bytes = code.chunked(2).map { it.toIntOrNull(16) ?: return null }.map { it.toByte() }
    val checkByte = bytes[0]
    val nonce = byteArrayOf(bytes[1], bytes[2])
    val hash = sha256(
        client.modulus.stripLeadingZeros() + client.exponent.stripLeadingZeros() +
        server.modulus.stripLeadingZeros() + server.exponent.stripLeadingZeros() + nonce
    )
    return if (hash[0] == checkByte) hash else null
}
