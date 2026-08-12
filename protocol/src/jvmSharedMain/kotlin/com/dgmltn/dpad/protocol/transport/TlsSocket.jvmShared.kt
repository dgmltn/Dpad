package com.dgmltn.dpad.protocol.transport

import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Pinned on both [TlsSocketFactory] (here) and `FakeTvServer` (jvmTest) so a rejected client cert
 * fails synchronously, inside `startHandshake()`, instead of asynchronously on a later read.
 *
 * Under TLS 1.3 the server sends its own Finished flight *before* it has verified the client's
 * certificate (RFC 8446 ordering), so a client-cert rejection only surfaces to the client on its
 * *next* I/O rather than from the handshake call itself. TLS 1.2's ordering verifies the client
 * cert as part of the same flight the client is still blocked on, making rejection observable
 * exactly where callers expect it (see `TlsHandshakeRejectedException`).
 *
 * TODO(Plan 3): re-verify this against real Android TV hardware — production TVs may negotiate
 * TLS 1.3 today, in which case rejection detection needs to move to first-read instead.
 */
internal const val TV_TLS_PROTOCOL = "TLSv1.2"

/**
 * A plain blocking `InputStream.read()` on a JVM socket does not observe coroutine cancellation —
 * a `withTimeout` around it can mark the coroutine cancelled, but the underlying OS thread stays
 * blocked in the native call regardless, so the timeout never actually unblocks the caller. Both
 * [JvmTlsConnection.read] and the fake server's read loop (jvmTest) poll with this SO_TIMEOUT
 * instead of a single indefinite read, checking cancellation between attempts, so an outer
 * `withTimeout` (or a test parking the peer) can actually be enforced instead of hanging forever.
 */
internal const val SOCKET_READ_POLL_MILLIS = 50

actual class TlsSocketFactory actual constructor(private val identity: ClientIdentity) {
    actual suspend fun connect(host: String, port: Int): TlsConnection = withContext(Dispatchers.IO) {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ClientIdentityGenerator.derOf(identity.certificatePem).inputStream())
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(ClientIdentityGenerator.derOf(identity.privateKeyPem)))
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("dpad", key, CharArray(0), arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, CharArray(0)) }
        var peer: X509Certificate? = null
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                peer = chain[0]
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trustAll), null) }
        val socket = context.socketFactory.createSocket(host, port) as SSLSocket
        socket.enabledProtocols = arrayOf(TV_TLS_PROTOCOL)
        try {
            socket.startHandshake()
        } catch (e: IOException) {
            // A server that rejects our client cert typically aborts the handshake mid-flight: it
            // closes its socket while our remaining flight bytes are still sitting in its receive
            // buffer, which makes the OS send a TCP RST instead of a graceful close. That surfaces to
            // us as a plain SocketException ("Broken pipe" / "Connection reset"), not an SSLException
            // — so we catch IOException broadly here rather than SSLException specifically.
            socket.close()
            throw TlsHandshakeRejectedException(e)
        }
        // Set only after the handshake completes: a timeout here would otherwise abort a slow-but-
        // legitimate handshake instead of just bounding post-handshake application reads.
        socket.soTimeout = SOCKET_READ_POLL_MILLIS
        val rsa = peer!!.publicKey as RSAPublicKey
        JvmTlsConnection(socket, RsaPublicParams(rsa.modulus.toByteArray(), rsa.publicExponent.toByteArray()))
    }
}

private class JvmTlsConnection(
    private val socket: SSLSocket,
    override val serverPublicParams: RsaPublicParams,
) : TlsConnection {
    override suspend fun read(max: Int): ByteArray = withContext(Dispatchers.IO) {
        val buf = ByteArray(max)
        while (true) {
            coroutineContext.ensureActive()
            try {
                val n = socket.inputStream.read(buf, 0, max)
                return@withContext if (n < 0) throw EofException() else buf.copyOf(n)
            } catch (e: SocketTimeoutException) {
                // No data within one poll interval; loop back so a cancelled outer coroutine (e.g.
                // withTimeout) gets checked instead of blocking on the next read indefinitely.
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        socket.outputStream.write(bytes)
        socket.outputStream.flush()
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
