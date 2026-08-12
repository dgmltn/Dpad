package com.dgmltn.dpad.data

import co.touchlab.kermit.Logger
import com.dgmltn.dpad.data.mapping.toProgress
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.pairing.PairingClient
import com.dgmltn.dpad.protocol.pairing.PairingEvent
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun defaultId(): String = Uuid.random().toString()

class DevicePairerImpl(
    private val identityStore: ClientIdentityStore,
    private val deviceRepository: DeviceRepository,
    private val scope: CoroutineScope,
    private val newId: () -> String = { defaultId() },
) : DevicePairer {
    private val log = Logger.withTag("DevicePairer")
    private val _progress = MutableStateFlow<PairingProgress>(PairingProgress.Connecting)
    override val progress: Flow<PairingProgress> = _progress.asStateFlow()

    private var client: PairingClient? = null
    private var pairing: DiscoveredDevice? = null

    // The events-collector Job launched by start(), tracked so cancel() (and a subsequent
    // start() retry) can cancel it — otherwise every pair/cancel/retry cycle leaks a collector.
    private var eventsJob: Job? = null

    // The outer coroutine that performs start()'s pre-assignment suspend work (identityStore.
    // protocolIdentity()) and launches the events collector, tracked so a second overlapping
    // start()/cancel() can interrupt it before it clobbers client/eventsJob. Without this,
    // start() ran that suspend work directly on the caller's coroutine with nothing to cancel it
    // by — two overlapping start() calls could both suspend before either assigned client/
    // eventsJob, then both resume and race to assign them, orphaning whichever lost the race
    // (leaked PairingClient connection, orphaned collector still writing into _progress). Using
    // async (not launch) so start() keeps suspend-fun exception transparency via await() below.
    private var startJob: Deferred<Unit>? = null

    override suspend fun start(device: DiscoveredDevice) {
        // Cancel any in-flight start() FIRST: if it's still suspended before assigning client/
        // eventsJob, this interrupts it there so it can never clobber the state we're about to
        // set up below.
        startJob?.cancel(); startJob = null
        eventsJob?.cancel(); eventsJob = null
        pairing = device
        _progress.value = PairingProgress.Connecting
        val job = scope.async {
            val identity = identityStore.protocolIdentity()
            val c = PairingClient(TlsSocketFactory(identity), identity)
            client = c
            eventsJob = scope.launch {
                c.events.collect { event ->
                    _progress.value = event.toProgress()
                    if (event is PairingEvent.Paired) persist(device)
                }
            }
            c.start(device.host)   // pairing port defaults to 6467
        }
        startJob = job
        job.await()
    }

    override suspend fun submitCode(code: String) { client?.submitCode(code) }
    override fun cancel() {
        startJob?.cancel(); startJob = null
        client?.cancel(); client = null
        eventsJob?.cancel(); eventsJob = null
    }

    private suspend fun persist(device: DiscoveredDevice) {
        val paired = PairedDevice(id = newId(), name = device.name, host = device.host,
            port = 6466, serviceName = device.name)
        deviceRepository.upsert(paired)
        deviceRepository.setLastUsed(paired.id)
        log.i { "Paired ${device.name}, persisted as ${paired.id}" }
    }
}
