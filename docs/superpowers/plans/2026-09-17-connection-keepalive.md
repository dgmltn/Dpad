# Connection Keep-Alive & TV Power State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Android remote session alive across screen-off with a `connectedDevice` foreground service (notification with state-aware quick controls + Disconnect), end forgotten background connections automatically, surface the TV's power state in the shared UI, and reconnect whenever the remote screen starts.

**Architecture:** `:protocol`'s `RemoteSession` starts exposing the TV's `remote_start` power report. `:domain`'s `RemoteController` gains `power` + `interactions`, and its `connect()` becomes truly idempotent. A shared, virtual-time-tested `AutoDisconnectPolicy` in `:data` owns the background lifetime rules. `:app-android` adds a thin `ConnectionService` that mirrors the controller's connection state and renders the notification; it never owns the connection.

**Tech Stack:** Kotlin Multiplatform, kotlinx-coroutines (+ `kotlinx-coroutines-test` virtual time), Turbine, Koin, Compose Multiplatform, androidx lifecycle (`lifecycle-runtime-compose`, `lifecycle-process`), androidx.core (`NotificationCompat`, `ServiceCompat`).

**Spec:** `docs/superpowers/specs/2026-09-17-connection-keepalive-design.md`

## Global Constraints

- Branch: `feat/connection-keepalive` (already exists, holds the spec commit). WIP commits per task; subject starts with a bracketed one-word tag (`[protocol]`, `[data]`, `[ui]`, `[app]`, `[android]`, `[docs]`). **No `Co-Authored-By` trailer, no "Generated with Claude Code" footer.** Never push. Squash-merge to `main` only after Doug's on-device pass (Task 7).
- New dependency versions (looked up live 2026-09-17, latest stable): `androidx.lifecycle:lifecycle-process` **2.11.0**, `androidx.core:core` **1.19.0**, `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` **2.11.0** (reuses the existing `lifecycle` catalog version).
- Session scope stays `CoroutineScope(SupervisorJob() + <main-confined dispatcher>)` — both properties are load-bearing (see `AndroidPlatformModule.kt` KDoc). Anything that calls `RemoteController` from a coroutine launches in that scope or on the main thread.
- `:ui` and `:design` must NOT depend on `:data` or `:protocol`.
- Compose conventions: stateful/stateless split; `@Preview` functions named `Preview_*` wrapped in `DpadTheme`; icons `24.dp`.
- Codebase idiom over spec spelling: the spec writes `TvPower { On, Off }`; this plan uses `TvPower { ON, OFF }` to match `RemoteKey` / `PairingFailureReason`.
- Implementation refinement over the spec: `AutoDisconnectPolicy` takes no `scope`; it exposes `suspend fun run()`, which each platform entry point launches once in the `named("session")` scope (no launching from `init`, and tests drive it from `backgroundScope`).
- Interpretation of the spec's "request `POST_NOTIFICATIONS` once, the first time the remote screen reaches Connected": `MainActivity` asks on the first `Connected` per activity creation if not yet granted; Android itself suppresses the prompt after two denials, so no extra persisted flag.
- Timings (from the spec, exact): TV off → **2 minutes**; stuck `Connecting` → **2 minutes**; idle → **30 minutes**. All only while the app is in the background.

### Contracts after Task 2 (exact — later tasks rely on these)

```kotlin
// :protocol
class RemoteSession { val power: StateFlow<Boolean?> /* true = on, false = standby, null = unknown */ }
// :domain
enum class TvPower { ON, OFF }
interface RemoteController {
    val connection: StateFlow<ConnectionState>
    val volume: StateFlow<Volume?>
    val power: StateFlow<TvPower?>
    val interactions: SharedFlow<Unit>
    fun connect(device: PairedDevice); fun disconnect(); fun press(key: RemoteKey)
    fun launchApp(appLinkUrl: String); fun sendText(text: String)
}
// :data
fun Boolean?.toTvPower(): TvPower?
internal fun isAlreadyTargeting(current: PairedDevice?, requested: PairedDevice, state: ConnectionState): Boolean
class AutoDisconnectPolicy(controller: RemoteController, appInForeground: StateFlow<Boolean>, timings: Timings = Timings()) { suspend fun run() }
// Koin qualifier for the platform-provided foreground signal: named("appInForeground") → StateFlow<Boolean>
```

---

### Task 1: `RemoteSession` exposes TV power

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/session/RemoteSession.kt`
- Modify: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/fake/FakeTvServer.kt`
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/session/RemoteSessionTest.kt`

**Interfaces:**
- Produces: `RemoteSession.power: StateFlow<Boolean?>`; test-side `FakeTvServer.sendRemoteStart(started: Boolean)` and `FakeTvServer.remoteStartDuringHandshake: Boolean?`.

- [ ] **Step 1: Teach `FakeTvServer` to send `remote_start`**

Add the import `import remote.RemoteStart` alongside the other `remote.*` imports.

Add this property next to `keyInjects`:

```kotlin
    /**
     * When non-null, the session peer sends `remote_start(started = this)` between receiving the
     * client's `remote_configure` reply and sending `remote_set_active` — i.e. mid-handshake, where
     * the client's handshake loop has to cope with an unexpected message.
     */
    @Volatile var remoteStartDuringHandshake: Boolean? = null
```

In `startSessionServer()`, insert between `receivedMessages += configureReply` and the `writeMessage(pipe, RemoteMessage(remote_set_active = …))` line:

```kotlin
                remoteStartDuringHandshake?.let { started ->
                    writeMessage(pipe, RemoteMessage(remote_start = RemoteStart(started = started)))
                }
```

Add next to `sendVolume`:

```kotlin
    /** Writes a `remote_start(started)` (TV power report) on the current session connection. */
    suspend fun sendRemoteStart(started: Boolean) {
        val pipe = currentPipe ?: error("FakeTvServer.sendRemoteStart: no active session connection")
        writeMessage(pipe, RemoteMessage(remote_start = RemoteStart(started = started)))
    }
```

- [ ] **Step 2: Write the failing tests**

Append to `RemoteSessionTest` (before the closing brace of the class):

```kotlin
    @Test fun tracksPowerFromRemoteStart() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            server.sendRemoteStart(false)
            s.power.test { awaitItemUntil { it == false } }
            server.sendRemoteStart(true)
            s.power.test { awaitItemUntil { it == true } }
        }
    }

    @Test fun powerResetsToNullOnDropAndOnDisconnect() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            server.sendRemoteStart(true)
            s.power.test {
                awaitItemUntil { it == true }
                server.dropConnection()
                awaitItemUntil { it == null }
            }
            s.state.test { awaitItemUntil { it == SessionState.Connected } }   // fake auto-accepts the retry
            server.sendRemoteStart(false)
            s.power.test { awaitItemUntil { it == false } }
            s.disconnect()
            assertNull(s.power.value)
        }
    }

    // The TV may report power before remote_set_active; handshakeReplies used to discard every
    // message it wasn't waiting for, which would lose the initial report.
    @Test fun remoteStartDuringHandshakeIsCapturedAndHandshakeCompletes() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.remoteStartDuringHandshake = false
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            assertEquals(false, s.power.value)
        }
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :protocol:jvmTest --tests '*RemoteSessionTest*'`
Expected: compilation FAILS with `Unresolved reference 'power'`.

- [ ] **Step 4: Implement `power` in `RemoteSession`**

Below the `volume` declarations add:

```kotlin
    private val _power = MutableStateFlow<Boolean?>(null)
    /** TV power as last reported by `remote_start`: true = screen on, false = standby, null = unknown / not connected. */
    val power: StateFlow<Boolean?> = _power.asStateFlow()
```

In `disconnect()`, after `activeChannel = null`, add:

```kotlin
        _power.value = null
```

In `runLoop`'s `finally` block, replace

```kotlin
                if (isCurrentGeneration(myGeneration)) activeChannel = null
```

with

```kotlin
                if (isCurrentGeneration(myGeneration)) {
                    activeChannel = null
                    _power.value = null
                }
```

In `runLoop`, change the two calls to pass the generation:

```kotlin
                    handshakeReplies(connection, channel, myGeneration)
                    if (isCurrentGeneration(myGeneration)) _state.value = SessionState.Connected
                    readLoop(connection, channel, myGeneration)
```

Add this helper right after `isCurrentGeneration`:

```kotlin
    /** Records a `remote_start` power report, if [message] carries one, for the current generation only. */
    private fun recordPower(message: RemoteMessage, myGeneration: Int) {
        val started = message.remote_start?.started ?: return
        if (isCurrentGeneration(myGeneration)) _power.value = started
    }
```

Update `handshakeReplies` — new signature, plus one line in each wait loop right after the decode:

```kotlin
    private suspend fun handshakeReplies(connection: TlsConnection, channel: Channel<RemoteMessage>, myGeneration: Int) {
        while (true) {
            val msg = RemoteMessage.ADAPTER.decode(connection.readFrame())
            recordPower(msg, myGeneration)
            if (msg.remote_configure != null) {
```

```kotlin
        while (true) {
            val msg = RemoteMessage.ADAPTER.decode(connection.readFrame())
            recordPower(msg, myGeneration)
            if (msg.remote_set_active != null) {
```

Update `readLoop` — new signature, plus one line after the decode (volume handling unchanged):

```kotlin
    private suspend fun readLoop(connection: TlsConnection, channel: Channel<RemoteMessage>, myGeneration: Int) {
        while (true) {
            val msg = RemoteMessage.ADAPTER.decode(connection.readFrame())
            recordPower(msg, myGeneration)
            msg.remote_ping_request?.let { ping ->
```

Also update `readLoop`'s KDoc to: `/** Runs for the life of the connection: answers pings immediately, tracks volume and power, records nothing else. */`

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :protocol:jvmTest`
Expected: all PASS (new three + existing).

- [ ] **Step 6: Commit**

```bash
git add protocol/
git commit -m "[protocol] Expose TV power state from remote_start, including mid-handshake reports"
```

---

### Task 2: `RemoteController` gains `power` + `interactions`; idempotent `connect()`

**Files:**
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/TvPower.kt`
- Modify: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/RemoteController.kt`
- Modify: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/mapping/ProtocolMappers.kt`
- Modify: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/RemoteControllerImpl.kt`
- Modify (fakes only, to keep compiling): `ui/src/androidHostTest/kotlin/com/dgmltn/dpad/ui/remote/RemoteViewModelTest.kt`, `ui/src/androidHostTest/kotlin/com/dgmltn/dpad/ui/devices/DevicesViewModelTest.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/mapping/ProtocolMappersTest.kt`, `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/RemoteControllerImplTest.kt`

**Interfaces:**
- Consumes: `RemoteSession.power: StateFlow<Boolean?>` (Task 1).
- Produces: `TvPower`, `RemoteController.power`, `RemoteController.interactions`, `Boolean?.toTvPower()`, `isAlreadyTargeting(...)` — exact signatures in Global Constraints.

- [ ] **Step 1: Add the domain type and contract members**

Create `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/TvPower.kt`:

```kotlin
package com.dgmltn.dpad.domain

/** TV screen power as reported by the TV. APIs use `null` for "unknown" (not connected, or never reported). */
enum class TvPower { ON, OFF }
```

Replace the body of `RemoteController.kt` with:

```kotlin
package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface RemoteController {
    val connection: StateFlow<ConnectionState>
    val volume: StateFlow<Volume?>
    /** TV power as last reported by the TV; `null` when unknown or not connected. */
    val power: StateFlow<TvPower?>
    /** Emits once per user command ([press], [launchApp], [sendText]); drives idle auto-disconnect. */
    val interactions: SharedFlow<Unit>
    /**
     * Connect to [device] and keep reconnecting. A no-op while already targeting [device] (connecting
     * or connected, until [disconnect] or a re-pair is required); switches target if [device] differs.
     */
    fun connect(device: PairedDevice)
    fun disconnect()
    fun press(key: RemoteKey)
    fun launchApp(appLinkUrl: String)
    fun sendText(text: String)          // per-character key events for search fields
}
```

- [ ] **Step 2: Update the two `:ui` test fakes so they still compile**

In `RemoteViewModelTest.FakeController` (file already imports `kotlinx.coroutines.flow.*` and `com.dgmltn.dpad.domain.*`), add after `override val volume = _vol.asStateFlow()`:

```kotlin
        val _power = MutableStateFlow<TvPower?>(null)
        override val power = _power.asStateFlow()
        override val interactions = MutableSharedFlow<Unit>().asSharedFlow()
```

In `DevicesViewModelTest.FakeController`, add the same three lines after `override val volume = _vol.asStateFlow()`, and add imports `kotlinx.coroutines.flow.MutableSharedFlow` and `kotlinx.coroutines.flow.asSharedFlow`.

- [ ] **Step 3: Write the failing `:data` tests**

Append to `ProtocolMappersTest`:

```kotlin
    @Test fun powerMapsToTvPower() {
        assertEquals(TvPower.ON, true.toTvPower())
        assertEquals(TvPower.OFF, false.toTvPower())
        assertNull((null as Boolean?).toTvPower())
    }
```

In `RemoteControllerImplTest`, add imports:

```kotlin
import app.cash.turbine.test
import com.dgmltn.dpad.domain.RemoteKey
```

Extend `disconnectWithoutPriorConnectDoesNotThrow` with one more assertion at the end:

```kotlin
        assertNull(controller.power.value)
```

Append these tests to the class:

```kotlin
    @Test fun alreadyTargetingOnlyForSameDeviceNotAwaitingRepair() {
        val a = PairedDevice(id = "a", name = "A", host = "10.0.0.1", serviceName = "a")
        val b = a.copy(id = "b", serviceName = "b")
        assertFalse(isAlreadyTargeting(current = null, requested = a, state = ConnectionState.Disconnected))
        // connect() in flight but its session not built yet: state is still Disconnected.
        assertTrue(isAlreadyTargeting(current = a, requested = a, state = ConnectionState.Disconnected))
        assertTrue(isAlreadyTargeting(current = a, requested = a, state = ConnectionState.Connecting))
        assertTrue(isAlreadyTargeting(current = a, requested = a, state = ConnectionState.Connected))
        assertFalse(isAlreadyTargeting(current = a, requested = a, state = ConnectionState.PairingRequired))
        assertFalse(isAlreadyTargeting(current = a, requested = b, state = ConnectionState.Connected))
        // Same TV at a new stored address deserves a fresh session.
        assertFalse(isAlreadyTargeting(current = a, requested = a.copy(host = "10.0.0.9"), state = ConnectionState.Connected))
    }

    @Test fun everyCommandEmitsAnInteraction() = runTest {
        val controller = RemoteControllerImpl(identityStore, discovery, this)
        controller.interactions.test {
            controller.press(RemoteKey.HOME)
            awaitItem()
            controller.launchApp("https://example.com")
            awaitItem()
            controller.sendText("a")
            awaitItem()
        }
    }
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :data:jvmTest --tests '*ProtocolMappersTest*' --tests '*RemoteControllerImplTest*'`
Expected: compilation FAILS (`toTvPower`, `isAlreadyTargeting`, `power`, `interactions` unresolved / `RemoteControllerImpl` not implementing abstract members).

- [ ] **Step 5: Implement the mapper**

Append to `ProtocolMappers.kt`:

```kotlin
fun Boolean?.toTvPower(): TvPower? = when (this) {
    true -> TvPower.ON
    false -> TvPower.OFF
    null -> null
}
```

- [ ] **Step 6: Implement `RemoteControllerImpl` changes**

Add imports:

```kotlin
import com.dgmltn.dpad.data.mapping.toTvPower
import kotlinx.coroutines.channels.BufferOverflow
```

(`kotlinx.coroutines.flow.*` is already imported.)

Below the `volume` declarations add:

```kotlin
    private val _power = MutableStateFlow<TvPower?>(null)
    override val power: StateFlow<TvPower?> = _power.asStateFlow()

    // DROP_OLDEST + 1 slot: tryEmit never fails and never suspends a UI-thread caller; a burst
    // collapsing to one emission is fine for "was there activity".
    private val _interactions = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val interactions: SharedFlow<Unit> = _interactions.asSharedFlow()

    // The device the current connect() targets; cleared only by disconnect(). Keyed on this rather
    // than on _connection because the state is still Disconnected while connect()'s launch is
    // suspended before building its session — a repeat connect() in that window must be a no-op too.
    private var currentDevice: PairedDevice? = null
```

Change the start of `connect()` from

```kotlin
    override fun connect(device: PairedDevice) {
        disconnect()
        connectJob = scope.launch {
```

to

```kotlin
    override fun connect(device: PairedDevice) {
        if (isAlreadyTargeting(currentDevice, device, _connection.value)) return
        disconnect()
        currentDevice = device
        connectJob = scope.launch {
```

Extend `collectorJobs` inside `connect()`:

```kotlin
            collectorJobs = listOf(
                scope.launch { s.state.collect { _connection.value = it.toDomain() } },
                scope.launch { s.volume.collect { _volume.value = it?.toDomain() } },
                scope.launch { s.power.collect { _power.value = it.toTvPower() } },
            )
```

At the end of `disconnect()` (after `_volume.value = null`) add:

```kotlin
        _power.value = null
        currentDevice = null
```

Replace the three command functions with:

```kotlin
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
```

Append at file level (after the class):

```kotlin
/**
 * True when [connect][RemoteControllerImpl.connect] for [requested] should be a no-op: it's the
 * exact device already being targeted and the session hasn't given up for a re-pair.
 */
internal fun isAlreadyTargeting(current: PairedDevice?, requested: PairedDevice, state: ConnectionState): Boolean =
    current == requested && state != ConnectionState.PairingRequired
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :domain:jvmTest :data:jvmTest :ui:testAndroidHostTest`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add domain/ data/ ui/src/androidHostTest/
git commit -m "[data] RemoteController exposes TV power and interactions; connect() is idempotent per device"
```

---

### Task 3: `AutoDisconnectPolicy`

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/AutoDisconnectPolicy.kt`
- Modify: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/di/DataModule.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/AutoDisconnectPolicyTest.kt`, `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/di/DataModuleTest.kt`

**Interfaces:**
- Consumes: `RemoteController.connection`, `.power`, `.interactions`, `.disconnect()` (Task 2).
- Produces: `class AutoDisconnectPolicy(controller: RemoteController, appInForeground: StateFlow<Boolean>, timings: Timings = Timings())` with `suspend fun run()`; Koin `single<AutoDisconnectPolicy>` that requires `single<StateFlow<Boolean>>(named("appInForeground"))` from the platform module.

- [ ] **Step 1: Write the failing policy tests**

Create `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/AutoDisconnectPolicyTest.kt`:

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDisconnectPolicyTest {
    private class FakeController : RemoteController {
        val conn = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val pwr = MutableStateFlow<TvPower?>(TvPower.ON)
        private val _interactions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val connection = conn.asStateFlow()
        override val volume = MutableStateFlow<Volume?>(null).asStateFlow()
        override val power = pwr.asStateFlow()
        override val interactions = _interactions.asSharedFlow()
        var disconnects = 0
        override fun connect(device: PairedDevice) {}
        override fun disconnect() {
            disconnects++
            conn.value = ConnectionState.Disconnected
            pwr.value = null
        }
        override fun press(key: RemoteKey) { _interactions.tryEmit(Unit) }
        override fun launchApp(appLinkUrl: String) {}
        override fun sendText(text: String) {}
    }

    private val controller = FakeController()
    private val foreground = MutableStateFlow(false)

    private fun TestScope.startPolicy() {
        backgroundScope.launch { AutoDisconnectPolicy(controller, foreground).run() }
        runCurrent()
    }

    @Test fun tvOffInBackgroundDisconnectsAfterTwoMinutes() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun tvBackOnBeforeDeadlineCancelsTvOffTimer() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        controller.pwr.value = TvPower.ON
        runCurrent()
        advanceTimeBy(2.seconds)
        assertEquals(0, controller.disconnects)
    }

    @Test fun stuckReconnectingInBackgroundDisconnectsAfterTwoMinutes() = runTest {
        controller.conn.value = ConnectionState.Connecting
        controller.pwr.value = null
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun idleInBackgroundDisconnectsAfterThirtyMinutes() = runTest {
        startPolicy()
        advanceTimeBy(30.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun interactionRestartsIdleTimer() = runTest {
        startPolicy()
        advanceTimeBy(29.minutes)
        controller.press(RemoteKey.MEDIA_PLAY_PAUSE)
        runCurrent()
        advanceTimeBy(29.minutes)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.minutes)
        assertEquals(1, controller.disconnects)
    }

    @Test fun foregroundingCancelsPendingTimerAndBackgroundingRestartsIt() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        foreground.value = true
        runCurrent()
        advanceTimeBy(10.minutes)
        assertEquals(0, controller.disconnects)

        foreground.value = false
        runCurrent()
        advanceTimeBy(2.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun neverDisconnectsWhileInForeground() = runTest {
        foreground.value = true
        controller.conn.value = ConnectionState.Connecting
        startPolicy()
        advanceTimeBy(1.hours)
        controller.conn.value = ConnectionState.Connected
        controller.pwr.value = TvPower.OFF
        runCurrent()
        advanceTimeBy(1.hours)
        assertEquals(0, controller.disconnects)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :data:jvmTest --tests '*AutoDisconnectPolicyTest*'`
Expected: compilation FAILS with `Unresolved reference 'AutoDisconnectPolicy'`.

- [ ] **Step 3: Implement the policy**

Create `data/src/commonMain/kotlin/com/dgmltn/dpad/data/AutoDisconnectPolicy.kt`:

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.domain.TvPower
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Ends a forgotten background connection. While the app is in the background it disconnects when
 * the TV has reported standby for [Timings.tvOff], the session has been stuck reconnecting for
 * [Timings.unreachable], or no command has been sent for [Timings.idle]. While the app is in the
 * foreground it never disconnects — the user may be about to press Power. Every timer starts
 * fresh when the app is backgrounded.
 */
class AutoDisconnectPolicy(
    private val controller: RemoteController,
    private val appInForeground: StateFlow<Boolean>,
    private val timings: Timings = Timings(),
) {
    data class Timings(
        val tvOff: Duration = 2.minutes,
        val unreachable: Duration = 2.minutes,
        val idle: Duration = 30.minutes,
    )

    private enum class Rule { NONE, TV_OFF, UNREACHABLE, IDLE }

    /** Runs until cancelled. Launch once, in the session scope. */
    suspend fun run() {
        appInForeground.collectLatest { foreground ->
            if (foreground) return@collectLatest
            combine(controller.connection, controller.power, ::ruleFor)
                .distinctUntilChanged()
                .collectLatest { rule ->
                    when (rule) {
                        Rule.NONE -> Unit
                        Rule.TV_OFF -> disconnectAfter(timings.tvOff)
                        Rule.UNREACHABLE -> disconnectAfter(timings.unreachable)
                        Rule.IDLE -> controller.interactions
                            .onStart { emit(Unit) }
                            .collectLatest { disconnectAfter(timings.idle) }
                    }
                }
        }
    }

    private suspend fun disconnectAfter(duration: Duration) {
        delay(duration)
        controller.disconnect()
    }

    private fun ruleFor(connection: ConnectionState, power: TvPower?): Rule = when (connection) {
        ConnectionState.Connected -> if (power == TvPower.OFF) Rule.TV_OFF else Rule.IDLE
        ConnectionState.Connecting -> Rule.UNREACHABLE
        ConnectionState.Disconnected, ConnectionState.PairingRequired -> Rule.NONE
    }
}
```

- [ ] **Step 4: Run policy tests to verify they pass**

Run: `./gradlew :data:jvmTest --tests '*AutoDisconnectPolicyTest*'`
Expected: 7 PASS.

- [ ] **Step 5: Write the failing DI test**

In `DataModuleTest.everyDomainContractResolves`, add imports `com.dgmltn.dpad.data.AutoDisconnectPolicy`, `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`; add to the `platform` module:

```kotlin
            single<StateFlow<Boolean>>(named("appInForeground")) { MutableStateFlow(true) }
```

and to the assertions:

```kotlin
        assertNotNull(koin.get<AutoDisconnectPolicy>())
```

Run: `./gradlew :data:jvmTest --tests '*DataModuleTest*'`
Expected: FAIL — `NoDefinitionFoundException` for `AutoDisconnectPolicy`.

- [ ] **Step 6: Bind the policy**

In `DataModule.kt`, add the binding after the `RemoteController` line:

```kotlin
    single { AutoDisconnectPolicy(get(), get(named("appInForeground"))) }
```

and add one line to the KDoc's list of required platform singletons:

```kotlin
 *   single<StateFlow<Boolean>>(named("appInForeground")) { ... true while the app is visible ... }
```

- [ ] **Step 7: Run all `:data` tests**

Run: `./gradlew :data:jvmTest`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add data/
git commit -m "[data] Add AutoDisconnectPolicy: end background connections on TV off, unreachable, or idle"
```

---

### Task 4: Remote screen shows TV power and reconnects on start

**Files:**
- Modify: `gradle/libs.versions.toml`, `ui/build.gradle.kts`
- Modify: `design/src/commonMain/kotlin/com/dgmltn/dpad/design/RemoteButtons.kt`
- Modify: `ui/src/commonMain/kotlin/com/dgmltn/dpad/ui/remote/RemoteUiState.kt`, `RemoteViewModel.kt`, `RemoteScreen.kt`
- Test: `ui/src/androidHostTest/kotlin/com/dgmltn/dpad/ui/remote/RemoteViewModelTest.kt`

**Interfaces:**
- Consumes: `RemoteController.power` (Task 2); `connect()` idempotency (Task 2) makes repeated `onConnectLastUsed()` safe.
- Produces: `RemoteUiState.power: TvPower?`; `RemoteIconButton(..., containerColor: Color = MaterialTheme.colorScheme.surfaceVariant)`.

- [ ] **Step 1: Write the failing ViewModel tests**

In `RemoteViewModelTest`, **delete** `constructionAloneTriggersConnectToLastUsedDevice` (connecting moves from `init` to the screen's `ON_START`) and add:

```kotlin
    @Test fun powerReachesUiState() = runTest {
        val controller = FakeController()
        val vm = RemoteViewModel(controller, FakeDeviceRepo(emptyList(), null), FakeShortcutRepo(emptyList()))
        vm.state.test {
            controller._conn.value = ConnectionState.Connected
            controller._power.value = TvPower.OFF
            val s = awaitItemUntil { it.power == TvPower.OFF }
            assertEquals(ConnectionState.Connected, s.connection)
        }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:testAndroidHostTest`
Expected: compilation FAILS with `Unresolved reference 'power'` on `RemoteUiState`.

- [ ] **Step 3: Add `power` to state and ViewModel; drop `init`**

`RemoteUiState.kt` — add import `com.dgmltn.dpad.domain.TvPower` and the field after `connection`:

```kotlin
    val power: TvPower? = null,
```

`RemoteViewModel.kt` — replace the `state` declaration and delete the `init { onConnectLastUsed() }` block:

```kotlin
    val state: StateFlow<RemoteUiState> = combine(
        combine(controller.connection, controller.power, controller.volume, ::Triple),
        deviceRepository.devices,
        deviceRepository.lastUsedDeviceId,
        shortcutRepository.shortcuts,
    ) { (connection, power, volume), devices, lastUsedDeviceId, shortcuts ->
        RemoteUiState(
            deviceName = devices.firstOrNull { it.id == lastUsedDeviceId }?.name,
            connection = connection,
            power = power,
            volume = volume,
            shortcuts = shortcuts.toImmutableList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteUiState())
```

Add KDoc on `onConnectLastUsed`:

```kotlin
    /**
     * Called by [RemoteScreen] on every ON_START. Safe to repeat: [RemoteController.connect] is a
     * no-op for the device it's already targeting.
     */
```

- [ ] **Step 4: Run to verify tests pass**

Run: `./gradlew :ui:testAndroidHostTest`
Expected: all PASS.

- [ ] **Step 5: Add `lifecycle-runtime-compose`**

`gradle/libs.versions.toml` `[libraries]`, after `androidx-lifecycle-viewmodel-compose`:

```toml
androidx-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
```

`ui/build.gradle.kts` `commonMain.dependencies`, after `libs.androidx.lifecycle.viewmodel.compose`:

```kotlin
            implementation(libs.androidx.lifecycle.runtime.compose)
```

- [ ] **Step 6: Give `RemoteIconButton` a container color**

In `RemoteButtons.kt`, add a parameter after `tint` and use it:

```kotlin
    tint: Color = Color.Unspecified,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    repeat: Boolean = false,
```

```kotlin
        color = containerColor,
```

- [ ] **Step 7: Update `RemoteScreen.kt`**

Imports to add: `androidx.lifecycle.compose.LifecycleStartEffect`, `com.dgmltn.dpad.domain.TvPower`.

In `RemoteScreen(...)`, before `RemoteContent(...)`:

```kotlin
    LifecycleStartEffect(vm) {
        vm.onConnectLastUsed()
        onStopOrDispose { }
    }
```

In `RemoteContent`, replace the `TopBar(...)` call and the `if (state.connection != ConnectionState.Connected) { ConnectionBanner(...) }` block with:

```kotlin
            val tvOff = state.connection == ConnectionState.Connected && state.power == TvPower.OFF

            TopBar(
                deviceName = state.deviceName,
                connection = state.connection,
                tvOff = tvOff,
                onDeviceNameClick = onOpenDevices,
                onKeyboardClick = { showTextSheet = true },
                onPowerClick = { onKey(RemoteKey.POWER) },
            )

            statusBannerText(state.connection, tvOff)?.let { StatusBanner(text = it) }
```

`TopBar`: add parameter `tvOff: Boolean,` after `connection`; change the dot to `connectionDotColor(connection, tvOff)`; replace the power button with:

```kotlin
            RemoteIconButton(
                icon = Icons.Filled.PowerSettingsNew,
                contentDescription = if (tvOff) "Turn TV on" else "Power",
                onClick = onPowerClick,
                tint = if (tvOff) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error,
                containerColor = if (tvOff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
```

Replace `connectionDotColor` and `ConnectionBanner` with:

```kotlin
@Composable
private fun connectionDotColor(connection: ConnectionState, tvOff: Boolean): Color = when {
    tvOff -> MaterialTheme.colorScheme.outline
    connection == ConnectionState.Connected -> Color(0xFF4CAF50)
    connection == ConnectionState.Connecting -> Color(0xFFFFC107)
    connection == ConnectionState.PairingRequired -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusBannerText(connection: ConnectionState, tvOff: Boolean): String? = when (connection) {
    ConnectionState.Connecting -> "Connecting…"
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.PairingRequired -> "Re-pair needed"
    ConnectionState.Connected -> if (tvOff) "TV is off" else null
}

@Composable
private fun StatusBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
```

Add a preview after `Preview_RemoteContent_Connected`:

```kotlin
@Preview
@Composable
private fun Preview_RemoteContent_TvOff() {
    DpadTheme {
        RemoteContent(
            state = RemoteUiState(
                deviceName = "Living Room TV",
                connection = ConnectionState.Connected,
                power = TvPower.OFF,
                volume = null,
                shortcuts = persistentListOf(),
            ),
            onKey = {},
            onLaunch = {},
            onText = {},
            onOpenDevices = {},
            onEditShortcuts = {},
        )
    }
}
```

- [ ] **Step 8: Verify compile on all targets + tests**

Run: `./gradlew :design:compileAndroidMain :design:compileKotlinIosSimulatorArm64 :ui:compileAndroidMain :ui:compileKotlinIosSimulatorArm64 :ui:testAndroidHostTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml design/ ui/
git commit -m "[ui] Show TV-off state on the remote screen; reconnect on every screen start"
```

---

### Task 5: Platform foreground signal + start the policy (Android + iOS)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app-android/build.gradle.kts`
- Modify: `app-android/src/main/kotlin/com/dgmltn/dpad/android/di/AndroidPlatformModule.kt`, `app-android/src/main/kotlin/com/dgmltn/dpad/android/DpadApplication.kt`
- Modify: `app-ios-shared/src/iosMain/kotlin/com/dgmltn/dpad/iosshared/IosPlatformModule.kt`, `app-ios-shared/src/iosMain/kotlin/com/dgmltn/dpad/iosshared/DpadShared.kt`

**Interfaces:**
- Consumes: `AutoDisconnectPolicy` Koin binding requiring `named("appInForeground")` (Task 3).
- Produces: `single<StateFlow<Boolean>>(named("appInForeground"))` on both platforms (Task 6 reuses it on Android).

No automated tests: this is platform wiring. The gate is that both apps build and the Koin graph resolves at startup (the policy is resolved eagerly, so a missing binding crashes on launch — Step 6 checks that).

- [ ] **Step 1: Add `lifecycle-process`**

`gradle/libs.versions.toml` `[versions]`:

```toml
lifecycle-google = "2.11.0"   # androidx.lifecycle (Google), for lifecycle-process
```

`[libraries]`:

```toml
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle-google" }
```

`app-android/build.gradle.kts` `dependencies`, after `libs.androidx.lifecycle.viewmodel.compose`:

```kotlin
    implementation(libs.androidx.lifecycle.process)
```

- [ ] **Step 2: Android `appInForeground`**

In `AndroidPlatformModule.kt` add imports:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
```

Add to the module (after the session scope):

```kotlin
    // True while any activity is started. ProcessLifecycleOwner delays ON_STOP ~700ms so rotation
    // doesn't flicker it.
    single<StateFlow<Boolean>>(named("appInForeground")) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .stateIn(get(named("session")), SharingStarted.Eagerly, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
```

Update the module KDoc's first sentence to say it provides "the four platform singletons" and mention the foreground signal.

- [ ] **Step 3: Start the policy on Android**

Replace `DpadApplication.kt` with:

```kotlin
package com.dgmltn.dpad.android

import android.app.Application
import com.dgmltn.dpad.android.di.androidPlatformModule
import com.dgmltn.dpad.android.di.uiModule
import com.dgmltn.dpad.data.AutoDisconnectPolicy
import com.dgmltn.dpad.data.di.dataModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class DpadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DpadApplication)
            modules(androidPlatformModule, dataModule, uiModule)
        }
        val sessionScope: CoroutineScope = get(named("session"))
        val policy: AutoDisconnectPolicy = get()
        sessionScope.launch { policy.run() }
    }
}
```

- [ ] **Step 4: iOS `appInForeground`**

In `IosPlatformModule.kt` add imports:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
```

Add to the module:

```kotlin
    // Starts true: Koin starts from DpadApp.init while the app is launching into the foreground.
    // Observers live as long as the process (this is a singleton), so they're never removed.
    single<StateFlow<Boolean>>(named("appInForeground")) {
        val state = MutableStateFlow(true)
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            state.value = true
        }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            state.value = false
        }
        state.asStateFlow()
    }
```

- [ ] **Step 5: Start the policy on iOS**

In `DpadShared.kt`, add imports `com.dgmltn.dpad.data.AutoDisconnectPolicy`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.launch`, `org.koin.core.qualifier.named`, and replace `startDpadKoin()` with:

```kotlin
fun startDpadKoin() {
    val koin = startKoin {
        modules(iosPlatformModule, dataModule, uiModule)
    }.koin
    val sessionScope: CoroutineScope = koin.get(named("session"))
    val policy: AutoDisconnectPolicy = koin.get()
    sessionScope.launch { policy.run() }
}
```

- [ ] **Step 6: Build both apps**

Run: `./gradlew :app-android:assembleDebug :app-ios-shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

If an emulator or device is attached (`adb devices`), also run
`adb install -r app-android/build/outputs/apk/debug/app-android-debug.apk && adb shell am start -n com.dgmltn.dpad/com.dgmltn.dpad.android.MainActivity`
and confirm with `adb logcat -d | grep -i "NoDefinitionFound\|FATAL"` that nothing crashed at startup.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app-android/ app-ios-shared/
git commit -m "[app] Provide app-in-foreground signal on Android and iOS; run AutoDisconnectPolicy"
```

---

### Task 6: Android `ConnectionService`, notification, starter, permissions

**Files:**
- Modify: `gradle/libs.versions.toml`, `app-android/build.gradle.kts`
- Modify: `app-android/src/main/AndroidManifest.xml`
- Create: `app-android/src/main/res/drawable/ic_notification.xml`
- Create: `app-android/src/main/kotlin/com/dgmltn/dpad/android/connection/ConnectionNotification.kt`
- Create: `app-android/src/main/kotlin/com/dgmltn/dpad/android/connection/ConnectionService.kt`
- Create: `app-android/src/main/kotlin/com/dgmltn/dpad/android/connection/ConnectionServiceStarter.kt`
- Modify: `app-android/src/main/kotlin/com/dgmltn/dpad/android/DpadApplication.kt`, `app-android/src/main/kotlin/com/dgmltn/dpad/android/MainActivity.kt`

**Interfaces:**
- Consumes: `RemoteController` (Task 2), `DeviceRepository`, `named("appInForeground")` (Task 5), `named("session")` scope.
- Produces: nothing other tasks consume.

No automated tests (spec: thin wiring, device-verified). Keep each file to its one job.

- [ ] **Step 1: Add `androidx.core`**

`gradle/libs.versions.toml` `[versions]`: `androidx-core = "1.19.0"`; `[libraries]`:

```toml
androidx-core = { module = "androidx.core:core", version.ref = "androidx-core" }
```

`app-android/build.gradle.kts` `dependencies`: `implementation(libs.androidx.core)`.

- [ ] **Step 2: Manifest**

Add after the existing permissions:

```xml
    <!-- Keep the TV session alive while the screen is off (ConnectionService). connectedDevice
         also requires one of a set of permissions; CHANGE_WIFI_MULTICAST_STATE above qualifies. -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Inside `<application>`, after the `<activity>`:

```xml
        <service
            android:name="com.dgmltn.dpad.android.connection.ConnectionService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
```

- [ ] **Step 3: Notification icon**

Create `app-android/src/main/res/drawable/ic_notification.xml` (a d-pad cross):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M9,2h6v7h7v6h-7v7h-6v-7h-7v-6h7z" />
</vector>
```

- [ ] **Step 4: `ConnectionNotification.kt`**

```kotlin
package com.dgmltn.dpad.android.connection

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dgmltn.dpad.android.MainActivity
import com.dgmltn.dpad.R   // app namespace is dpad.appId = com.dgmltn.dpad
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteKey
import com.dgmltn.dpad.domain.TvPower

internal const val CONNECTION_NOTIFICATION_ID = 1
private const val CHANNEL_ID = "connection"
private const val REQUEST_OPEN_APP = 1
private const val REQUEST_DISCONNECT = 2
private const val REQUEST_KEY_BASE = 100

internal fun createConnectionChannel(context: Context) {
    val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName("Remote connection")
        .setDescription("Keeps the remote connected to your TV while the screen is off")
        .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}

/**
 * Ongoing notification for [ConnectionService]. Actions follow TV state (standard notifications
 * show at most three): TV on/unknown → Play/Pause, Mute, Disconnect; TV off → Power, Disconnect.
 * Power is deliberately absent while the TV is on so the shade can't switch it off by accident.
 */
internal fun buildConnectionNotification(
    context: Context,
    deviceName: String?,
    connection: ConnectionState,
    power: TvPower?,
): Notification {
    val tvOff = connection == ConnectionState.Connected && power == TvPower.OFF
    val status = when {
        tvOff -> "TV is off"
        connection == ConnectionState.Connected -> "Connected"
        else -> "Reconnecting…"
    }
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(deviceName ?: "Dpad")
        .setContentText(status)
        .setContentIntent(openAppIntent(context))
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    if (tvOff) {
        builder.addAction(keyAction(context, RemoteKey.POWER, "Power"))
    } else {
        builder.addAction(keyAction(context, RemoteKey.MEDIA_PLAY_PAUSE, "Play/Pause"))
        builder.addAction(keyAction(context, RemoteKey.MUTE, "Mute"))
    }
    builder.addAction(serviceAction(context, ConnectionService.disconnectIntent(context), "Disconnect", REQUEST_DISCONNECT))
    return builder.build()
}

private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    REQUEST_OPEN_APP,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun keyAction(context: Context, key: RemoteKey, title: String): NotificationCompat.Action =
    serviceAction(context, ConnectionService.keyIntent(context, key), title, REQUEST_KEY_BASE + key.ordinal)

// getForegroundService: a notification action may (re)start the service even if it was just
// stopped; ConnectionService calls startForeground first thing in onStartCommand.
private fun serviceAction(context: Context, intent: Intent, title: String, requestCode: Int): NotificationCompat.Action {
    val pending = PendingIntent.getForegroundService(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Action.Builder(R.drawable.ic_notification, title, pending).build()
}
```

- [ ] **Step 5: `ConnectionService.kt`**

```kotlin
package com.dgmltn.dpad.android.connection

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.domain.RemoteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the process in the foreground-service state while the remote is connected, so Android
 * doesn't cache/freeze it on screen-off and the TV's keepalive pings keep getting answered.
 *
 * It does NOT own the connection: [RemoteController] does. This service mirrors
 * [RemoteController.connection] — it stops itself on Disconnected or PairingRequired — and renders
 * the ongoing notification. Started by [ConnectionServiceStarter] and by notification actions.
 */
class ConnectionService : Service() {
    private val controller: RemoteController by inject()
    private val deviceRepository: DeviceRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var lastNotification: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First, always: startForegroundService() only allows a few seconds before this call.
        postForeground(
            lastNotification
                ?: buildConnectionNotification(this, deviceName = null, controller.connection.value, controller.power.value),
        )
        when (intent?.action) {
            ACTION_KEY -> intent.getStringExtra(EXTRA_KEY)
                ?.let { name -> RemoteKey.entries.firstOrNull { it.name == name } }
                ?.let(controller::press)
            ACTION_DISCONNECT -> controller.disconnect()
        }
        if (!observing) {
            observing = true
            observe()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun observe() {
        val deviceName = combine(deviceRepository.devices, deviceRepository.lastUsedDeviceId) { devices, id ->
            devices.firstOrNull { it.id == id }?.name
        }
        scope.launch {
            combine(controller.connection, controller.power, deviceName, ::Triple).collect { (connection, power, name) ->
                if (connection == ConnectionState.Disconnected || connection == ConnectionState.PairingRequired) {
                    stopSelf()
                } else {
                    postForeground(buildConnectionNotification(this@ConnectionService, name, connection, power))
                }
            }
        }
    }

    // Re-calling startForeground is how a foreground service updates its own notification; it
    // needs no POST_NOTIFICATIONS check (if denied, the notification is simply hidden).
    private fun postForeground(notification: Notification) {
        lastNotification = notification
        ServiceCompat.startForeground(
            this,
            CONNECTION_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    companion object {
        private const val ACTION_KEY = "com.dgmltn.dpad.action.KEY"
        private const val ACTION_DISCONNECT = "com.dgmltn.dpad.action.DISCONNECT"
        private const val EXTRA_KEY = "key"

        fun startIntent(context: Context): Intent = Intent(context, ConnectionService::class.java)

        fun keyIntent(context: Context, key: RemoteKey): Intent =
            startIntent(context).setAction(ACTION_KEY).putExtra(EXTRA_KEY, key.name)

        fun disconnectIntent(context: Context): Intent =
            startIntent(context).setAction(ACTION_DISCONNECT)
    }
}
```

- [ ] **Step 6: `ConnectionServiceStarter.kt`**

```kotlin
package com.dgmltn.dpad.android.connection

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val TAG = "ConnectionService"

/**
 * Starts [ConnectionService] whenever the controller is connecting/connected while the app is in
 * the foreground. Starting only from the foreground satisfies Android 12+'s background-start ban;
 * background reconnects happen while the service is already running. Re-starting an already
 * running service is harmless (it just re-posts its notification).
 */
class ConnectionServiceStarter(
    private val context: Context,
    private val controller: RemoteController,
    private val appInForeground: StateFlow<Boolean>,
) {
    /** Runs until cancelled. Launch once, in the session scope. */
    suspend fun run() {
        combine(controller.connection, appInForeground) { connection, foreground ->
            foreground && (connection == ConnectionState.Connecting || connection == ConnectionState.Connected)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { start() }
    }

    private fun start() {
        try {
            ContextCompat.startForegroundService(context, ConnectionService.startIntent(context))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException, as
            // are older background-start refusals. Carry on without keep-alive (today's behavior).
            Log.w(TAG, "Couldn't start ConnectionService; continuing without keep-alive", e)
        }
    }
}
```

- [ ] **Step 7: Wire into `DpadApplication`**

Add imports `com.dgmltn.dpad.android.connection.ConnectionServiceStarter`, `com.dgmltn.dpad.android.connection.createConnectionChannel`, then append to `onCreate()` (after `sessionScope.launch { policy.run() }`):

```kotlin
        createConnectionChannel(this)
        val starter = ConnectionServiceStarter(this, get(), get(named("appInForeground")))
        sessionScope.launch { starter.run() }
```

- [ ] **Step 8: Ask for notification permission on first connect**

Replace `MainActivity.kt` with:

```kotlin
package com.dgmltn.dpad.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dgmltn.dpad.design.DpadTheme
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.ui.nav.AppNavHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val controller: RemoteController by inject()

    // Result ignored: if denied, ConnectionService still runs with its notification hidden, and
    // AutoDisconnectPolicy still bounds its lifetime.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DpadTheme {
                AppNavHost()
            }
        }
        askForNotificationsOnFirstConnect()
    }

    // Once per activity creation, at the moment the notification becomes relevant. Android itself
    // stops showing the prompt after the user denies it twice.
    private fun askForNotificationsOnFirstConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            controller.connection.first { it == ConnectionState.Connected }
            val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

- [ ] **Step 9: Build + lint**

Run: `./gradlew :app-android:assembleDebug :app-android:lintDebug`
Expected: `assembleDebug` succeeds. For lint, the requirement is no errors in files this task touched (`connection/`, `MainActivity.kt`, `DpadApplication.kt`, the manifest) — fix any `MissingPermission` / `ForegroundServiceType` / `NotificationPermission` findings there. If `lintDebug` fails only on findings in untouched files, note them in the task report rather than fixing them here. `InlinedApi` warnings on `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` are expected (`ServiceCompat` ignores the type below API 29).

If an emulator/device is attached, install and launch (as in Task 5 Step 6) and confirm no startup crash.

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml app-android/
git commit -m "[android] Foreground ConnectionService with state-aware notification controls and Disconnect"
```

---

### Task 7: Device-verification checklist + full gate

**Files:**
- Modify: `docs/DEVICE_VERIFICATION.md`

- [ ] **Step 1: Add checklist items**

In `docs/DEVICE_VERIFICATION.md`, insert after item 9 (before `## Notes`):

```markdown
- [ ] **10. Screen-off keep-alive (Android).** Connected, turn the phone screen off for 5+
  minutes, turn it on → the remote works immediately with no "Connecting…" banner. Before
  this change, `adb logcat -s Session` showed `session connection lost` at screen-on; it
  should no longer appear.
- [ ] **11. Notification controls (Android).** From the shade and from the lock screen:
  Play/Pause and Mute reach the TV; Disconnect ends the session, removes the notification,
  and reopening the app reconnects.
- [ ] **12. TV power state.** Turn the TV off with its own remote → the app shows "TV is off"
  with a highlighted Power button, and the notification switches to Power + Disconnect.
  Power turns it back on and the UI returns to normal.
- [ ] **13. Auto-disconnect (Android).** With the app in the background: TV off → service
  stops ~2 min later; TV unplugged → stops ~2 min later; TV on and untouched → stops after
  ~30 min. With the app visible, none of these disconnect.
- [ ] **14. `remote_start` survey.** For each TV/streamer, record whether it sends
  `remote_start` on connect, on power change, and whether it keeps networking in standby.
  If a device never sends it, the UI must look exactly as before (no TV-off state).
- [ ] **15. Pings with screen off.** If item 10 fails despite the service running, that's
  the signal to add a `WifiLock` held only while the TV is on (deliberately left out).
```

- [ ] **Step 2: Full gate**

Run: `./gradlew :protocol:jvmTest :domain:jvmTest :data:jvmTest :ui:testAndroidHostTest :app-android:assembleDebug :app-ios-shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add docs/DEVICE_VERIFICATION.md
git commit -m "[docs] Add keep-alive, notification, and TV power items to device verification"
```

- [ ] **Step 4: Stop for Doug's on-device pass**

Report the branch as ready for device verification (items 10–15). Do **not** squash-merge or push. After Doug confirms, squash locally:

```bash
git switch main
git merge --squash feat/connection-keepalive
git commit -m "[android] Keep the TV connection alive across screen-off; track TV power state"
git branch -D feat/connection-keepalive
```

Then stop and ask before any push.
