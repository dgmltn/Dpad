package com.dgmltn.dpad.protocol.crypto

import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

actual object ClientIdentityGenerator {
    actual fun generate(commonName: String): ClientIdentity {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Principal("CN=$commonName")
        val now = System.currentTimeMillis()
        val cert = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000),
                Date(now + 10L * 365 * 86_400_000), name, keyPair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        )
        return ClientIdentity(
            certificatePem = pem("CERTIFICATE", cert.encoded),
            privateKeyPem = pem("PRIVATE KEY", keyPair.private.encoded),
            publicParams = (keyPair.public as RSAPublicKey).toParams(),
        )
    }

    actual fun fromPem(certificatePem: String, privateKeyPem: String): ClientIdentity {
        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(derOf(certificatePem).inputStream()) as java.security.cert.X509Certificate
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(derOf(privateKeyPem))) as RSAPrivateKey
        val publicKey = cert.publicKey as RSAPublicKey
        require(privateKey.modulus == publicKey.modulus) { "certificate and private key do not match" }
        return ClientIdentity(certificatePem, privateKeyPem, publicKey.toParams())
    }

    private fun RSAPublicKey.toParams() = RsaPublicParams(
        modulus = modulus.toByteArray(), exponent = publicExponent.toByteArray(),
    )
    private fun pem(label: String, der: ByteArray) =
        "-----BEGIN $label-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)}\n-----END $label-----\n"
    internal fun derOf(pem: String): ByteArray =
        Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
}
