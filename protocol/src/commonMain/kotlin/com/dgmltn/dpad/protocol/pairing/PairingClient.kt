package com.dgmltn.dpad.protocol.pairing

import co.touchlab.kermit.Logger
import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.transport.EofException
import com.dgmltn.dpad.protocol.transport.TlsConnection
import com.dgmltn.dpad.protocol.transport.TlsHandshakeRejectedException
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import com.dgmltn.dpad.protocol.transport.readFrame
import com.dgmltn.dpad.protocol.transport.writeFrame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.toByteString
import pairing.PairingConfiguration
import pairing.PairingEncoding
import pairing.PairingMessage
import pairing.PairingOption
import pairing.PairingRequest
import pairing.PairingSecret
import pairing.RoleType

private const val TAG = "Pairing"
private const val PROTOCOL_VERSION = 2

sealed interface PairingEvent {
    /** The TV is now showing a code; call [PairingClient.submitCode] with what the user typed. */
    data object WaitingForCode : PairingEvent
    data object Paired : PairingEvent
    data class Failed(val reason: PairingFailure) : PairingEvent
}

enum class PairingFailure { WRONG_CODE, REJECTED, CONNECTION_LOST, TIMEOUT }

/** Internal signal that a reply's status wasn't STATUS_OK before a secret was sent; maps to [PairingFailure.REJECTED]. */
private class PairingRejectedException : Exception()

/** Internal signal that the TV rejected our submitted secret; maps to [PairingFailure.WRONG_CODE]. */
private class BadSecretException : Exception()

/**
 * Drives the Android TV pairing handshake (request → option → configuration → secret) over a
 * fresh TLS connection to port 6467, emitting [PairingEvent]s as it goes.
 *
 * Usage: call [start], wait for [PairingEvent.WaitingForCode] on [events], then call [submitCode]
 * with what the user typed off the TV's on-screen code.
 *
 * [timeout] bounds every protocol await (trailing/optional so `PairingClient(factory, identity)`
 * still works); tests shrink it to keep a deliberately-silent-peer test fast.
 */
class PairingClient(
    private val factory: TlsSocketFactory,
    private val identity: ClientIdentity,
    private val clientName: String = "Dpad",
    private val serviceName: String = "com.dgmltn.dpad",
    private val timeout: Duration = 10.seconds,
) {
    // replay = 8 comfortably covers this handshake's whole event history (WaitingForCode plus one
    // terminal event), so a collector that starts late — e.g. `start()` racing ahead on another
    // coroutine — still sees everything that already happened instead of missing it.
    private val _events = MutableSharedFlow<PairingEvent>(replay = 8)
    val events: Flow<PairingEvent> = _events.asSharedFlow()

    private var connection: TlsConnection? = null

    /** Connects to [host]:[port] and runs request→option→configuration, emitting [PairingEvent.WaitingForCode] on success. */
    suspend fun start(host: String, port: Int = 6467) {
        try {
            withRealTimeout {
                val conn = factory.connect(host, port)
                connection = conn

                Logger.d(tag = TAG) { "connected to $host:$port; sending pairing_request" }
                exchange(
                    conn,
                    PairingMessage(
                        protocol_version = PROTOCOL_VERSION,
                        status = PairingMessage.Status.STATUS_OK,
                        pairing_request = PairingRequest(client_name = clientName, service_name = serviceName),
                    ),
                )

                Logger.d(tag = TAG) { "pairing_request acked; sending pairing_option" }
                exchange(
                    conn,
                    PairingMessage(
                        protocol_version = PROTOCOL_VERSION,
                        status = PairingMessage.Status.STATUS_OK,
                        pairing_option = PairingOption(
                            input_encodings = listOf(HEX_ENCODING),
                            preferred_role = RoleType.ROLE_TYPE_INPUT,
                        ),
                    ),
                )

                Logger.d(tag = TAG) { "pairing_option acked; sending pairing_configuration" }
                exchange(
                    conn,
                    PairingMessage(
                        protocol_version = PROTOCOL_VERSION,
                        status = PairingMessage.Status.STATUS_OK,
                        pairing_configuration = PairingConfiguration(
                            encoding = HEX_ENCODING,
                            client_role = RoleType.ROLE_TYPE_INPUT,
                        ),
                    ),
                )
            }
            Logger.d(tag = TAG) { "pairing_configuration acked -> WaitingForCode" }
            _events.emit(PairingEvent.WaitingForCode)
        } catch (e: TimeoutCancellationException) {
            fail(PairingFailure.TIMEOUT)
        } catch (e: EofException) {
            fail(PairingFailure.CONNECTION_LOST)
        } catch (e: PairingRejectedException) {
            fail(PairingFailure.REJECTED)
        } catch (e: TlsHandshakeRejectedException) {
            fail(PairingFailure.REJECTED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(PairingFailure.CONNECTION_LOST)
        }
    }

    /** Computes and sends [pairing.PairingSecret] for [code]; emits [PairingEvent.Paired] or [PairingEvent.Failed]. */
    suspend fun submitCode(code: String) {
        val conn = connection
        if (conn == null) {
            fail(PairingFailure.CONNECTION_LOST)
            return
        }
        val secret = computePairingSecret(identity.publicParams, conn.serverPublicParams, code)
        if (secret == null) {
            Logger.d(tag = TAG) { "code check byte mismatch -> WRONG_CODE (nothing sent)" }
            fail(PairingFailure.WRONG_CODE)
            return
        }
        try {
            withRealTimeout {
                conn.writeFrame(
                    PairingMessage(
                        protocol_version = PROTOCOL_VERSION,
                        status = PairingMessage.Status.STATUS_OK,
                        pairing_secret = PairingSecret(secret = secret.toByteString()),
                    ).encode()
                )
                val ack = PairingMessage.ADAPTER.decode(conn.readFrame())
                if (ack.status != PairingMessage.Status.STATUS_OK) throw BadSecretException()
            }
            Logger.d(tag = TAG) { "pairing_secret acked -> Paired" }
            _events.emit(PairingEvent.Paired)
        } catch (e: TimeoutCancellationException) {
            fail(PairingFailure.TIMEOUT)
        } catch (e: EofException) {
            fail(PairingFailure.CONNECTION_LOST)
        } catch (e: BadSecretException) {
            fail(PairingFailure.WRONG_CODE)
        }
    }

    /** Aborts the in-flight pairing attempt by closing the connection. */
    fun cancel() {
        connection?.close()
    }

    private suspend fun exchange(conn: TlsConnection, msg: PairingMessage) {
        conn.writeFrame(msg.encode())
        val reply = PairingMessage.ADAPTER.decode(conn.readFrame())
        if (reply.status != PairingMessage.Status.STATUS_OK) throw PairingRejectedException()
    }

    /**
     * [withTimeout] whose deadline runs in real time. A plain `withTimeout` schedules its deadline
     * as a `delay` on the *caller's* dispatcher; under `runTest`, that's the virtual-time test
     * dispatcher, which fires the deadline the instant it looks idle — which it does the moment
     * this coroutine hops onto a real thread to block on socket I/O (invisible to the virtual
     * clock). [Dispatchers.Default] isn't part of that virtual clock (and, unlike [Dispatchers.IO],
     * is public API on every KMP target), so running the deadline there keeps it on real time.
     */
    private suspend fun <T> withRealTimeout(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(timeout) { block() } }

    /**
     * Every [PairingFailure] here is terminal — this client doesn't support resuming a handshake
     * after REJECTED/TIMEOUT/CONNECTION_LOST, and WRONG_CODE isn't retried on the same connection
     * either (a fresh [start] is required) — so the connection is closed before the event is
     * emitted, guaranteeing no socket is left open past the point callers observe the failure.
     * [TlsConnection.close] is safe to call more than once, so this doesn't conflict with a
     * caller-initiated [cancel].
     */
    private suspend fun fail(reason: PairingFailure) {
        Logger.d(tag = TAG) { "Failed($reason)" }
        connection?.close()
        _events.emit(PairingEvent.Failed(reason))
    }

    private companion object {
        val HEX_ENCODING = PairingEncoding(type = PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL, symbol_length = 6)
    }
}
