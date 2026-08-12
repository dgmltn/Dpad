package com.dgmltn.dpad.protocol.transport

import co.touchlab.kermit.Logger
import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.crypto.DerX509
import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_t
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_error_t
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_parameters_t
import platform.Network.nw_protocol_options_t
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Security.SecCertificateCopyKey
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecAttrLabel
import platform.Security.kSecClass
import platform.Security.kSecClassCertificate
import platform.Security.kSecClassIdentity
import platform.Security.kSecClassKey
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnRef
import platform.Security.kSecValueData
import platform.Security.kSecValueRef
import platform.Security.sec_identity_create
import platform.Security.sec_protocol_options_set_local_identity
import platform.Security.sec_protocol_options_set_verify_block
import platform.Security.sec_trust_copy_ref
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create

private const val TAG = "Tls"

/** Fixed keychain tag for the imported client identity — see [buildSecIdentity]. */
private const val KEYCHAIN_TAG = "com.dgmltn.dpad.tls.client-identity"

/**
 * `NWConnection` + `NWProtocolTLS` mirror of the JVM/Android actual (`SSLSocket`, trust-all +
 * capture-peer-cert, client-cert keystore): the same shape, built on Apple's Network framework
 * instead of JSSE. Kept thin per Task 10's brief — this is the riskiest untested surface in the
 * project and is compile-verified only here (no simulator/device runtime in this task; see Plan 3).
 */
@OptIn(ExperimentalForeignApi::class)
actual class TlsSocketFactory actual constructor(private val identity: ClientIdentity) {
    actual suspend fun connect(host: String, port: Int): TlsConnection {
        val secIdentity = buildSecIdentity(identity)
        val queue = dispatch_queue_create("com.dgmltn.dpad.tls", null)

        // Captured off the server's cert inside the verify block below (see the class doc on the
        // JVM actual's JvmTlsConnection) — trust-all, but record who we're actually talking to.
        var peerParams: RsaPublicParams? = null

        val parameters: nw_parameters_t = nw_parameters_create_secure_tcp(
            { protoOptions: nw_protocol_options_t ->
                val secOptions = nw_tls_copy_sec_protocol_options(protoOptions)
                sec_protocol_options_set_local_identity(secOptions, secIdentity)
                sec_protocol_options_set_verify_block(
                    secOptions,
                    { _, trustRef, completeBlock ->
                        // Runs on EVERY handshake — sec_trust_copy_ref ("copy") and
                        // SecCertificateCopyKey ("Copy") each hand back a +1-owned CoreFoundation
                        // reference that isn't toll-free bridged (unlike CFData/CFDictionary), so
                        // each needs an explicit CFRelease or this leaks on every (re)connect.
                        // SecTrustGetCertificateAtIndex is a "Get" — not owned, must NOT be released.
                        var trust: platform.Security.SecTrustRef? = null
                        var key: SecKeyRef? = null
                        try {
                            trust = sec_trust_copy_ref(trustRef)
                            val cert = SecTrustGetCertificateAtIndex(trust, 0)
                            key = cert?.let { SecCertificateCopyKey(it) }
                            if (key != null) {
                                peerParams = DerX509.readRsaPkcs1(copyExternalRepresentation(key))
                            }
                        } catch (t: Throwable) {
                            Logger.w(tag = TAG) { "failed to capture peer cert: $t" }
                        } finally {
                            key?.let { CFRelease(it) }
                            trust?.let { CFRelease(it) }
                        }
                        // Trust-all (pairing itself is the trust decision, same as the JVM actual's
                        // X509TrustManager) — the client only cares WHICH key it's talking to.
                        completeBlock?.invoke(true)
                    },
                    queue,
                )
            },
            { _: nw_protocol_options_t -> }, // default TCP options
        )

        val endpoint = nw_endpoint_create_host(host, port.toString())
        val connection = nw_connection_create(endpoint, parameters)
            ?: throw TlsHandshakeRejectedException(IllegalStateException("nw_connection_create failed for $host:$port"))
        nw_connection_set_queue(connection, queue)

        try {
            awaitReady(connection)
        } catch (e: Exception) {
            nw_connection_cancel(connection)
            throw TlsHandshakeRejectedException(e)
        }

        val params = peerParams
            ?: run { nw_connection_cancel(connection); throw TlsHandshakeRejectedException(IllegalStateException("no peer certificate captured")) }
        return NwTlsConnection(connection, params)
    }

    private suspend fun awaitReady(connection: nw_connection_t) = suspendCancellableCoroutine<Unit> { cont ->
        nw_connection_set_state_changed_handler(connection) { state, error ->
            when (state) {
                nw_connection_state_ready -> if (cont.isActive) cont.resume(Unit)
                nw_connection_state_failed -> if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("nw_connection failed: ${error?.let { nwErrorDescription(it) }}"))
                }
                nw_connection_state_cancelled -> if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("nw_connection cancelled before becoming ready"))
                }
                else -> Unit // waiting/preparing — keep waiting
            }
        }
        cont.invokeOnCancellation { nw_connection_cancel(connection) }
        nw_connection_start(connection)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class NwTlsConnection(
    private val connection: nw_connection_t,
    override val serverPublicParams: RsaPublicParams,
) : TlsConnection {
    // suspendCancellableCoroutine + invokeOnCancellation{ nw_connection_cancel } gives the same
    // cancellability property the JVM actual gets from polling with SOCKET_READ_POLL_MILLIS + an
    // ensureActive() check: a `withTimeout` (or RemoteSession's generation-based teardown) around a
    // suspended read actually unblocks it, instead of leaving it parked forever. Unlike the JVM
    // poll, this is immediate rather than bounded by a poll interval — and it's safe here because
    // every coroutine cancellation that can reach a suspended read in this codebase (RemoteSession's
    // disconnect()/reconnect generation bump) already tears the whole connection down via close()
    // right after anyway (see RemoteSession.runLoop's finally block).
    override suspend fun read(max: Int): ByteArray = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { nw_connection_cancel(connection) }
        // nw_connection_receive hands back its content as `dispatch_data_t` — see nsDataToByteArray's
        // doc for why that's bridged into a ByteArray via interpretObjCPointer rather than a cast.
        nw_connection_receive(connection, 1u, max.toUInt()) { data, _, isComplete, error ->
            when {
                error != null -> if (cont.isActive) cont.resumeWithException(EofException("nw_connection_receive error: ${nwErrorDescription(error)}"))
                data != null -> if (cont.isActive) cont.resume(nsDataToByteArray(data))
                isComplete -> if (cont.isActive) cont.resumeWithException(EofException())
                else -> if (cont.isActive) cont.resume(ByteArray(0))
            }
        }
    }

    override suspend fun write(bytes: ByteArray): Unit = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { nw_connection_cancel(connection) }
        val data = byteArrayToNSData(bytes)
        nw_connection_send(connection, data, null, true) { error ->
            if (error != null) {
                if (cont.isActive) cont.resumeWithException(EofException("nw_connection_send error: ${nwErrorDescription(error)}"))
            } else {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    override fun close() {
        nw_connection_cancel(connection)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun nwErrorDescription(error: nw_error_t): String = error.toString()

/**
 * Imports [identity]'s cert+key into the keychain under a fixed tag (deleting any stale item under
 * that tag first, so re-generating/re-persisting the identity across app runs stays idempotent) so a
 * `SecIdentityRef` can be formed from them — iOS has no way to build one purely in memory; a
 * cert+key pair only becomes a queryable `kSecClassIdentity` item once both are keychain-resident.
 */
@OptIn(ExperimentalForeignApi::class)
private fun buildSecIdentity(identity: ClientIdentity): platform.Security.sec_identity_t {
    val certDer = ClientIdentityGenerator.derOf(identity.certificatePem)
    val pkcs1Der = DerX509.unwrapPkcs8(ClientIdentityGenerator.derOf(identity.privateKeyPem))
    val tag = byteArrayToCFData(KEYCHAIN_TAG.encodeToByteArray())

    val deleteKeyQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionarySetValue(deleteKeyQuery, kSecClass, kSecClassKey)
    CFDictionarySetValue(deleteKeyQuery, kSecAttrApplicationTag, tag)
    SecItemDelete(deleteKeyQuery)

    val deleteCertQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionarySetValue(deleteCertQuery, kSecClass, kSecClassCertificate)
    CFDictionarySetValue(deleteCertQuery, kSecAttrLabel, tag)
    SecItemDelete(deleteCertQuery)

    // SecCertificateRef/SecKeyRef/the CFTypeRef from SecItemCopyMatching are plain CoreFoundation —
    // NOT toll-free bridged (unlike the CFDictionary/CFData used above, which auto-manage via
    // NSObject bridging) — so each +1 from a "Create"/"Copy" call below is released explicitly once
    // we're done needing OUR reference. Note that's independent of the CFDictionaries also holding a
    // retain on cert/privateKey via kCFTypeDictionaryValueCallBacks: each owner balances its own
    // retain, so releasing ours here doesn't affect the dictionary's.
    val cert: SecCertificateRef = SecCertificateCreateWithData(kCFAllocatorDefault, byteArrayToCFData(certDer))
        ?: throw IllegalStateException("SecCertificateCreateWithData failed")
    try {
        val keyAttributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(keyAttributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionarySetValue(keyAttributes, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        val privateKey: SecKeyRef = memScoped {
            val errorPtr = alloc<CFErrorRefVar>()
            SecKeyCreateWithData(byteArrayToCFData(pkcs1Der), keyAttributes, errorPtr.ptr)
                ?: throw IllegalStateException("SecKeyCreateWithData failed: ${errorPtr.value}")
        }
        try {
            val addKeyQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionarySetValue(addKeyQuery, kSecClass, kSecClassKey)
            CFDictionarySetValue(addKeyQuery, kSecAttrKeyType, kSecAttrKeyTypeRSA)
            CFDictionarySetValue(addKeyQuery, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
            CFDictionarySetValue(addKeyQuery, kSecAttrApplicationTag, tag)
            CFDictionarySetValue(addKeyQuery, kSecValueRef, privateKey)
            checkOsStatus("SecItemAdd(key)", SecItemAdd(addKeyQuery, null))
        } finally {
            // Not needed past this point — the keychain now holds its own persistent copy.
            CFRelease(privateKey)
        }

        val addCertQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(addCertQuery, kSecClass, kSecClassCertificate)
        CFDictionarySetValue(addCertQuery, kSecAttrLabel, tag)
        CFDictionarySetValue(addCertQuery, kSecValueRef, cert)
        checkOsStatus("SecItemAdd(cert)", SecItemAdd(addCertQuery, null))
    } finally {
        // Same reasoning: the keychain holds its own copy after SecItemAdd succeeds.
        CFRelease(cert)
    }

    val findIdentityQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionarySetValue(findIdentityQuery, kSecClass, kSecClassIdentity)
    CFDictionarySetValue(findIdentityQuery, kSecMatchLimit, kSecMatchLimitOne)
    CFDictionarySetValue(findIdentityQuery, kSecReturnRef, kCFBooleanTrue)
    val identityRef = memScoped {
        val resultPtr = alloc<platform.CoreFoundation.CFTypeRefVar>()
        checkOsStatus("SecItemCopyMatching(identity)", SecItemCopyMatching(findIdentityQuery, resultPtr.ptr))
        resultPtr.value
    } ?: throw IllegalStateException("SecItemCopyMatching found no matching identity after import")
    // "SecItemCopyMatching" — "Copy" — is another +1 owned CFTypeRef. sec_identity_create takes its
    // SecIdentityRef input at +0 (it retains internally, matching Network.framework's usual pattern
    // of retaining what you hand it rather than consuming your reference — see also
    // sec_protocol_options_set_local_identity below, which similarly retains the sec_identity_t it's
    // given), so our own reference is released right after, not held for the connection's lifetime.
    try {
        @Suppress("UNCHECKED_CAST")
        return sec_identity_create(identityRef as platform.Security.SecIdentityRef)
            ?: throw IllegalStateException("sec_identity_create failed for imported identity")
    } finally {
        CFRelease(identityRef)
    }
}

private fun checkOsStatus(what: String, status: Int) {
    check(status == platform.Security.errSecSuccess) { "$what failed: OSStatus $status" }
}

@OptIn(ExperimentalForeignApi::class)
private fun copyExternalRepresentation(key: SecKeyRef): ByteArray = memScoped {
    val errorPtr = alloc<CFErrorRefVar>()
    val data = SecKeyCopyExternalRepresentation(key, errorPtr.ptr)
        ?: throw IllegalStateException("SecKeyCopyExternalRepresentation failed: ${errorPtr.value}")
    cfDataToByteArray(data)
}

@OptIn(ExperimentalForeignApi::class)
private fun byteArrayToCFData(bytes: ByteArray): CFDataRef? = bytes.usePinned { pinned ->
    CFDataCreate(
        kCFAllocatorDefault,
        if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret<UByteVar>(),
        bytes.size.toLong(),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun cfDataToByteArray(data: CFDataRef): ByteArray {
    val length = CFDataGetLength(data).toInt()
    if (length == 0) return ByteArray(0)
    val ptr = CFDataGetBytePtr(data) ?: return ByteArray(0)
    return ByteArray(length) { i -> ptr[i.toLong()].toByte() }
}

/**
 * `nw_connection_send`/`nw_connection_receive`'s content is `dispatch_data_t`, which this SDK's
 * Kotlin binding exposes only as a generic `NSObject`/`NSData` — with no `NSData`-from-bytes
 * factory/constructor actually surfaced (`dataWithBytes:length:` isn't imported). CFData IS fully
 * usable here ([byteArrayToCFData]/[cfDataToByteArray], proven against the Security-framework calls
 * above) and is toll-free bridged with NSData at the Objective-C runtime level — but that bridging
 * isn't visible to Kotlin's *static* type checker via a plain `as` cast between the two (CFDataRef
 * is a distinct `CPointer` typealias, not an NSObject subtype). [kotlinx.cinterop.interpretObjCPointer]
 * bridges the two explicitly instead: it reinterprets a raw pointer as a Kotlin ObjC wrapper of the
 * requested class, which is exactly what "the same object, viewed as the bridged NSData" means.
 */
@OptIn(ExperimentalForeignApi::class)
private fun byteArrayToNSData(bytes: ByteArray): NSData? {
    val cfData = byteArrayToCFData(bytes) ?: return null
    return kotlinx.cinterop.interpretObjCPointer(cfData.rawValue)
}

@OptIn(ExperimentalForeignApi::class)
private fun nsDataToByteArray(data: NSObject): ByteArray =
    cfDataToByteArray(kotlinx.cinterop.interpretCPointer(data.objcPtr())!!)
