package com.dgmltn.dpad.protocol.crypto

import com.dgmltn.dpad.protocol.pairing.RsaPublicParams

/**
 * Minimal, dependency-free DER/X.509 builder. iOS has no public certificate-authoring API — this lets
 * [com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator]'s iOS actual self-sign an RSA key entirely
 * by hand: build the unsigned [tbsCertificate], sign its bytes with `SecKeyCreateSignature`, then
 * [assembleCertificate]. [readRsaPkcs1]/[wrapPkcs8]/[unwrapPkcs8] handle the PKCS#1 <-> PEM plumbing
 * `SecKeyCopyExternalRepresentation` needs on both sides.
 *
 * Pure Kotlin (no platform APIs) so it's JVM-tested here — see DerX509Test — verifying the DER encoding
 * the iOS path depends on against `java.security` before it ever runs on a device.
 */
object DerX509 {
    private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val OID_RSA_ENCRYPTION = "1.2.840.113549.1.1.1"
    private const val OID_COMMON_NAME = "2.5.4.3"

    /** Unsigned TBSCertificate: `SEQUENCE { version[0], serial, sigAlg, issuer, validity, subject, spki }`. */
    fun tbsCertificate(
        commonName: String,
        modulus: ByteArray,
        exponent: ByteArray,
        serial: Long,
        notBeforeEpochSec: Long,
        notAfterEpochSec: Long,
    ): ByteArray {
        val name = derName(commonName)
        return derSequence(
            derExplicit(0, derInteger(byteArrayOf(2))), // version: v3
            derInteger(longToMinimalBytes(serial)),
            signatureAlgorithm(),
            name, // issuer
            derSequence(derUtcTime(notBeforeEpochSec), derUtcTime(notAfterEpochSec)),
            name, // subject == issuer (self-signed)
            subjectPublicKeyInfo(modulus, exponent),
        )
    }

    /** Final certificate: `SEQUENCE { tbs, sigAlg, signature BIT STRING }`. */
    fun assembleCertificate(tbs: ByteArray, signature: ByteArray): ByteArray =
        derSequence(tbs, signatureAlgorithm(), derBitString(signature))

    /**
     * Reads a PKCS#1 RSAPublicKey DER (`SEQUENCE { modulus INTEGER, publicExponent INTEGER }`) — the
     * shape `SecKeyCopyExternalRepresentation` returns for an RSA *public* SecKey.
     */
    fun readRsaPkcs1(der: ByteArray): RsaPublicParams {
        val (seqTag, seqContent) = DerReader(der).readTlv()
        require(seqTag == TAG_SEQUENCE) { "expected SEQUENCE, got tag $seqTag" }
        val body = DerReader(seqContent)
        val (modTag, modulus) = body.readTlv()
        require(modTag == TAG_INTEGER) { "expected INTEGER modulus, got tag $modTag" }
        val (expTag, exponent) = body.readTlv()
        require(expTag == TAG_INTEGER) { "expected INTEGER exponent, got tag $expTag" }
        return RsaPublicParams(modulus, exponent)
    }

    /** Wraps a PKCS#1 RSAPrivateKey DER in a PKCS#8 PrivateKeyInfo envelope (for PEM persistence). */
    fun wrapPkcs8(pkcs1: ByteArray): ByteArray =
        derSequence(derInteger(byteArrayOf(0)), rsaAlgorithmIdentifier(), derOctetString(pkcs1))

    /** Reverse of [wrapPkcs8]: extracts the PKCS#1 payload from a PKCS#8 PrivateKeyInfo DER. */
    fun unwrapPkcs8(pkcs8: ByteArray): ByteArray {
        val (seqTag, seqContent) = DerReader(pkcs8).readTlv()
        require(seqTag == TAG_SEQUENCE) { "expected SEQUENCE, got tag $seqTag" }
        val body = DerReader(seqContent)
        body.readTlv() // version
        body.readTlv() // AlgorithmIdentifier
        val (octetTag, octetContent) = body.readTlv()
        require(octetTag == TAG_OCTET_STRING) { "expected OCTET STRING, got tag $octetTag" }
        return octetContent
    }

    /**
     * Extracts SubjectPublicKeyInfo's RSA params directly from a full Certificate DER (as produced by
     * [assembleCertificate]) — pure Kotlin, so `ClientIdentityGenerator.fromPem`'s iOS actual can
     * cross-validate a persisted certificate against its reconstructed private key without any
     * platform certificate-parsing API (mirrors the JVM actual's `CertificateFactory`-based check).
     */
    fun readCertificateRsaPublicKey(certDer: ByteArray): RsaPublicParams {
        val (certTag, certContent) = DerReader(certDer).readTlv()
        require(certTag == TAG_SEQUENCE) { "expected Certificate SEQUENCE, got tag $certTag" }
        val (tbsTag, tbsContent) = DerReader(certContent).readTlv()
        require(tbsTag == TAG_SEQUENCE) { "expected TBSCertificate SEQUENCE, got tag $tbsTag" }

        val tbsReader = DerReader(tbsContent)
        var element = tbsReader.readTlv()
        if (element.first == 0xA0) element = tbsReader.readTlv() // optional [0] EXPLICIT version -> serialNumber
        // `element` now holds serialNumber; skip signature AlgorithmIdentifier, issuer, validity,
        // subject — subjectPublicKeyInfo is the next element after those four.
        repeat(4) { tbsReader.readTlv() }
        val (spkiTag, spkiContent) = tbsReader.readTlv()
        require(spkiTag == TAG_SEQUENCE) { "expected SubjectPublicKeyInfo SEQUENCE, got tag $spkiTag" }

        val spkiReader = DerReader(spkiContent)
        spkiReader.readTlv() // AlgorithmIdentifier
        val (bitStringTag, bitStringContent) = spkiReader.readTlv()
        require(bitStringTag == TAG_BIT_STRING) { "expected BIT STRING, got tag $bitStringTag" }
        return readRsaPkcs1(bitStringContent.copyOfRange(1, bitStringContent.size)) // drop unused-bits byte
    }

    private fun signatureAlgorithm() = derSequence(derOid(OID_SHA256_WITH_RSA), derNull())
    private fun rsaAlgorithmIdentifier() = derSequence(derOid(OID_RSA_ENCRYPTION), derNull())

    private fun derName(commonName: String) =
        derSequence(derSet(derSequence(derOid(OID_COMMON_NAME), derUtf8String(commonName))))

    private fun subjectPublicKeyInfo(modulus: ByteArray, exponent: ByteArray) = derSequence(
        rsaAlgorithmIdentifier(),
        derBitString(derSequence(derInteger(modulus), derInteger(exponent))),
    )

    // ---- DER primitives ----

    private const val TAG_INTEGER = 0x02
    private const val TAG_BIT_STRING = 0x03
    private const val TAG_OCTET_STRING = 0x04
    private const val TAG_NULL = 0x05
    private const val TAG_OID = 0x06
    private const val TAG_UTF8_STRING = 0x0C
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_SET = 0x31
    private const val TAG_UTC_TIME = 0x17

    private fun derLength(length: Int): ByteArray = if (length < 0x80) {
        byteArrayOf(length.toByte())
    } else {
        var n = length
        val bytes = ArrayList<Byte>()
        while (n > 0) {
            bytes.add(0, (n and 0xFF).toByte())
            n = n shr 8
        }
        byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun derTlv(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + derLength(content.size) + content

    private fun derSequence(vararg parts: ByteArray): ByteArray =
        derTlv(TAG_SEQUENCE, parts.fold(ByteArray(0)) { acc, p -> acc + p })

    private fun derSet(vararg parts: ByteArray): ByteArray =
        derTlv(TAG_SET, parts.fold(ByteArray(0)) { acc, p -> acc + p })

    /** Minimal, sign-correct DER INTEGER: strips redundant leading zero bytes, re-adds exactly one if
     *  the high bit would otherwise read as negative. Callers only ever pass non-negative values
     *  (RSA modulus/exponent, serial numbers, version), so no two's-complement negative support. */
    private fun derInteger(bytesIn: ByteArray): ByteArray {
        var start = 0
        while (start < bytesIn.size - 1 && bytesIn[start] == 0.toByte()) start++
        var b = bytesIn.copyOfRange(start, bytesIn.size)
        if (b.isEmpty()) b = byteArrayOf(0)
        if ((b[0].toInt() and 0x80) != 0) b = byteArrayOf(0) + b
        return derTlv(TAG_INTEGER, b)
    }

    private fun derBitString(content: ByteArray): ByteArray = derTlv(TAG_BIT_STRING, byteArrayOf(0) + content)
    private fun derOctetString(content: ByteArray): ByteArray = derTlv(TAG_OCTET_STRING, content)
    private fun derNull(): ByteArray = byteArrayOf(TAG_NULL.toByte(), 0)
    private fun derUtf8String(s: String): ByteArray = derTlv(TAG_UTF8_STRING, s.encodeToByteArray())

    private fun derOid(dotted: String): ByteArray {
        val parts = dotted.split(".").map { it.toInt() }
        val out = ArrayList<Byte>()
        out.add((parts[0] * 40 + parts[1]).toByte())
        for (p in parts.drop(2)) {
            var v = p
            val chunk = ArrayList<Byte>()
            chunk.add((v and 0x7F).toByte())
            v = v shr 7
            while (v > 0) {
                chunk.add(0, ((v and 0x7F) or 0x80).toByte())
                v = v shr 7
            }
            out.addAll(chunk)
        }
        return derTlv(TAG_OID, out.toByteArray())
    }

    private fun Int.pad2(): String = if (this < 10) "0$this" else this.toString()

    /** RFC 5280 UTCTime (`YYMMDDHHMMSSZ`) — valid through 2049, comfortably covering this app's 10-year certs. */
    private fun derUtcTime(epochSec: Long): ByteArray {
        val dt = epochSecToUtc(epochSec)
        val str = "${(dt.year % 100).pad2()}${dt.month.pad2()}${dt.day.pad2()}" +
            "${dt.hour.pad2()}${dt.minute.pad2()}${dt.second.pad2()}Z"
        return derTlv(TAG_UTC_TIME, str.encodeToByteArray())
    }

    private fun derExplicit(tag: Int, content: ByteArray): ByteArray = derTlv(0xA0 or tag, content)

    private fun longToMinimalBytes(value: Long): ByteArray {
        require(value >= 0) { "serial must be non-negative" }
        var v = value
        val bytes = ArrayList<Byte>()
        do {
            bytes.add(0, (v and 0xFF).toByte())
            v = v shr 8
        } while (v > 0)
        return bytes.toByteArray()
    }

    /** Minimal cursor-based DER TLV reader — enough for the SEQUENCE/INTEGER/OCTET STRING shapes above. */
    private class DerReader(private val data: ByteArray) {
        private var pos = 0
        fun readTlv(): Pair<Int, ByteArray> {
            val tag = data[pos++].toInt() and 0xFF
            var len = data[pos++].toInt() and 0xFF
            if (len and 0x80 != 0) {
                val numBytes = len and 0x7F
                len = 0
                repeat(numBytes) { len = (len shl 8) or (data[pos++].toInt() and 0xFF) }
            }
            val content = data.copyOfRange(pos, pos + len)
            pos += len
            return tag to content
        }
    }

    private data class UtcDateTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int)

    /** Civil-from-days (Howard Hinnant's algorithm), proleptic Gregorian — pure-Kotlin so this stays
     *  usable from commonMain with no platform calendar dependency. */
    private fun epochSecToUtc(epochSec: Long): UtcDateTime {
        val secsPerDay = 86_400L
        val days = epochSec.floorDiv(secsPerDay)
        val secOfDay = epochSec.mod(secsPerDay)

        val z = days + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L // [0, 146096]
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365 // [0, 399]
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
        val mp = (5 * doy + 2) / 153 // [0, 11]
        val d = doy - (153 * mp + 2) / 5 + 1 // [1, 31]
        val m = mp + (if (mp < 10) 3 else -9) // [1, 12]
        val year = if (m <= 2) y + 1 else y

        val hour = (secOfDay / 3600).toInt()
        val minute = ((secOfDay % 3600) / 60).toInt()
        val second = (secOfDay % 60).toInt()
        return UtcDateTime(year.toInt(), m.toInt(), d.toInt(), hour, minute, second)
    }
}
