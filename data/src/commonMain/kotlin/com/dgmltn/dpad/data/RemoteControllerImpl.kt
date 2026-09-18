package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.mapping.charToKeyCodes
import com.dgmltn.dpad.data.mapping.toDomain
import com.dgmltn.dpad.data.mapping.toKeyCode
import com.dgmltn.dpad.data.mapping.toTvPower
import com.dgmltn.dpad.data.resolve.resolveHostForAttempt
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.session.RemoteSession
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * [scope] MUST be single-threaded-confined (e.g. viewModelScope / Dispatchers.Main.immediate) —
 * RemoteSession's generation guard is atomic only under cooperative scheduling (Plan-1 note).
 */
class RemoteControllerImpl(
    private val identityStore: ClientIdentityStore,
    private val discovery: DeviceDiscovery,
    private val scope: CoroutineScope,
) : RemoteController {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    private val _volume = MutableStateFlow<Volume?>(null)
    override val volume: StateFlow<Volume?> = _volume.asStateFlow()
    private val _power = MutableStateFlow<TvPower?>(null)
    override val power: StateFlow<TvPower?> = _power.asStateFlow()

    // DROP_OLDEST + 1 slot: tryEmit never fails and never suspends a UI-thread caller; a burst
    // collapsing to one emission is fine for "was there activity".
    private val _interactions = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val interactions: SharedFlow<Unit> = _interactions.asSharedFlow()

    private var session: RemoteSession? = null

    // The state/volume collector Jobs launched by the current session's connect(), tracked so
    // disconnect() can cancel them — RemoteSession's own contract ("nothing outlives the
    // connection") otherwise only covers RemoteSession's internal coroutines, not these
    // collectors we launch on top of it.
    private var collectorJobs: List<Job> = emptyList()

    // The outer coroutine launched by connect() itself, tracked so disconnect() can cancel it.
    // Its body suspends at identityStore.protocolIdentity() BEFORE assigning session/collectorJobs,
    // so without tracking+cancelling this job, a second overlapping connect() can't interrupt an
    // in-flight first launch that hasn't reached that assignment yet — both launches then race to
    // assign session/collectorJobs, and whichever resumes last silently orphans the other's live
    // RemoteSession and collectors (leaked connection, flickering _connection/_volume).
    private var connectJob: Job? = null

    // The device the current connect() targets; cleared only by disconnect(). Keyed on this rather
    // than on _connection because the state is still Disconnected while connect()'s launch is
    // suspended before building its session — a repeat connect() in that window must be a no-op too.
    private var currentDevice: PairedDevice? = null

    override fun connect(device: PairedDevice) {
        if (isAlreadyTargeting(currentDevice, device, _connection.value)) return
        disconnect()
        currentDevice = device
        connectJob = scope.launch {
            val identity = identityStore.protocolIdentity()
            // Attempt 0 uses the stored IP immediately (instant reconnect when the TV kept its
            // address); mDNS is consulted only on retries, and only briefly. See resolveHostForAttempt.
            var attempt = 0
            val s = RemoteSession(
                scope = scope,
                factory = TlsSocketFactory(identity),
                resolveHost = {
                    resolveHostForAttempt(attempt, device, { discovery.discovered().first() })
                        .also { attempt++ }
                },
                // Snappier wake-reconnects than the protocol default (max 15s): cap the backoff at 4s.
                backoffMillis = listOf(500L, 1_000L, 2_000L, 4_000L),
            )
            session = s
            collectorJobs = listOf(
                scope.launch { s.state.collect { _connection.value = it.toDomain() } },
                scope.launch { s.volume.collect { _volume.value = it?.toDomain() } },
                scope.launch { s.power.collect { _power.value = it.toTvPower() } },
            )
            s.connect()
        }
    }

    override fun disconnect() {
        // Cancel the outer connect() launch FIRST: if it's still suspended before assigning
        // session/collectorJobs, this interrupts it there so it can never clobber the state we're
        // about to reset below.
        connectJob?.cancel(); connectJob = null
        collectorJobs.forEach { it.cancel() }
        collectorJobs = emptyList()
        session?.disconnect(); session = null
        _connection.value = ConnectionState.Disconnected
        _volume.value = null
        _power.value = null
        currentDevice = null
    }

    override fun press(key: RemoteKey) {
        _interactions.tryEmit(Unit)
        session?.sendKey(key.toKeyCode())
    }
    override fun launchApp(appLinkUrl: String) {
        _interactions.tryEmit(Unit)
        session?.launchApp(appLinkUrl)
    }
    override fun sendText(text: String) {
        _interactions.tryEmit(Unit)
        text.forEach { ch -> charToKeyCodes(ch).forEach { session?.sendKey(it) } }
    }
}

/**
 * True when [connect][RemoteControllerImpl.connect] for [requested] should be a no-op: it's the
 * exact device already being targeted and the session hasn't given up for a re-pair.
 */
internal fun isAlreadyTargeting(current: PairedDevice?, requested: PairedDevice, state: ConnectionState): Boolean =
    current == requested && state != ConnectionState.PairingRequired
