package com.dgmltn.dpad.protocol.crypto

import com.dgmltn.dpad.protocol.pairing.RsaPublicParams

/** One self-signed RSA identity; the client cert is the pairing credential. PEMs are what Plan 2 persists. */
data class ClientIdentity(
    val certificatePem: String,     // -----BEGIN CERTIFICATE-----
    val privateKeyPem: String,      // -----BEGIN PRIVATE KEY----- (PKCS#8)
    val publicParams: RsaPublicParams,
)

expect object ClientIdentityGenerator {
    /** 2048-bit RSA, SHA256withRSA self-signed, CN=[commonName], 10-year validity. */
    fun generate(commonName: String): ClientIdentity
    /** Rebuild from persisted PEMs (validates they parse and match). */
    fun fromPem(certificatePem: String, privateKeyPem: String): ClientIdentity
}
