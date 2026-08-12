package com.dgmltn.dpad.protocol.pairing

import com.dgmltn.dpad.protocol.crypto.sha256
import com.dgmltn.dpad.protocol.crypto.stripLeadingZeros
import java.security.MessageDigest
import kotlin.test.*

class PairingSecretTest {
    private val client = RsaPublicParams(
        modulus = byteArrayOf(0x00, 0x7F, 0x33, 0x21),  // note leading zero to strip
        exponent = byteArrayOf(0x01, 0x00, 0x01),
    )
    private val server = RsaPublicParams(
        modulus = byteArrayOf(0x55, 0x44, 0x33),
        exponent = byteArrayOf(0x01, 0x00, 0x01),
    )

    private fun oracle(nonce: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(
            byteArrayOf(0x7F, 0x33, 0x21) + byteArrayOf(0x01, 0x00, 0x01) +
            byteArrayOf(0x55, 0x44, 0x33) + byteArrayOf(0x01, 0x00, 0x01) +
            nonce
        )

    @Test fun stripsLeadingZeros() =
        assertContentEquals(byteArrayOf(0x7F, 0x33), byteArrayOf(0x00, 0x00, 0x7F, 0x33).stripLeadingZeros())

    @Test fun sha256MatchesJdk() =
        assertContentEquals(
            MessageDigest.getInstance("SHA-256").digest("dpad".encodeToByteArray()),
            sha256("dpad".encodeToByteArray()),
        )

    @Test fun secretMatchesOracleWhenCheckByteCorrect() {
        val nonce = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val hash = oracle(nonce)
        val code = "%02x".format(hash[0]) + "abcd"
        assertContentEquals(hash, computePairingSecret(client, server, code))
    }

    @Test fun returnsNullOnWrongCheckByte() {
        val nonce = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val hash = oracle(nonce)
        val wrong = "%02x".format((hash[0] + 1).toByte()) + "abcd"
        assertNull(computePairingSecret(client, server, wrong))
    }
}
