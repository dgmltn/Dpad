package com.dgmltn.dpad.protocol.fake

import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.crypto.sha256
import com.dgmltn.dpad.protocol.crypto.stripLeadingZeros
import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import com.dgmltn.dpad.protocol.transport.BytePipe
import com.dgmltn.dpad.protocol.transport.EofException
import com.dgmltn.dpad.protocol.transport.SOCKET_READ_POLL_MILLIS
import com.dgmltn.dpad.protocol.transport.TV_TLS_PROTOCOL
import com.dgmltn.dpad.protocol.transport.readFrame
import com.dgmltn.dpad.protocol.transport.writeFrame
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pairing.PairingConfiguration
import pairing.PairingConfigurationAck
import pairing.PairingEncoding
import pairing.PairingMessage
import pairing.PairingOption
import pairing.PairingRequestAck
import pairing.PairingSecretAck
import pairing.RoleType
import remote.RemoteConfigure
import remote.RemoteDeviceInfo
import remote.RemoteKeyCode
import remote.RemoteMessage
import remote.RemotePingRequest
import remote.RemoteSetActive
import remote.RemoteSetVolumeLevel

/**
 * TLS-level test harness for a fake Android TV pairing/session server. This task only wires up
 * mutual TLS (accept, capture the client cert, optionally reject it) and echoes bytes through a
 * [BytePipe]; Tasks 7/8 layer the actual pairing/session protocol on top of [start]'s handler.
 */
class FakeTvServer(private val requireClientCert: Boolean) : AutoCloseable {
    val serverIdentity: ClientIdentity = ClientIdentityGenerator.generate("dpad-fake-tv")

    /** When true, the server's trust manager rejects the client's certificate during handshake. */
    @Volatile var rejectClientCerts: Boolean = false

    @Volatile var lastClientCertParams: RsaPublicParams? = null
        private set

    /**
     * Set when a connection's [handler] throws anything other than a handshake/EOF IOException —
     * most importantly an `AssertionError` from a Task 7/8 test handler. Surfaced by [close]
     * (rethrown wrapped) rather than left to kill the accept thread silently.
     */
    @Volatile var handlerFailure: Throwable? = null
        private set

    private val serverSocket: SSLServerSocket
    private var acceptThread: Thread? = null

    @Volatile private var closed = false

    init {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ClientIdentityGenerator.derOf(serverIdentity.certificatePem).inputStream())
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(ClientIdentityGenerator.derOf(serverIdentity.privateKeyPem)))
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("dpad", key, CharArray(0), arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, CharArray(0)) }
        val trustClientCerts = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                if (rejectClientCerts) throw CertificateException("rejected by FakeTvServer for test")
                val rsa = chain[0].publicKey as RSAPublicKey
                lastClientCertParams = RsaPublicParams(rsa.modulus.toByteArray(), rsa.publicExponent.toByteArray())
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf(trustClientCerts), null)
        }
        // Ephemeral port; bound+listening immediately so connect() can be called right after start().
        serverSocket = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        serverSocket.needClientAuth = requireClientCert
        serverSocket.enabledProtocols = arrayOf(TV_TLS_PROTOCOL)
    }

    val port: Int get() = serverSocket.localPort

    /** Accepts one connection at a time on a daemon thread; each connection's [handler] runs inside [runBlocking]. */
    fun start(handler: suspend (BytePipe) -> Unit) {
        val thread = Thread({
            while (!closed) {
                val socket = try {
                    serverSocket.accept() as SSLSocket
                } catch (e: Exception) {
                    // The expected shutdown path is close() closing serverSocket, which unblocks this
                    // accept() with a SocketException — but log so an unexpected accept() failure
                    // (before close() was called) isn't fully silent either.
                    if (!closed) System.err.println("FakeTvServer: accept() failed: $e")
                    break
                }
                try {
                    socket.startHandshake()
                    // Only after the handshake, same reasoning as JvmTlsConnection: bounds
                    // application-level reads so a handler that parks on a read (see
                    // PairingClientTest.timesOutWhenServerSilent) doesn't block this daemon thread
                    // past the peer closing — without touching handshake timing.
                    socket.soTimeout = SOCKET_READ_POLL_MILLIS
                    runBlocking { handler(SocketBytePipe(socket)) }
                } catch (e: IOException) {
                    // Client cert rejected during handshake (surfaces as SSLException or, if we close
                    // before draining the client's remaining flight bytes, a plain SocketException —
                    // see the matching comment in TlsSocketFactory), or the connection was reset.
                    // Either way: nothing more to do, move on to the next connection.
                } catch (e: EofException) {
                    // Handler hit EOF because the client closed early — fine, move on to the next connection.
                } catch (t: Throwable) {
                    // Anything else — most importantly an AssertionError from a test's handler — must
                    // not kill this daemon thread silently (the loop would just stop accepting, and
                    // tests would hang or see a spurious EofException instead of the real failure).
                    // Record it for close() to surface, and keep serving further connections.
                    handlerFailure = t
                } finally {
                    runCatching { socket.close() }
                }
            }
        }, "FakeTvServer-accept")
        thread.isDaemon = true
        acceptThread = thread
        thread.start()
    }

    override fun close() {
        closed = true
        runCatching { serverSocket.close() }
        acceptThread?.join(1000)
        // Surfaces a swallowed handler failure (e.g. a test assertion) to whoever called close() —
        // typically Kotlin's `use { }`, which propagates this as/alongside the block's result.
        handlerFailure?.let { throw AssertionError("FakeTvServer handler failed", it) }
    }

    /**
     * Scripted pairing peer (Task 7): drives request → option → configuration, then computes the
     * pairing code the real client will independently derive — sha256(clientMod||clientExp||
     * serverMod||serverExp||nonce)[0] as the check byte, with each RSA param leading-zero-stripped,
     * exactly like [com.dgmltn.dpad.protocol.pairing.computePairingSecret] — from the *real* client
     * cert (captured off the TLS handshake) and this server's own cert, plus a random 2-byte nonce.
     * Hands the resulting code to [showCode], then accepts or rejects the client's submitted secret
     * against that same hash.
     */
    fun startPairingServer(showCode: (String) -> Unit) {
        // needClientAuth (set from `requireClientCert` in init) and wantClientAuth are mutually
        // exclusive in JSSE — setting wantClientAuth unconditionally would silently downgrade a
        // *required* client cert to optional. Only opt into "request but don't require" when the
        // cert isn't already mandatory; pairing still needs to see the client's cert either way to
        // derive the code, and a required cert already guarantees that.
        if (!requireClientCert) serverSocket.wantClientAuth = true
        start { pipe ->
            val request = PairingMessage.ADAPTER.decode(pipe.readFrame())
            check(request.pairing_request != null) { "expected pairing_request, got $request" }
            pipe.writeFrame(
                PairingMessage(
                    protocol_version = 2,
                    status = PairingMessage.Status.STATUS_OK,
                    pairing_request_ack = PairingRequestAck(server_name = "dpad-fake-tv"),
                ).encode()
            )

            val option = PairingMessage.ADAPTER.decode(pipe.readFrame())
            check(option.pairing_option != null) { "expected pairing_option, got $option" }
            pipe.writeFrame(
                PairingMessage(
                    protocol_version = 2,
                    status = PairingMessage.Status.STATUS_OK,
                    pairing_option = PairingOption(
                        output_encodings = listOf(
                            PairingEncoding(type = PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL, symbol_length = 6),
                        ),
                        preferred_role = RoleType.ROLE_TYPE_OUTPUT,
                    ),
                ).encode()
            )

            val configuration = PairingMessage.ADAPTER.decode(pipe.readFrame())
            check(configuration.pairing_configuration != null) { "expected pairing_configuration, got $configuration" }

            // Compute the SAME code the client will independently validate, and show it, *before*
            // acking the configuration: the client emits WaitingForCode as soon as it reads that
            // ack, so showCode() must have already returned by the time those bytes hit the wire —
            // otherwise a test racing straight from WaitingForCode to submitCode(shownCode!!) could
            // observe shownCode still null.
            val client = lastClientCertParams
                ?: error("client cert wasn't captured during handshake — is wantClientAuth set?")
            val nonce = kotlin.random.Random.nextBytes(2)
            val expectedSecret = sha256(
                client.modulus.stripLeadingZeros() + client.exponent.stripLeadingZeros() +
                    serverIdentity.publicParams.modulus.stripLeadingZeros() + serverIdentity.publicParams.exponent.stripLeadingZeros() +
                    nonce
            )
            val code = "%02x".format(expectedSecret[0]) + nonce.joinToString("") { "%02x".format(it) }
            showCode(code)

            pipe.writeFrame(
                PairingMessage(
                    protocol_version = 2,
                    status = PairingMessage.Status.STATUS_OK,
                    pairing_configuration_ack = PairingConfigurationAck(),
                ).encode()
            )

            val secretMsg = PairingMessage.ADAPTER.decode(pipe.readFrame())
            val receivedSecret = secretMsg.pairing_secret?.secret
            val matches = receivedSecret != null && receivedSecret.toByteArray().contentEquals(expectedSecret)
            pipe.writeFrame(
                PairingMessage(
                    protocol_version = 2,
                    status = if (matches) PairingMessage.Status.STATUS_OK else PairingMessage.Status.STATUS_BAD_SECRET,
                    pairing_secret_ack = if (matches) PairingSecretAck(secret = receivedSecret) else null,
                ).encode()
            )
        }
    }

    /** Every [RemoteMessage] this session peer has read from the client, oldest first. */
    val receivedMessages: MutableList<RemoteMessage> = CopyOnWriteArrayList()

    // The BytePipe of whichever session connection is currently accepted, or null between
    // connections (see startSessionServer/dropConnection). @Volatile: sendPing/sendVolume/
    // dropConnection are called from the test's coroutine, the pipe is set from the accept
    // thread's handler coroutine.
    @Volatile private var currentPipe: BytePipe? = null

    // Guards writes to currentPipe so the handler's own scripted writes (remote_configure,
    // remote_set_active) can't interleave on the wire with a test-triggered sendPing/sendVolume.
    private val writeMutex = Mutex()

    private val pingResponses = Channel<Int>(Channel.UNLIMITED)
    private val keyInjects = Channel<RemoteKeyCode>(Channel.UNLIMITED)

    /**
     * Scripted session peer (Task 8): drives the post-pairing handshake the real client expects —
     * server sends remote_configure, awaits the client's own remote_configure reply, sends
     * remote_set_active, awaits the client's reply — then reads whatever the client sends
     * (key injects, app launches, ping responses) forever, recording each into [receivedMessages]
     * and routing ping responses / key injects to [awaitPingResponse]/[awaitKeyInject]. Test-side
     * pushes ([sendPing], [sendVolume]) and drops ([dropConnection]) act on [currentPipe].
     */
    fun startSessionServer() {
        // Same reasoning as startPairingServer: only request (don't require) a client cert when
        // one isn't already mandatory via requireClientCert/needClientAuth.
        if (!requireClientCert) serverSocket.wantClientAuth = true
        start { pipe ->
            currentPipe = pipe
            try {
                writeMessage(
                    pipe,
                    RemoteMessage(
                        remote_configure = RemoteConfigure(
                            code1 = 622,
                            device_info = RemoteDeviceInfo(
                                model = "faketv",
                                vendor = "dgmltn",
                                unknown1 = 1,
                                unknown2 = "1",
                                package_name = "com.dgmltn.dpad",
                                app_version = "1.0",
                            ),
                        ),
                    ),
                )
                val configureReply = RemoteMessage.ADAPTER.decode(pipe.readFrame())
                check(configureReply.remote_configure != null) { "expected remote_configure reply, got $configureReply" }
                receivedMessages += configureReply

                writeMessage(pipe, RemoteMessage(remote_set_active = RemoteSetActive(active = 622)))
                val setActiveReply = RemoteMessage.ADAPTER.decode(pipe.readFrame())
                check(setActiveReply.remote_set_active != null) { "expected remote_set_active reply, got $setActiveReply" }
                receivedMessages += setActiveReply

                // Session established from the peer's point of view; read whatever the client
                // sends (key injects, app launches, ping responses) until it disconnects.
                while (true) {
                    val msg = RemoteMessage.ADAPTER.decode(pipe.readFrame())
                    receivedMessages += msg
                    msg.remote_ping_response?.let { pingResponses.trySend(it.val1) }
                    msg.remote_key_inject?.let { keyInjects.trySend(it.key_code) }
                }
            } finally {
                if (currentPipe === pipe) currentPipe = null
            }
        }
    }

    /** Writes a `remote_ping_request(val1 = v)` on the current session connection. */
    suspend fun sendPing(v: Int) {
        val pipe = currentPipe ?: error("FakeTvServer.sendPing: no active session connection")
        writeMessage(pipe, RemoteMessage(remote_ping_request = RemotePingRequest(val1 = v)))
    }

    /** Writes a `remote_set_volume_level` on the current session connection. */
    suspend fun sendVolume(level: Int, max: Int, muted: Boolean) {
        val pipe = currentPipe ?: error("FakeTvServer.sendVolume: no active session connection")
        writeMessage(
            pipe,
            RemoteMessage(
                remote_set_volume_level = RemoteSetVolumeLevel(volume_level = level, volume_max = max, volume_muted = muted),
            ),
        )
    }

    /** Force-closes the current session connection, simulating a dropped connection mid-session. */
    fun dropConnection() {
        currentPipe?.close()
    }

    /** Suspends until a `remote_ping_response(val1 = v)` has been observed (discarding earlier/other values). */
    suspend fun awaitPingResponse(v: Int) {
        while (pingResponses.receive() != v) { /* keep draining */ }
    }

    /** Suspends until a `remote_key_inject` with [code] has been observed (discarding earlier/other codes). */
    suspend fun awaitKeyInject(code: RemoteKeyCode) {
        while (keyInjects.receive() != code) { /* keep draining */ }
    }

    private suspend fun writeMessage(pipe: BytePipe, message: RemoteMessage) {
        writeMutex.withLock { pipe.writeFrame(message.encode()) }
    }
}

private class SocketBytePipe(private val socket: SSLSocket) : BytePipe {
    override suspend fun read(max: Int): ByteArray = withContext(Dispatchers.IO) {
        val buf = ByteArray(max)
        while (true) {
            coroutineContext.ensureActive()
            try {
                val n = socket.inputStream.read(buf, 0, max)
                return@withContext if (n < 0) throw EofException() else buf.copyOf(n)
            } catch (e: SocketTimeoutException) {
                // No data within one poll interval — keep blocking (a handler that intentionally
                // parks, e.g. PairingClientTest.timesOutWhenServerSilent, should keep waiting for
                // the peer, not fail); this only bounds how long each individual read() call blocks.
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
