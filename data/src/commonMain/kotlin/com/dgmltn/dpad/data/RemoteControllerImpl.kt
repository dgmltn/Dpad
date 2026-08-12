package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.mapping.toDomain
import com.dgmltn.dpad.data.mapping.toKeyCode
import com.dgmltn.dpad.data.resolve.HostResolver
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.session.RemoteSession
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    override fun connect(device: PairedDevice) {
        disconnect()
        connectJob = scope.launch {
            val identity = identityStore.protocolIdentity()
            val s = RemoteSession(
                scope = scope,
                factory = TlsSocketFactory(identity),
                resolveHost = { HostResolver.resolve(device, discovery.discovered().first()) },
            )
            session = s
            collectorJobs = listOf(
                scope.launch { s.state.collect { _connection.value = it.toDomain() } },
                scope.launch { s.volume.collect { _volume.value = it?.toDomain() } },
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
    }

    override fun press(key: RemoteKey) { session?.sendKey(key.toKeyCode()) }
    override fun launchApp(appLinkUrl: String) { session?.launchApp(appLinkUrl) }
    override fun sendText(text: String) {
        // Per-character key events: map ASCII to KEYCODE_* is out of scope for Plan 2's protocol surface;
        // Plan 3 wires the text-input sheet. For now, forward nothing here — RemoteSession has no text API yet.
        // (Documented gap: text input is delivered in Plan 3 alongside the UI that produces it.)
    }
}
