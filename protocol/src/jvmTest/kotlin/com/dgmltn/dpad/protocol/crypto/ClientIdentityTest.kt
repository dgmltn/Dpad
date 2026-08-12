package com.dgmltn.dpad.protocol.crypto

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.test.*

class ClientIdentityTest {
    private fun parse(pem: String): X509Certificate {
        val der = Base64.getMimeDecoder().decode(
            pem.lines().filterNot { it.startsWith("-----") }.joinToString("")
        )
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    @Test fun generatesValidSelfSignedCert() {
        val id = ClientIdentityGenerator.generate("dpad-test")
        val cert = parse(id.certificatePem)
        cert.verify(cert.publicKey)  // throws if not properly self-signed
        assertTrue(cert.subjectX500Principal.name.contains("dpad-test"))
    }

    @Test fun publicParamsMatchCertificate() {
        val id = ClientIdentityGenerator.generate("dpad-test")
        val cert = parse(id.certificatePem)
        val rsa = cert.publicKey as java.security.interfaces.RSAPublicKey
        assertEquals(rsa.modulus, BigInteger(1, id.publicParams.modulus))
        assertEquals(rsa.publicExponent, BigInteger(1, id.publicParams.exponent))
    }

    @Test fun roundTripsThroughPem() {
        val id = ClientIdentityGenerator.generate("dpad-test")
        val restored = ClientIdentityGenerator.fromPem(id.certificatePem, id.privateKeyPem)
        assertEquals(id.publicParams.modulus.toList(), restored.publicParams.modulus.toList())
    }

    @Test fun fromPemRejectsMismatchedCertAndKey() {
        val a = ClientIdentityGenerator.generate("dpad-a")
        val b = ClientIdentityGenerator.generate("dpad-b")
        assertFailsWith<IllegalArgumentException> {
            ClientIdentityGenerator.fromPem(a.certificatePem, b.privateKeyPem)
        }
    }
}
