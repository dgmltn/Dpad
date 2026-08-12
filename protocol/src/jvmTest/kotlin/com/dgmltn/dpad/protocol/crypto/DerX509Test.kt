package com.dgmltn.dpad.protocol.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.test.*

class DerX509Test {
    @Test fun builtCertParsesAndVerifies() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val now = System.currentTimeMillis() / 1000
        val tbs = DerX509.tbsCertificate(
            "dpad-ios", pub.modulus.toByteArray(), pub.publicExponent.toByteArray(),
            serial = 7, notBeforeEpochSec = now - 60, notAfterEpochSec = now + 3_650L * 86_400,
        )
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(kp.private); update(tbs) }.sign()
        val der = DerX509.assembleCertificate(tbs, sig)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
        cert.verify(kp.public)
        assertTrue(cert.subjectX500Principal.name.contains("dpad-ios"))
        assertEquals(BigInteger(1, (cert.publicKey as RSAPublicKey).modulus.toByteArray().let {
            if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }), BigInteger(1, pub.modulus.toByteArray().let {
            if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }))
    }

    @Test fun readCertificateRsaPublicKeyMatchesOriginalKey() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val now = System.currentTimeMillis() / 1000
        val tbs = DerX509.tbsCertificate(
            "dpad-ios", pub.modulus.toByteArray(), pub.publicExponent.toByteArray(),
            serial = 3, notBeforeEpochSec = now - 60, notAfterEpochSec = now + 3_650L * 86_400,
        )
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(kp.private); update(tbs) }.sign()
        val der = DerX509.assembleCertificate(tbs, sig)

        val params = DerX509.readCertificateRsaPublicKey(der)

        assertEquals(BigInteger(1, pub.modulus.toByteArray()), BigInteger(1, params.modulus))
        assertEquals(BigInteger(1, pub.publicExponent.toByteArray()), BigInteger(1, params.exponent))
    }

    // Independent of DerX509's own encoder: hand-rolls a minimal PKCS#1 RSAPublicKey
    // SEQUENCE{INTEGER modulus, INTEGER exponent} byte-by-byte (short-form lengths only, since these
    // test values are small) — the shape SecKeyCopyExternalRepresentation returns for an RSA public
    // SecKey — and confirms readRsaPkcs1 extracts the same modulus/exponent bytes back out.
    @Test fun readRsaPkcs1ExtractsModulusAndExponent() {
        fun derInt(vararg bytes: Int): ByteArray {
            val content = bytes.map { it.toByte() }.toByteArray()
            return byteArrayOf(0x02, content.size.toByte()) + content
        }
        val modulus = derInt(0x00, 0xC1, 0xA0, 0x7B) // leading 0x00 sign byte, like a real RSA modulus
        val exponent = derInt(0x01, 0x00, 0x01) // 65537
        val body = modulus + exponent
        val der = byteArrayOf(0x30, body.size.toByte()) + body

        val params = DerX509.readRsaPkcs1(der)

        assertContentEquals(byteArrayOf(0x00, 0xC1.toByte(), 0xA0.toByte(), 0x7B), params.modulus)
        assertContentEquals(byteArrayOf(0x01, 0x00, 0x01), params.exponent)
    }

    // Strong independent check: unwraps/rewraps a REAL java.security-generated PKCS#8 key (produced by
    // a completely different encoder than DerX509) and expects byte-for-byte identity, then confirms
    // the rewrapped bytes still parse into the same key via KeyFactory. This is exactly the round trip
    // ClientIdentity.ios.kt needs: SecKeyCopyExternalRepresentation gives PKCS#1, wrapPkcs8 makes it
    // persistable PEM, unwrapPkcs8 recovers PKCS#1 for SecKeyCreateWithData on fromPem.
    @Test fun wrapPkcs8RoundTripsRealJcePkcs8AndReparses() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val realPkcs8 = kp.private.encoded

        val pkcs1 = DerX509.unwrapPkcs8(realPkcs8)
        val rewrapped = DerX509.wrapPkcs8(pkcs1)

        assertContentEquals(realPkcs8, rewrapped)
        val restored = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(rewrapped)) as RSAPrivateKey
        assertEquals((kp.private as RSAPrivateKey).modulus, restored.modulus)
    }
}
