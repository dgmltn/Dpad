package com.dgmltn.dpad.protocol.session

import co.touchlab.kermit.Logger
import com.dgmltn.dpad.protocol.transport.TlsConnection
import com.dgmltn.dpad.protocol.transport.TlsHandshakeRejectedException
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import com.dgmltn.dpad.protocol.transport.readFrame
import com.dgmltn.dpad.protocol.transport.writeFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import remote.RemoteAppLinkLaunchRequest
import remote.RemoteConfigure
import remote.RemoteDeviceInfo
import remote.RemoteDirection
import remote.RemoteKeyCode
import remote.RemoteKeyInject
import remote.RemoteMessage
import remote.RemotePingResponse
import remote.RemoteSetActive

private const val TAG = "Session"

data class HostAddress(val host: String, val port: Int = 6466)
data class VolumeState(val level: Int, val max: Int, val muted: Boolean)

/**
 * Number of consecutive [TlsHandshakeRejectedException]s — on the SAME logical connect loop,
 * across backoff retries — required before [RemoteSession] treats a rejected client cert as a
 * real pairing failure rather than a transient network blip.
 *
 * Per Task 6's review: a server-side cert rejection surfaces as a broken-pipe RST that is
 * INDISTINGUISHABLE, at the TLS layer, from a transient connection reset — so a *single*
 * [TlsHandshakeRejectedException] must NOT be mapped straight to [SessionState.PairingRequired].
 * Instead a rejection is treated as retryable (state -> [SessionState.Connecting], backoff,
 * re-resolve host, retry) and only escalated to [SessionState.PairingRequired] once it has
 * happened [PAIRING_REQUIRED_THRESHOLD] times in a row. 3 was chosen as a small-but-not-hair-
 * trigger threshold: one blip shouldn't cause a false PairingRequired, but a genuinely-unpaired
 * TV shouldn't need many retries before the user is told to re-pair either. The counter resets to
 * 0 the instant any [TlsSocketFactory.connect] attempt succeeds (handshake actually completed) —
 * a plain connect-time `IOException` in between (host unreachable, etc.; see the catch below)
 * does NOT reset it, since that's ordinary transient noise, not evidence the cert is fine. So the
 * counter really measures "consecutive rejections since the last successful handshake," not
 * "consecutive rejections with literally nothing else in between."
 */
internal const val PAIRING_REQUIRED_THRESHOLD = 3

/**
 * Drives the post-pairing Android TV remote session: connect (with auto-reconnect/backoff),
 * handshake (remote_configure/remote_set_active), keepalive (ping/pong), key/app-launch sends,
 * and volume tracking.
 *
 * All of [connect]'s coroutines (the connect/reconnect loop, the per-connection reader, and the
 * per-connection writer) are children of [scope] (directly, or via `coroutineScope` inside the
 * loop), so cancelling [scope] — or calling [disconnect] — tears every one of them down; nothing
 * outlives the connection or the session.
 */
class RemoteSession(
    private val scope: CoroutineScope,
    private val factory: TlsSocketFactory,
    /** Re-invoked before every (re)connect attempt — Plan 2 plugs mDNS re-resolution in here. */
    private val resolveHost: suspend () -> HostAddress,
    private val clientModel: String = "Dpad",
    private val backoffMillis: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 15_000),
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(null)
    val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private var loopJob: Job? = null

    // The current connection's outbound queue, or null when not Connected. Recreated fresh per
    // connection (not one long-lived channel) so a send racing a disconnect lands in a channel
    // that's about to be abandoned rather than silently resurrected on the NEXT connection.
    @Volatile private var activeChannel: Channel<RemoteMessage>? = null

    // Bumped on every connect() and disconnect(); each runLoop() captures the value current at
    // its own launch as `myGeneration`. Cancelling a Job is cooperative — a runLoop parked inside
    // the blocking, not-reliably-cancellable factory.connect() (see TlsSocketFactory) can still be
    // executing after disconnect() (or an immediately-following connect() that starts a NEW
    // generation) has already moved on. Without this guard, that stale runLoop would go on to
    // write `activeChannel`/`_state` as if it were still the current session, clobbering the new
    // generation's real channel/state out from under it. Every shared-state write in runLoop is
    // gated on `generation == myGeneration` so a superseded generation silently closes whatever
    // it opened and stops touching shared state instead.
    @Volatile private var generation: Int = 0

    /** Idempotent: starts the connect/reconnect loop if it isn't already running. */
    fun connect() {
        if (loopJob?.isActive == true) return
        val myGeneration = ++generation
        loopJob = scope.launch { runLoop(myGeneration) }
    }

    /** Stops the connect/reconnect loop and any in-flight connection; state -> [SessionState.Disconnected]. */
    fun disconnect() {
        // Invalidate the current generation immediately — even before cancellation of loopJob is
        // actually observed by its coroutine — so a runLoop that's stuck in blocking I/O at this
        // instant is already stale by the time it next checks, rather than only becoming stale if
        // and when a future connect() happens to be called.
        generation++
        loopJob?.cancel()
        loopJob = null
        activeChannel = null
        _state.value = SessionState.Disconnected
    }

    /** Fire-and-forget: dropped silently (never queued) when not [SessionState.Connected]. */
    fun sendKey(keyCode: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT) {
        send(RemoteMessage(remote_key_inject = RemoteKeyInject(key_code = keyCode, direction = direction)))
    }

    /** Fire-and-forget: dropped silently (never queued) when not [SessionState.Connected]. */
    fun launchApp(appLinkUrl: String) {
        send(RemoteMessage(remote_app_link_launch_request = RemoteAppLinkLaunchRequest(app_link = appLinkUrl)))
    }

    private fun send(message: RemoteMessage) {
        if (_state.value != SessionState.Connected) {
            Logger.d(tag = TAG) { "not connected — dropping $message" }
            return
        }
        val sent = activeChannel?.trySend(message)?.isSuccess == true
        if (!sent) {
            Logger.d(tag = TAG) { "send channel unavailable — dropping $message" }
        }
    }

    private fun isCurrentGeneration(myGeneration: Int) = generation == myGeneration

    private suspend fun runLoop(myGeneration: Int) {
        var consecutiveRejections = 0
        var backoffIndex = 0
        while (isCurrentGeneration(myGeneration)) {
            _state.value = SessionState.Connecting
            val host = resolveHost()

            val connection: TlsConnection = try {
                factory.connect(host.host, host.port)
            } catch (e: TlsHandshakeRejectedException) {
                consecutiveRejections++
                Logger.w(tag = TAG) {
                    "TLS handshake rejected by ${host.host}:${host.port} " +
                        "($consecutiveRejections/$PAIRING_REQUIRED_THRESHOLD consecutive) — treating as retryable"
                }
                if (consecutiveRejections >= PAIRING_REQUIRED_THRESHOLD) {
                    if (isCurrentGeneration(myGeneration)) _state.value = SessionState.PairingRequired
                    return
                }
                backoffIndex = delayBackoff(backoffIndex)
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Connect-time refusal (host unreachable, etc.) is a plain IOException, not
                // TlsHandshakeRejectedException — always a normal transient, never counts toward
                // the pairing-rejection threshold above.
                Logger.w(tag = TAG) { "connect to ${host.host}:${host.port} failed: $e" }
                backoffIndex = delayBackoff(backoffIndex)
                continue
            }

            if (!isCurrentGeneration(myGeneration)) {
                // A newer generation (disconnect()/connect()) superseded us while factory.connect()
                // — a blocking call that doesn't reliably observe cancellation — was in flight.
                // This connection is ours alone and stale; close it without touching any shared
                // state (activeChannel/_state) that the new generation now owns.
                connection.close()
                return
            }

            // A successful handshake proves our cert IS accepted, and that this attempt made real
            // progress — reset both counters so a later blip starts counting from zero again.
            consecutiveRejections = 0
            backoffIndex = 0

            val channel = Channel<RemoteMessage>(Channel.UNLIMITED)
            activeChannel = channel
            try {
                coroutineScope {
                    launch { writeLoop(connection, channel) }
                    // Every outbound frame — handshake replies, ping responses, queued
                    // sendKey/launchApp — flows through `channel` so writeLoop is this
                    // connection's ONLY writer. Two coroutines calling connection.writeFrame()
                    // directly and concurrently could interleave bytes on the wire; routing
                    // everything through one UNLIMITED channel drained by one coroutine rules
                    // that out structurally instead of relying on a lock.
                    handshakeReplies(connection, channel)
                    if (isCurrentGeneration(myGeneration)) _state.value = SessionState.Connected
                    readLoop(connection, channel)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // EofException (peer closed / dropConnection()) or any other read/write failure
                // (e.g. SSLException mid-session) — connection lost. Flip to Connecting BEFORE the
                // backoff delay below, not after: state must reflect "the TV is gone" for the
                // whole backoff window (up to 15s in prod), not lie as Connected until the next
                // attempt happens to succeed.
                Logger.d(tag = TAG) { "session connection lost: $e" }
                if (isCurrentGeneration(myGeneration)) _state.value = SessionState.Connecting
            } finally {
                if (isCurrentGeneration(myGeneration)) activeChannel = null
                channel.close()
                connection.close()
            }
            backoffIndex = delayBackoff(backoffIndex)
        }
    }

    /**
     * [delay] on [Dispatchers.Default] rather than whatever dispatcher [scope] happens to run on —
     * same reasoning as [com.dgmltn.dpad.protocol.pairing.PairingClient]'s `withRealTimeout`: a
     * plain `delay` here would schedule its wakeup on the CALLING coroutine's dispatcher, which
     * under `runTest` is the virtual-time test dispatcher. That dispatcher auto-advances (fires
     * instantly, in real time) any `delay` it schedules the moment it looks idle — which it does
     * as soon as this coroutine isn't the only thing pending, e.g. while a sibling is genuinely
     * blocked on real socket I/O on `Dispatchers.IO`. [Dispatchers.Default] isn't part of that
     * virtual clock, so backoff always takes real wall-clock time — required both so a real caller
     * gets real backoff, and so tests can observe timing (not just ordering) around it.
     */
    private suspend fun delayBackoff(index: Int): Int {
        withContext(Dispatchers.Default) { delay(backoffMillis.getOrElse(index) { backoffMillis.last() }) }
        return index + 1
    }

    /** Waits for remote_configure then remote_set_active, queueing a reply to each per the session handshake contract. */
    private suspend fun handshakeReplies(connection: TlsConnection, channel: Channel<RemoteMessage>) {
        while (true) {
            val msg = RemoteMessage.ADAPTER.decode(connection.readFrame())
            if (msg.remote_configure != null) {
                channel.trySend(
                    RemoteMessage(
                        remote_configure = RemoteConfigure(
                            code1 = 622,
                            device_info = RemoteDeviceInfo(
                                model = clientModel,
                                vendor = "dgmltn",
                                unknown1 = 1,
                                unknown2 = "1",
                                package_name = "com.dgmltn.dpad",
                                app_version = "1.0",
                            ),
                        ),
                    ),
                )
                break
            }
        }
        while (true) {
            val msg = RemoteMessage.ADAPTER.decode(connection.readFrame())
            if (msg.remote_set_active != null) {
                channel.trySend(RemoteMessage(remote_set_active = RemoteSetActive(active = 622)))
                break
            }
        }
    }

    /** Runs for the life of the connection: answers pings immediately, tracks volume, records nothing else. */
    private suspend fun readLoop(connection: TlsConnection, channel: Channel<RemoteMessage>) {
        while (true) {
            val msg = RemoteMessage.ADAPTER.decode(connection.readFrame())
            msg.remote_ping_request?.let { ping ->
                channel.trySend(RemoteMessage(remote_ping_response = RemotePingResponse(val1 = ping.val1)))
            }
            msg.remote_set_volume_level?.let { vol ->
                _volume.value = VolumeState(level = vol.volume_level, max = vol.volume_max, muted = vol.volume_muted)
            }
        }
    }

    /** Drains [channel] onto the wire; callers ([sendKey]/[launchApp]) never block on this. */
    private suspend fun writeLoop(connection: TlsConnection, channel: Channel<RemoteMessage>) {
        for (message in channel) {
            connection.writeFrame(message.encode())
        }
    }
}
