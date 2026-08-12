package com.dgmltn.dpad.protocol.crypto

import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFNumberType
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256

/**
 * iOS has no public X.509-authoring API, so identity generation is: ask the Security framework for a
 * raw RSA key pair (`SecKeyCreateRandomKey`), self-sign a certificate we build by hand with [DerX509]
 * (`SecKeyCreateSignature` over the TBS bytes), and PEM-encode both — mirroring the JVM actual's shape
 * (BouncyCastle cert builder + PEM) with Apple's Security framework standing in for BouncyCastle.
 *
 * Talks to Security in raw CoreFoundation (CFDictionary/CFData) rather than NSDictionary/NSData: the
 * two are toll-free bridged, but staying in CF avoids ambiguity about how Kotlin/Native's ObjC-interop
 * bridges Security's plain-C CFTypeRef constants into an NSDictionary's `Any` value slots.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
actual object ClientIdentityGenerator {
    private const val TEN_YEARS_SEC = 10L * 365 * 86_400

    actual fun generate(commonName: String): ClientIdentity = memScoped {
        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        val keySize = cfNumber(2048)
        CFDictionarySetValue(attributes, kSecAttrKeySizeInBits, keySize)

        val errorPtr = alloc<CFErrorRefVar>()
        val privateKey = SecKeyCreateRandomKey(attributes, errorPtr.ptr)
            ?: throw IllegalStateException("SecKeyCreateRandomKey failed: ${errorPtr.value}")
        // SecKeyRef is plain CoreFoundation, NOT toll-free bridged to any Foundation class — unlike
        // CFData/CFDictionary/CFNumber (auto-managed via NSObject bridging), it needs an explicit
        // CFRelease to balance this +1 from SecKeyCreateRandomKey, or it leaks on every call. Held
        // for the whole function (readPublicParams, sign, and copyExternalRepresentation all need it),
        // so release in a `finally` around the rest of the body rather than at a single call site.
        try {
            val params = derivePublicParams(privateKey)

            val now = nowEpochSec()
            val tbs = DerX509.tbsCertificate(
                commonName, params.modulus, params.exponent,
                serial = 1L, notBeforeEpochSec = now - 86_400, notAfterEpochSec = now + TEN_YEARS_SEC,
            )
            val signature = sign(privateKey, tbs)
            val certDer = DerX509.assembleCertificate(tbs, signature)

            val pkcs1PrivateDer = copyExternalRepresentation(privateKey)
            val pkcs8Der = DerX509.wrapPkcs8(pkcs1PrivateDer)

            ClientIdentity(
                certificatePem = pem("CERTIFICATE", certDer),
                privateKeyPem = pem("PRIVATE KEY", pkcs8Der),
                publicParams = params,
            )
        } finally {
            CFRelease(privateKey)
        }
    }

    actual fun fromPem(certificatePem: String, privateKeyPem: String): ClientIdentity = memScoped {
        val pkcs1Der = DerX509.unwrapPkcs8(derOf(privateKeyPem))

        val keyAttributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(keyAttributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionarySetValue(keyAttributes, kSecAttrKeyClass, kSecAttrKeyClassPrivate)

        val errorPtr = alloc<CFErrorRefVar>()
        val privateKey = SecKeyCreateWithData(byteArrayToCFData(pkcs1Der), keyAttributes, errorPtr.ptr)
            ?: throw IllegalStateException("SecKeyCreateWithData failed: ${errorPtr.value}")
        // See the matching comment in generate(): SecKeyRef needs an explicit CFRelease.
        try {
            val params = derivePublicParams(privateKey)

            // Cross-validate cert/key match, same as the JVM actual (see ClientIdentity.jvmShared.kt):
            // parse the certificate's own SubjectPublicKeyInfo out of its DER (pure Kotlin — no
            // SecCertificate needed) and confirm its modulus matches the reconstructed key's.
            val certParams = DerX509.readCertificateRsaPublicKey(derOf(certificatePem))
            require(certParams.modulus.contentEquals(params.modulus)) { "certificate and private key do not match" }

            ClientIdentity(certificatePem, privateKeyPem, params)
        } finally {
            CFRelease(privateKey)
        }
    }

    /** Derives an RSA public key from [privateKey] via `SecKeyCopyPublicKey` (itself a "Copy" — owned,
     *  released here once its params are extracted) and reads its params. */
    private fun derivePublicParams(privateKey: SecKeyRef): RsaPublicParams {
        val publicKey = SecKeyCopyPublicKey(privateKey)
            ?: throw IllegalStateException("SecKeyCopyPublicKey returned null")
        return try {
            DerX509.readRsaPkcs1(copyExternalRepresentation(publicKey))
        } finally {
            CFRelease(publicKey)
        }
    }

    private fun sign(privateKey: SecKeyRef, data: ByteArray): ByteArray = memScoped {
        val errorPtr = alloc<CFErrorRefVar>()
        val signatureData = SecKeyCreateSignature(
            privateKey,
            kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
            byteArrayToCFData(data),
            errorPtr.ptr,
        ) ?: throw IllegalStateException("SecKeyCreateSignature failed: ${errorPtr.value}")
        cfDataToByteArray(signatureData)
    }

    private fun copyExternalRepresentation(key: SecKeyRef): ByteArray = memScoped {
        val errorPtr = alloc<CFErrorRefVar>()
        val data = SecKeyCopyExternalRepresentation(key, errorPtr.ptr)
            ?: throw IllegalStateException("SecKeyCopyExternalRepresentation failed: ${errorPtr.value}")
        cfDataToByteArray(data)
    }

    private fun cfNumber(value: Int): CFNumberRef? = memScoped {
        val v = alloc<kotlinx.cinterop.IntVar>()
        v.value = value
        CFNumberCreate(kCFAllocatorDefault, platform.CoreFoundation.kCFNumberIntType, v.ptr)
    }

    private fun byteArrayToCFData(bytes: ByteArray): CFDataRef? = bytes.usePinned { pinned ->
        CFDataCreate(
            kCFAllocatorDefault,
            if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret<UByteVar>(),
            bytes.size.toLong(),
        )
    }

    private fun cfDataToByteArray(data: CFDataRef): ByteArray {
        val length = CFDataGetLength(data).toInt()
        if (length == 0) return ByteArray(0)
        val ptr = CFDataGetBytePtr(data) ?: return ByteArray(0)
        return ByteArray(length) { i -> ptr[i.toLong()].toByte() }
    }

    private fun nowEpochSec(): Long = platform.posix.time(null)

    private fun pem(label: String, der: ByteArray): String {
        val body = Base64.Default.encode(der).chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }

    internal fun derOf(pem: String): ByteArray {
        val base64 = pem.lines().filterNot { it.startsWith("-----") }.joinToString("")
        return Base64.Default.decode(base64)
    }
}
