# Dpad Plan 2 of 3: Domain + Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `:domain` (pure-Kotlin models + repository/service contracts) and `:data` (DataStore persistence, protocol adapters, Koin wiring) so Plan 3's UI has a fully testable, protocol-agnostic backend for pairing, remote control, shortcuts, and multi-device management.

**Architecture:** `:domain` is pure Kotlin — app-wide models and interfaces, no dependency on `:protocol`. `:data` depends on both `:domain` and `:protocol`, implements the domain contracts, persists via DataStore Preferences (JSON-in-Preferences), and owns ALL mapping between domain types and the Wire/`:protocol` types. Everything with real logic (repositories, identity store, pure mappers) is JVM-tested against temp-file DataStore; the thin adapters that call final `:protocol` classes (`PairingClient`, `RemoteSession`, `MdnsBrowser`) are compile-verified, with their pure sub-logic extracted and tested — full protocol wiring is device-verified in Plan 3.

**Tech Stack:** Kotlin Multiplatform (jvm + android + iosArm64 + iosSimulatorArm64), DataStore Preferences (KMP), kotlinx-serialization-json, Koin (DI), Kermit (logging), kotlin.uuid.Uuid, kotlin-test + coroutines-test + Turbine + koin-test.

**Spec:** `docs/superpowers/specs/2026-08-11-android-tv-remote-design.md`. **Builds on:** the merged `:protocol` module (Plan 1). **Followed by:** Plan 3 (`:design`, feature modules, `app-android`/`app-ios`, on-device verification).

## Global Constraints

- Branch: `feat/domain-data` off `main`. WIP commits per task, subject format `[domain] ...` or `[data] ...` (bracketed tag first, matching the module). **No `Co-Authored-By` trailer, no "Generated with Claude Code" footer, ever.** Squash-merge to `main` only at plan completion; never push.
- New dependency versions pinned to **latest stable** in `gradle/libs.versions.toml` via a live-metadata run (extend the existing `scripts/latest-versions.sh`) — never from memory, no alpha/beta/rc.
- Project config (JDK 21, minSdk 26, compileSdk 36) already lives in `gradle.properties`; read via `providers.gradleProperty(...)`. Do not add project config to the version catalog.
- Android targets use the **`com.android.kotlin.multiplatform.library`** plugin (catalog alias `androidKotlinMultiplatformLibrary`), matching `:protocol`. The Android compile task is `compileAndroidMain`, NOT `compileDebugKotlinAndroid`.
- Every non-UI KMP module has a `jvm()` target so tests run on the JVM. Tests live in `jvmTest`, run via `./gradlew :<module>:jvmTest`. Real collaborators over mocks: temp-file DataStore, hand-written fakes.
- `:domain` is **pure Kotlin** — it MUST NOT depend on `:protocol`, `:data`, or any Android/platform API. Allowed deps: `kotlinx-coroutines-core` only.
- `:data` depends on `:domain` and `:protocol`. It owns every conversion between domain types and `:protocol`/Wire types. Feature modules (Plan 3) will depend on `:domain`, never on `:data` or `:protocol` directly.
- Logging: Kermit tagged loggers only (`co.touchlab.kermit.Logger`). No `println`.
- Secrets policy (personal-use app): the client cert/key PEMs persist in **plain DataStore** — do NOT add Keychain/Keystore/EncryptedSharedPreferences. That upgrade is an explicit future decision for a store release.
- DI: **Koin**. `:data` exposes a `dataModule` (+ a `domainModule` if any domain-level singletons exist). The composition root and platform-specific singletons (the `DataStore` file path, the `Context`-bearing `MdnsBrowser`) are provided by the app modules in Plan 3 — `:data`'s Koin module declares them as required dependencies.
- **Carried-forward from Plan 1** (`protocol/README.md`): `RemoteSession` must be driven from a **single-threaded-confined `CoroutineScope`** (its generation guard's check-then-act is atomic only under cooperative scheduling). `RemoteControllerImpl` MUST create/accept such a scope and document it. TLS is pinned to 1.2 pending real-hardware verification (Plan 3). `DiscoveredTv` is `(name: String, host: String, port: Int)` — the `:protocol` README's "ipAddress/serviceName" wording is stale; use the real fields.

### `:protocol` public API consumed by this plan (exact signatures — do not guess)

```kotlin
// package com.dgmltn.dpad.protocol.crypto
data class ClientIdentity(val certificatePem: String, val privateKeyPem: String, val publicParams: RsaPublicParams)
expect object ClientIdentityGenerator {
    fun generate(commonName: String): ClientIdentity
    fun fromPem(certificatePem: String, privateKeyPem: String): ClientIdentity
}

// package com.dgmltn.dpad.protocol.transport
expect class TlsSocketFactory(identity: ClientIdentity) { suspend fun connect(host: String, port: Int): TlsConnection }

// package com.dgmltn.dpad.protocol.discovery
data class DiscoveredTv(val name: String, val host: String, val port: Int)
expect class MdnsBrowser { fun discovered(): Flow<List<DiscoveredTv>> }   // Android actual ctor takes Context; JVM/iOS no-arg

// package com.dgmltn.dpad.protocol.pairing
sealed interface PairingEvent { object WaitingForCode; object Paired; data class Failed(val reason: PairingFailure) }
enum class PairingFailure { WRONG_CODE, REJECTED, CONNECTION_LOST, TIMEOUT }
class PairingClient(factory: TlsSocketFactory, identity: ClientIdentity, clientName: String = "Dpad",
                    serviceName: String = "com.dgmltn.dpad", timeout: Duration = 10.seconds) {
    val events: Flow<PairingEvent>
    suspend fun start(host: String, port: Int = 6467)
    suspend fun submitCode(code: String)
    fun cancel()
}

// package com.dgmltn.dpad.protocol.session
data class HostAddress(val host: String, val port: Int = 6466)
data class VolumeState(val level: Int, val max: Int, val muted: Boolean)
sealed interface SessionState { object Disconnected; object Connecting; object Connected; object PairingRequired }
class RemoteSession(scope: CoroutineScope, factory: TlsSocketFactory, resolveHost: suspend () -> HostAddress,
                    clientModel: String = "Dpad", backoffMillis: List<Long> = listOf(1000,2000,4000,8000,15000)) {
    val state: StateFlow<SessionState>
    val volume: StateFlow<VolumeState?>
    fun connect(); fun disconnect()
    fun sendKey(keyCode: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT)  // remote.RemoteKeyCode / remote.RemoteDirection
    fun launchApp(appLinkUrl: String)
}
// Wire-generated in package `remote`: enum RemoteKeyCode (KEYCODE_DPAD_UP, KEYCODE_BACK, KEYCODE_HOME,
//   KEYCODE_VOLUME_UP/DOWN, KEYCODE_VOLUME_MUTE, KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_REWIND,
//   KEYCODE_MEDIA_FAST_FORWARD, KEYCODE_POWER, KEYCODE_DPAD_DOWN/LEFT/RIGHT, KEYCODE_DPAD_CENTER, ...)
//   enum RemoteDirection (SHORT, START_LONG, END_LONG, ...)
```

---

### Task 1: Version catalog + Gradle additions

**Files:**
- Modify: `scripts/latest-versions.sh`, `gradle/libs.versions.toml`, `settings.gradle.kts`

**Interfaces:**
- Consumes: existing catalog (kotlin 2.4.10, agp 9.3.1, coroutines 1.11.0, kermit 2.1.0)
- Produces: catalog aliases later tasks reference — `libs.plugins.kotlinSerialization`, `libs.kotlinx.serialization.json`, `libs.androidx.datastore.preferences.core`, `libs.okio`, `libs.koin.core`, `libs.koin.test`; settings including `:domain` and `:data`

- [ ] **Step 1: Create branch**

```bash
git switch -c feat/domain-data main
```

- [ ] **Step 2: Extend the version script with the new libraries**

Append these lines to the `echo` block in `scripts/latest-versions.sh` (reusing its `central` helper):

```bash
echo "kotlinx-serialization  = $(central org/jetbrains/kotlinx kotlinx-serialization-json)"
echo "datastore-preferences  = $(central androidx/datastore datastore-preferences-core)"
echo "okio                   = $(central com/squareup/okio okio)"
echo "koin                   = $(central io/insert-koin koin-core)"
```

- [ ] **Step 3: Run it and capture output**

Run: `scripts/latest-versions.sh`
Expected: one concrete stable version per line. Use these exact values in Step 4 (this is the one permitted "fill-in" — values come from the live run, not memory). If `datastore-preferences-core` isn't on Maven Central under that path, read `https://dl.google.com/dl/android/maven2/androidx/datastore/group-index.xml` and take the highest stable `datastore-preferences-core`.

- [ ] **Step 4: Add versions, libraries, and the serialization plugin to the catalog**

In `gradle/libs.versions.toml`, add under `[versions]` (fill `<pinned>` from Step 3):

```toml
kotlinx-serialization = "<pinned>"
datastore = "<pinned>"
okio = "<pinned>"
koin = "<pinned>"
```

under `[libraries]`:

```toml
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
androidx-datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
okio-fakefilesystem = { module = "com.squareup.okio:okio-fakefilesystem", version.ref = "okio" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }
```

under `[plugins]`:

```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 5: Register the new modules in settings**

In `settings.gradle.kts`, add after `include(":protocol")`:

```kotlin
include(":domain")
include(":data")
```

- [ ] **Step 6: Verify the catalog resolves**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. (The `:domain`/`:data` includes will warn about missing build files until Task 2/6 create them — if `help` hard-fails on the missing projects, create empty `domain/build.gradle.kts` and `data/build.gradle.kts` containing only `// placeholder — real content in Task 2/6` plus `plugins { }`, or temporarily comment the includes and restore them in Task 2. Prefer the placeholder.)

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "[deps] Add serialization, DataStore, okio, Koin to catalog; register :domain/:data"
```

---

### Task 2: `:domain` module + core value types

**Files:**
- Create: `domain/build.gradle.kts`, `domain/README.md`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/RemoteKey.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/ConnectionState.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/PairingProgress.kt`
- Test: `domain/src/jvmTest/kotlin/com/dgmltn/dpad/domain/RemoteKeyTest.kt`

**Interfaces:**
- Consumes: catalog from Task 1
- Produces:

```kotlin
package com.dgmltn.dpad.domain
enum class RemoteKey {
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
    BACK, HOME, VOLUME_UP, VOLUME_DOWN, MUTE,
    MEDIA_PLAY_PAUSE, MEDIA_REWIND, MEDIA_FAST_FORWARD, POWER,
}
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object PairingRequired : ConnectionState   // stored client cert no longer trusted; re-pair
}
data class Volume(val level: Int, val max: Int, val muted: Boolean)
sealed interface PairingProgress {
    data object Connecting : PairingProgress
    data object AwaitingCode : PairingProgress      // TV is showing the on-screen code; prompt the user
    data object Paired : PairingProgress
    data class Failed(val reason: PairingFailureReason) : PairingProgress
}
enum class PairingFailureReason { WRONG_CODE, REJECTED, CONNECTION_LOST, TIMEOUT }
```

- [ ] **Step 1: Module build file**

`domain/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    androidLibrary {
        namespace = "com.dgmltn.dpad.domain"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

(If the `androidLibrary { namespace/compileSdk/minSdk }` DSL differs on the pinned AGP, mirror exactly what `:protocol`'s `protocol/build.gradle.kts` does — it already compiles under this plugin. Copy that block's shape.)

`domain/README.md`: "Pure-Kotlin app-wide domain models and repository/service contracts for Dpad. Depends only on kotlinx-coroutines-core. No dependency on :protocol, :data, or any platform API. Consumed by feature modules and :data."

- [ ] **Step 2: Write the failing test**

`RemoteKeyTest.kt`:

```kotlin
package com.dgmltn.dpad.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteKeyTest {
    @Test fun coversEveryControlOnTheRemoteScreen() {
        // The remote screen (spec) needs exactly these keys; guard against accidental additions/removals.
        val expected = setOf(
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER",
            "BACK", "HOME", "VOLUME_UP", "VOLUME_DOWN", "MUTE",
            "MEDIA_PLAY_PAUSE", "MEDIA_REWIND", "MEDIA_FAST_FORWARD", "POWER",
        )
        assertEquals(expected, RemoteKey.entries.map { it.name }.toSet())
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :domain:jvmTest`
Expected: FAIL — `RemoteKey` unresolved.

- [ ] **Step 4: Implement the three files**

Write `RemoteKey.kt`, `ConnectionState.kt`, `PairingProgress.kt` exactly as in the Produces block.

- [ ] **Step 5: Run to verify it passes + compile all targets**

Run: `./gradlew :domain:jvmTest :domain:compileKotlinIosSimulatorArm64 :domain:compileAndroidMain`
Expected: `BUILD SUCCESSFUL`, test passes.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "[domain] Module scaffold and core value types (RemoteKey, ConnectionState, Volume, PairingProgress)"
```

---

### Task 3: `:domain` entity models

**Files:**
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/PairedDevice.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/DiscoveredDevice.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/Shortcut.kt`
- Test: `domain/src/jvmTest/kotlin/com/dgmltn/dpad/domain/EntityModelsTest.kt`

**Interfaces:**
- Consumes: nothing new
- Produces:

```kotlin
package com.dgmltn.dpad.domain
/** A TV the user has paired with. [id] is app-generated and stable across host changes. */
data class PairedDevice(
    val id: String,
    val name: String,
    val host: String,          // last-known IP; re-resolved via mDNS before each connect
    val port: Int = 6466,
    val serviceName: String,   // mDNS instance name, used to re-resolve host
)
/** A TV seen on the network via mDNS but not necessarily paired. */
data class DiscoveredDevice(val name: String, val host: String, val port: Int)
/** A configurable app-launch button. [appLinkUrl] is what the TV's RemoteAppLinkLaunchRequest receives. */
data class Shortcut(val id: String, val label: String, val appLinkUrl: String)
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dgmltn.dpad.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EntityModelsTest {
    @Test fun pairedDeviceDefaultsToSessionPort() {
        val d = PairedDevice(id = "a", name = "Living Room", host = "192.168.1.5", serviceName = "living._androidtvremote2._tcp")
        assertEquals(6466, d.port)
    }

    @Test fun discoveredDeviceCarriesHostAndPort() {
        val d = DiscoveredDevice(name = "Bedroom", host = "192.168.1.9", port = 6466)
        assertEquals("192.168.1.9", d.host)
    }

    @Test fun shortcutHoldsLabelAndUrl() {
        val s = Shortcut(id = "s1", label = "Netflix", appLinkUrl = "https://www.netflix.com/title")
        assertEquals("Netflix", s.label)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :domain:jvmTest --tests '*EntityModelsTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the three files** exactly as in Produces.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :domain:jvmTest --tests '*EntityModelsTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[domain] Entity models: PairedDevice, DiscoveredDevice, Shortcut"
```

---

### Task 4: `:domain` shortcut catalog

**Files:**
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/ShortcutCatalog.kt`
- Test: `domain/src/jvmTest/kotlin/com/dgmltn/dpad/domain/ShortcutCatalogTest.kt`

**Interfaces:**
- Consumes: `Shortcut` (Task 3)
- Produces:

```kotlin
package com.dgmltn.dpad.domain
/** A pickable entry in the curated app catalog (spec: Netflix … Twitch). */
data class CatalogApp(val key: String, val label: String, val appLinkUrl: String)
object ShortcutCatalog {
    val apps: List<CatalogApp>          // the curated list
    /** Build a user Shortcut from a catalog entry, assigning [id]. */
    fun toShortcut(app: CatalogApp, id: String): Shortcut
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dgmltn.dpad.domain

import kotlin.test.*

class ShortcutCatalogTest {
    @Test fun catalogContainsTheSpecifiedApps() {
        val labels = ShortcutCatalog.apps.map { it.label }.toSet()
        listOf("Netflix", "YouTube", "Prime Video", "Disney+", "Max", "Peacock",
                "Paramount+", "Plex", "Jellyfin", "Spotify", "Twitch").forEach {
            assertTrue(it in labels, "catalog missing $it")
        }
    }

    @Test fun keysAndUrlsAreUnique() {
        assertEquals(ShortcutCatalog.apps.size, ShortcutCatalog.apps.map { it.key }.toSet().size)
        assertEquals(ShortcutCatalog.apps.size, ShortcutCatalog.apps.map { it.appLinkUrl }.toSet().size)
    }

    @Test fun toShortcutCopiesLabelAndUrlAndAssignsId() {
        val app = ShortcutCatalog.apps.first { it.label == "Netflix" }
        val s = ShortcutCatalog.toShortcut(app, id = "sc-1")
        assertEquals("sc-1", s.id)
        assertEquals("Netflix", s.label)
        assertEquals(app.appLinkUrl, s.appLinkUrl)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :domain:jvmTest --tests '*ShortcutCatalogTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`ShortcutCatalog.kt` — use real Android TV app-link URLs (deep links / market fallbacks). These are the launch URLs sent verbatim to the TV:

```kotlin
package com.dgmltn.dpad.domain

data class CatalogApp(val key: String, val label: String, val appLinkUrl: String)

object ShortcutCatalog {
    val apps: List<CatalogApp> = listOf(
        CatalogApp("netflix", "Netflix", "https://www.netflix.com/title"),
        CatalogApp("youtube", "YouTube", "https://www.youtube.com"),
        CatalogApp("primevideo", "Prime Video", "https://app.primevideo.com"),
        CatalogApp("disneyplus", "Disney+", "https://www.disneyplus.com"),
        CatalogApp("max", "Max", "https://play.max.com"),
        CatalogApp("peacock", "Peacock", "https://www.peacocktv.com"),
        CatalogApp("paramountplus", "Paramount+", "https://www.paramountplus.com"),
        CatalogApp("plex", "Plex", "https://app.plex.tv"),
        CatalogApp("jellyfin", "Jellyfin", "https://jellyfin.org"),
        CatalogApp("spotify", "Spotify", "https://open.spotify.com"),
        CatalogApp("twitch", "Twitch", "https://www.twitch.tv"),
    )

    fun toShortcut(app: CatalogApp, id: String): Shortcut =
        Shortcut(id = id, label = app.label, appLinkUrl = app.appLinkUrl)
}
```

(App-link URLs are best-effort launch targets; Plan 3 device testing confirms each resolves on a real TV. A URL that doesn't launch is a Plan-3 data fix, not a structural problem.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :domain:jvmTest --tests '*ShortcutCatalogTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[domain] Curated shortcut catalog"
```

---

### Task 5: `:domain` repository & service contracts

**Files:**
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/DeviceRepository.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/ShortcutRepository.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/ClientIdentityStore.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/DeviceDiscovery.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/DevicePairer.kt`
- Create: `domain/src/commonMain/kotlin/com/dgmltn/dpad/domain/RemoteController.kt`
- Test: `domain/src/jvmTest/kotlin/com/dgmltn/dpad/domain/ContractsCompileTest.kt`

**Interfaces:**
- Consumes: all domain models (Tasks 2-4)
- Produces (the contracts `:data` implements and Plan 3's ViewModels consume):

```kotlin
package com.dgmltn.dpad.domain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val devices: Flow<List<PairedDevice>>
    val lastUsedDeviceId: Flow<String?>
    suspend fun upsert(device: PairedDevice)      // add or update by id
    suspend fun remove(id: String)
    suspend fun setLastUsed(id: String)
    suspend fun get(id: String): PairedDevice?
}

interface ShortcutRepository {
    val shortcuts: Flow<List<Shortcut>>           // ordered
    suspend fun add(shortcut: Shortcut)
    suspend fun remove(id: String)
    suspend fun reorder(orderedIds: List<String>)
}

/** Supplies the app-wide client identity, generating and persisting it on first use. */
interface ClientIdentityStore {
    /** Returns the persisted identity, generating+persisting one the first time. Idempotent. */
    suspend fun getOrCreate(): ClientIdentityHandle
}
/** Opaque handle so :domain need not know :protocol's ClientIdentity type. */
class ClientIdentityHandle internal constructor(val certificatePem: String, val privateKeyPem: String)

interface DeviceDiscovery {
    /** Live list of TVs seen on the LAN. */
    fun discovered(): Flow<List<DiscoveredDevice>>
}

interface DevicePairer {
    val progress: Flow<PairingProgress>
    /** Begin pairing with [device]; drives [progress] to AwaitingCode. */
    suspend fun start(device: DiscoveredDevice)
    /** Submit the on-screen code; drives [progress] to Paired or Failed. On Paired, persists a PairedDevice. */
    suspend fun submitCode(code: String)
    fun cancel()
}

interface RemoteController {
    val connection: StateFlow<ConnectionState>
    val volume: StateFlow<Volume?>
    /** Connect to [device] (idempotent; auto-reconnects). Switches target if already connected elsewhere. */
    fun connect(device: PairedDevice)
    fun disconnect()
    fun press(key: RemoteKey)
    fun launchApp(appLinkUrl: String)
    fun sendText(text: String)          // per-character key events for search fields
}
```

Note: `ClientIdentityHandle`'s constructor is `internal` so only `:data` (which shares the module boundary via a same-package factory) can build it — but since `:data` is a separate module, mark the constructor `public` instead if the compiler rejects cross-module `internal` construction. Simplest: make it a plain `data class ClientIdentityHandle(val certificatePem: String, val privateKeyPem: String)` with a public constructor. Use that form.

- [ ] **Step 1: Write the failing compile-test**

`ContractsCompileTest.kt` — a compile-time check that the contracts exist with the expected shape (no behavior yet):

```kotlin
package com.dgmltn.dpad.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertNotNull

class ContractsCompileTest {
    @Test fun contractsHaveExpectedSurface() {
        // Anonymous no-op implementations must compile against the interfaces — this pins their shape.
        val discovery = object : DeviceDiscovery {
            override fun discovered(): Flow<List<DiscoveredDevice>> = kotlinx.coroutines.flow.flowOf(emptyList())
        }
        assertNotNull(discovery)
        val handle = ClientIdentityHandle(certificatePem = "c", privateKeyPem = "k")
        assertNotNull(handle.certificatePem)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :domain:jvmTest --tests '*ContractsCompileTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the six contract files** exactly as in Produces, using the plain `data class ClientIdentityHandle(...)` form.

- [ ] **Step 4: Run to verify pass + all targets compile**

Run: `./gradlew :domain:jvmTest :domain:compileKotlinIosSimulatorArm64 :domain:compileAndroidMain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[domain] Repository and service contracts"
```

---

### Task 6: `:data` module + DataStore factory + serialization DTOs

**Files:**
- Create: `data/build.gradle.kts`, `data/README.md`
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/store/DataStoreFactory.kt`
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/store/Dtos.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/store/DtosTest.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/store/TestDataStore.kt`

**Interfaces:**
- Consumes: `:domain` models, `:protocol` (available as a dependency)
- Produces:

```kotlin
package com.dgmltn.dpad.data.store
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okio.Path
/** Builds a Preferences DataStore at [path]. Platform code (Plan 3) supplies the path. */
fun createDataStore(path: Path): DataStore<Preferences>

@Serializable data class PairedDeviceDto(val id: String, val name: String, val host: String, val port: Int, val serviceName: String)
@Serializable data class ShortcutDto(val id: String, val label: String, val appLinkUrl: String)
// mappers
fun PairedDeviceDto.toDomain(): PairedDevice ; fun PairedDevice.toDto(): PairedDeviceDto
fun ShortcutDto.toDomain(): Shortcut ; fun Shortcut.toDto(): ShortcutDto
```

- [ ] **Step 1: Module build file**

`data/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    androidLibrary {
        namespace = "com.dgmltn.dpad.data"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            implementation(projects.protocol)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
```

(`projects.domain`/`projects.protocol` require type-safe project accessors — if not enabled, use `project(":domain")` / `project(":protocol")`. Check whether `:protocol`'s build uses `projects.` accessors; match that convention. If `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` isn't in `settings.gradle.kts`, use the `project(":...")` string form.)

`data/README.md`: "Implements `:domain` contracts. Depends on `:domain` and `:protocol`. Persists via DataStore Preferences (JSON-in-Preferences); owns all mapping between domain types and `:protocol`/Wire types. Exposes a Koin `dataModule`; the DataStore path and the `Context`-bearing `MdnsBrowser` are supplied by the app modules (Plan 3)."

- [ ] **Step 2: Write the failing test**

`DtosTest.kt`:

```kotlin
package com.dgmltn.dpad.data.store

import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DtosTest {
    private val json = Json

    @Test fun pairedDeviceRoundTripsThroughJsonAndDomain() {
        val device = PairedDevice(id = "1", name = "Den", host = "10.0.0.4", port = 6466, serviceName = "den._androidtvremote2._tcp")
        val encoded = json.encodeToString(PairedDeviceDto.serializer(), device.toDto())
        val decoded = json.decodeFromString(PairedDeviceDto.serializer(), encoded).toDomain()
        assertEquals(device, decoded)
    }

    @Test fun shortcutRoundTrips() {
        val s = Shortcut(id = "s", label = "Plex", appLinkUrl = "https://app.plex.tv")
        assertEquals(s, json.decodeFromString(ShortcutDto.serializer(),
            json.encodeToString(ShortcutDto.serializer(), s.toDto())).toDomain())
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*DtosTest*'`
Expected: FAIL — module/types unresolved.

- [ ] **Step 4: Implement**

`DataStoreFactory.kt`:

```kotlin
package com.dgmltn.dpad.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path

fun createDataStore(path: Path): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path })
```

`Dtos.kt` — the `@Serializable` DTOs + mappers exactly as in Produces (each mapper is a trivial field copy).

`TestDataStore.kt` (jvmTest helper, reused by later tasks) — a temp-file DataStore:

```kotlin
package com.dgmltn.dpad.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.nio.file.Files

/** A DataStore backed by a fresh temp file — one per call, so tests don't share state. */
fun tempDataStore(): DataStore<Preferences> {
    val file = Files.createTempFile("dpad-test", ".preferences_pb").toFile()
    file.delete()   // DataStore must create it itself
    return createDataStore(file.absolutePath.toPath())
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*DtosTest*'`
Expected: PASS (2 tests).

- [ ] **Step 6: Verify all targets compile**

Run: `./gradlew :data:compileKotlinIosSimulatorArm64 :data:compileAndroidMain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "[data] Module scaffold, DataStore factory, serialization DTOs"
```

---

### Task 7: `:data` DeviceRepository over DataStore

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/DeviceRepositoryImpl.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/DeviceRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `DeviceRepository`, `PairedDevice` (`:domain`); `createDataStore`/DTOs (Task 6)
- Produces: `class DeviceRepositoryImpl(private val store: DataStore<Preferences>) : DeviceRepository`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dgmltn.dpad.data

import app.cash.turbine.test
import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.PairedDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DeviceRepositoryImplTest {
    private fun repo() = DeviceRepositoryImpl(tempDataStore())
    private fun device(id: String, name: String = "TV") =
        PairedDevice(id = id, name = name, host = "10.0.0.$id", serviceName = "$id._androidtvremote2._tcp")

    @Test fun startsEmpty() = runTest {
        assertEquals(emptyList(), repo().devices.first())
    }

    @Test fun upsertAddsThenUpdatesById() = runTest {
        val r = repo()
        r.upsert(device("1", "Den"))
        r.upsert(device("1", "Living Room"))   // same id → update, not duplicate
        val all = r.devices.first()
        assertEquals(1, all.size)
        assertEquals("Living Room", all.single().name)
    }

    @Test fun removeDeletesById() = runTest {
        val r = repo()
        r.upsert(device("1")); r.upsert(device("2"))
        r.remove("1")
        assertEquals(listOf("2"), r.devices.first().map { it.id })
    }

    @Test fun lastUsedTracksAndClearsOnRemove() = runTest {
        val r = repo()
        r.upsert(device("1")); r.setLastUsed("1")
        assertEquals("1", r.lastUsedDeviceId.first())
        r.remove("1")
        assertNull(r.lastUsedDeviceId.first())   // removing the last-used device clears the pointer
    }

    @Test fun getReturnsByIdOrNull() = runTest {
        val r = repo()
        r.upsert(device("1", "Den"))
        assertEquals("Den", r.get("1")?.name)
        assertNull(r.get("nope"))
    }

    @Test fun devicesFlowEmitsOnChange() = runTest {
        val r = repo()
        r.devices.test {
            assertEquals(emptyList(), awaitItem())
            r.upsert(device("1"))
            assertEquals(listOf("1"), awaitItem().map { it.id })
        }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*DeviceRepositoryImplTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.dgmltn.dpad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dgmltn.dpad.data.store.PairedDeviceDto
import com.dgmltn.dpad.data.store.toDomain
import com.dgmltn.dpad.data.store.toDto
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.PairedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class DeviceRepositoryImpl(private val store: DataStore<Preferences>) : DeviceRepository {
    private val devicesKey = stringPreferencesKey("paired_devices")
    private val lastUsedKey = stringPreferencesKey("last_used_device_id")
    private val json = Json

    override val devices: Flow<List<PairedDevice>> =
        store.data.map { prefs -> decode(prefs[devicesKey]).map { it.toDomain() } }

    override val lastUsedDeviceId: Flow<String?> =
        store.data.map { it[lastUsedKey] }

    override suspend fun upsert(device: PairedDevice) {
        store.edit { prefs ->
            val current = decode(prefs[devicesKey]).associateBy { it.id }.toMutableMap()
            current[device.id] = device.toDto()
            prefs[devicesKey] = json.encodeToString(current.values.toList())
        }
    }

    override suspend fun remove(id: String) {
        store.edit { prefs ->
            prefs[devicesKey] = json.encodeToString(decode(prefs[devicesKey]).filterNot { it.id == id })
            if (prefs[lastUsedKey] == id) prefs.remove(lastUsedKey)
        }
    }

    override suspend fun setLastUsed(id: String) {
        store.edit { it[lastUsedKey] = id }
    }

    override suspend fun get(id: String): PairedDevice? =
        devices.first().firstOrNull { it.id == id }

    private fun decode(raw: String?): List<PairedDeviceDto> =
        if (raw.isNullOrEmpty()) emptyList() else json.decodeFromString(raw)
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*DeviceRepositoryImplTest*'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[data] DeviceRepository over DataStore"
```

---

### Task 8: `:data` ShortcutRepository over DataStore

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/ShortcutRepositoryImpl.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/ShortcutRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `ShortcutRepository`, `Shortcut` (`:domain`); DTOs (Task 6)
- Produces: `class ShortcutRepositoryImpl(private val store: DataStore<Preferences>) : ShortcutRepository`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ShortcutRepositoryImplTest {
    private fun repo() = ShortcutRepositoryImpl(tempDataStore())
    private fun sc(id: String, label: String = id) = Shortcut(id = id, label = label, appLinkUrl = "https://x/$id")

    @Test fun startsEmpty() = runTest { assertEquals(emptyList(), repo().shortcuts.first()) }

    @Test fun addAppendsInOrder() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b"))
        assertEquals(listOf("a", "b"), r.shortcuts.first().map { it.id })
    }

    @Test fun removeDeletesById() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b"))
        r.remove("a")
        assertEquals(listOf("b"), r.shortcuts.first().map { it.id })
    }

    @Test fun reorderAppliesNewOrder() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b")); r.add(sc("c"))
        r.reorder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), r.shortcuts.first().map { it.id })
    }

    @Test fun reorderIgnoresUnknownIdsAndKeepsOmittedAtEnd() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b"))
        r.reorder(listOf("b", "ghost"))   // ghost isn't a shortcut; a is omitted from the order
        assertEquals(listOf("b", "a"), r.shortcuts.first().map { it.id })  // omitted 'a' retained, appended
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*ShortcutRepositoryImplTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.dgmltn.dpad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dgmltn.dpad.data.store.ShortcutDto
import com.dgmltn.dpad.data.store.toDomain
import com.dgmltn.dpad.data.store.toDto
import com.dgmltn.dpad.domain.Shortcut
import com.dgmltn.dpad.domain.ShortcutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ShortcutRepositoryImpl(private val store: DataStore<Preferences>) : ShortcutRepository {
    private val key = stringPreferencesKey("shortcuts")
    private val json = Json

    override val shortcuts: Flow<List<Shortcut>> =
        store.data.map { prefs -> decode(prefs[key]).map { it.toDomain() } }

    override suspend fun add(shortcut: Shortcut) = store.edit { prefs ->
        prefs[key] = json.encodeToString(decode(prefs[key]) + shortcut.toDto())
    }

    override suspend fun remove(id: String) = store.edit { prefs ->
        prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.id == id })
    }

    override suspend fun reorder(orderedIds: List<String>) = store.edit { prefs ->
        val byId = decode(prefs[key]).associateBy { it.id }
        val ordered = orderedIds.mapNotNull { byId[it] }               // known ids in requested order
        val omitted = byId.values.filter { it.id !in orderedIds.toSet() }  // anything not listed, retained
        prefs[key] = json.encodeToString(ordered + omitted)
    }

    private fun decode(raw: String?): List<ShortcutDto> =
        if (raw.isNullOrEmpty()) emptyList() else json.decodeFromString(raw)
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*ShortcutRepositoryImplTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[data] ShortcutRepository over DataStore"
```

---

### Task 9: `:data` ClientIdentityStore (generate-once, persist, reload)

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/ClientIdentityStoreImpl.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/ClientIdentityStoreImplTest.kt`

**Interfaces:**
- Consumes: `ClientIdentityStore`, `ClientIdentityHandle` (`:domain`); `ClientIdentityGenerator`, `ClientIdentity` (`:protocol`)
- Produces:

```kotlin
class ClientIdentityStoreImpl(
    private val store: DataStore<Preferences>,
    private val commonName: String = "Dpad",
) : ClientIdentityStore
// plus, for :data-internal use by the pairing/session adapters:
suspend fun ClientIdentityStore.protocolIdentity(): ClientIdentity   // rebuilds :protocol ClientIdentity from the handle
```

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.store.tempDataStore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ClientIdentityStoreImplTest {
    @Test fun generatesAndPersistsOnFirstCall() = runTest {
        val store = tempDataStore()
        val handle = ClientIdentityStoreImpl(store).getOrCreate()
        assertTrue(handle.certificatePem.contains("BEGIN CERTIFICATE"))
        assertTrue(handle.privateKeyPem.contains("BEGIN PRIVATE KEY"))
    }

    @Test fun returnsTheSameIdentityAcrossCallsAndInstances() = runTest {
        val store = tempDataStore()
        val first = ClientIdentityStoreImpl(store).getOrCreate()
        // A fresh store instance over the SAME backing file must reload, not regenerate.
        val second = ClientIdentityStoreImpl(store).getOrCreate()
        assertEquals(first.certificatePem, second.certificatePem)
        assertEquals(first.privateKeyPem, second.privateKeyPem)
    }

    @Test fun protocolIdentityRebuildsFromPersistedPems() = runTest {
        val store = tempDataStore()
        val impl = ClientIdentityStoreImpl(store)
        impl.getOrCreate()
        val identity = impl.protocolIdentity()   // must not throw (fromPem validates cert/key match)
        assertTrue(identity.certificatePem.contains("BEGIN CERTIFICATE"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*ClientIdentityStoreImplTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.dgmltn.dpad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dgmltn.dpad.domain.ClientIdentityHandle
import com.dgmltn.dpad.domain.ClientIdentityStore
import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import kotlinx.coroutines.flow.first

class ClientIdentityStoreImpl(
    private val store: DataStore<Preferences>,
    private val commonName: String = "Dpad",
) : ClientIdentityStore {
    private val certKey = stringPreferencesKey("client_cert_pem")
    private val keyKey = stringPreferencesKey("client_key_pem")

    override suspend fun getOrCreate(): ClientIdentityHandle {
        store.data.first().let { prefs ->
            val cert = prefs[certKey]; val key = prefs[keyKey]
            if (cert != null && key != null) return ClientIdentityHandle(cert, key)
        }
        val generated = ClientIdentityGenerator.generate(commonName)
        store.edit { it[certKey] = generated.certificatePem; it[keyKey] = generated.privateKeyPem }
        return ClientIdentityHandle(generated.certificatePem, generated.privateKeyPem)
    }
}

/** Rebuild the :protocol ClientIdentity from the persisted handle (validates cert/key match). */
suspend fun ClientIdentityStore.protocolIdentity(): ClientIdentity {
    val handle = getOrCreate()
    return ClientIdentityGenerator.fromPem(handle.certificatePem, handle.privateKeyPem)
}
```

Note the potential generate-race: two concurrent `getOrCreate()` calls could both generate. For this personal app the store is accessed from a single composition root; document that `getOrCreate()` is expected to be called from a single scope. (If a guard is wanted later, wrap in a `Mutex` — not needed for Plan 2's scope.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*ClientIdentityStoreImplTest*'`
Expected: PASS (3 tests). (This exercises real `:protocol` `ClientIdentityGenerator` on the JVM — RSA keygen, ~1s per test, acceptable.)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[data] ClientIdentityStore: generate-once, persist, reload"
```

---

### Task 10: `:data` pure protocol mappers

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/mapping/ProtocolMappers.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/mapping/ProtocolMappersTest.kt`

**Interfaces:**
- Consumes: `:domain` (`RemoteKey`, `ConnectionState`, `Volume`, `PairingProgress`, `PairingFailureReason`, `DiscoveredDevice`); `:protocol` (`remote.RemoteKeyCode`, `SessionState`, `VolumeState`, `PairingEvent`, `PairingFailure`, `DiscoveredTv`)
- Produces (all pure, total functions):

```kotlin
package com.dgmltn.dpad.data.mapping
fun RemoteKey.toKeyCode(): remote.RemoteKeyCode
fun SessionState.toDomain(): ConnectionState
fun VolumeState.toDomain(): Volume
fun PairingEvent.toProgress(): PairingProgress
fun PairingFailure.toReason(): PairingFailureReason
fun DiscoveredTv.toDomain(): DiscoveredDevice
```

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dgmltn.dpad.data.mapping

import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.discovery.DiscoveredTv
import com.dgmltn.dpad.protocol.pairing.PairingEvent
import com.dgmltn.dpad.protocol.pairing.PairingFailure
import com.dgmltn.dpad.protocol.session.SessionState
import com.dgmltn.dpad.protocol.session.VolumeState
import remote.RemoteKeyCode
import kotlin.test.*

class ProtocolMappersTest {
    @Test fun everyRemoteKeyMapsToADistinctKeyCode() {
        val codes = RemoteKey.entries.map { it.toKeyCode() }
        assertEquals(RemoteKey.entries.size, codes.toSet().size, "RemoteKey→RemoteKeyCode must be injective")
        // spot-check a few load-bearing ones
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_UP, RemoteKey.DPAD_UP.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_BACK, RemoteKey.BACK.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_VOLUME_MUTE, RemoteKey.MUTE.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE, RemoteKey.MEDIA_PLAY_PAUSE.toKeyCode())
        assertEquals(RemoteKeyCode.KEYCODE_POWER, RemoteKey.POWER.toKeyCode())
    }

    @Test fun sessionStateMapsToConnectionState() {
        assertEquals(ConnectionState.Disconnected, SessionState.Disconnected.toDomain())
        assertEquals(ConnectionState.Connecting, SessionState.Connecting.toDomain())
        assertEquals(ConnectionState.Connected, SessionState.Connected.toDomain())
        assertEquals(ConnectionState.PairingRequired, SessionState.PairingRequired.toDomain())
    }

    @Test fun volumeStateMaps() {
        assertEquals(Volume(7, 100, false), VolumeState(7, 100, false).toDomain())
    }

    @Test fun pairingEventsMapToProgress() {
        assertEquals(PairingProgress.AwaitingCode, PairingEvent.WaitingForCode.toProgress())
        assertEquals(PairingProgress.Paired, PairingEvent.Paired.toProgress())
        assertEquals(PairingProgress.Failed(PairingFailureReason.WRONG_CODE),
            PairingEvent.Failed(PairingFailure.WRONG_CODE).toProgress())
    }

    @Test fun discoveredTvMaps() {
        assertEquals(DiscoveredDevice("Den", "10.0.0.4", 6466), DiscoveredTv("Den", "10.0.0.4", 6466).toDomain())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*ProtocolMappersTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.dgmltn.dpad.data.mapping

import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.discovery.DiscoveredTv
import com.dgmltn.dpad.protocol.pairing.PairingEvent
import com.dgmltn.dpad.protocol.pairing.PairingFailure
import com.dgmltn.dpad.protocol.session.SessionState
import com.dgmltn.dpad.protocol.session.VolumeState
import remote.RemoteKeyCode

fun RemoteKey.toKeyCode(): RemoteKeyCode = when (this) {
    RemoteKey.DPAD_UP -> RemoteKeyCode.KEYCODE_DPAD_UP
    RemoteKey.DPAD_DOWN -> RemoteKeyCode.KEYCODE_DPAD_DOWN
    RemoteKey.DPAD_LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
    RemoteKey.DPAD_RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
    RemoteKey.DPAD_CENTER -> RemoteKeyCode.KEYCODE_DPAD_CENTER
    RemoteKey.BACK -> RemoteKeyCode.KEYCODE_BACK
    RemoteKey.HOME -> RemoteKeyCode.KEYCODE_HOME
    RemoteKey.VOLUME_UP -> RemoteKeyCode.KEYCODE_VOLUME_UP
    RemoteKey.VOLUME_DOWN -> RemoteKeyCode.KEYCODE_VOLUME_DOWN
    RemoteKey.MUTE -> RemoteKeyCode.KEYCODE_VOLUME_MUTE
    RemoteKey.MEDIA_PLAY_PAUSE -> RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE
    RemoteKey.MEDIA_REWIND -> RemoteKeyCode.KEYCODE_MEDIA_REWIND
    RemoteKey.MEDIA_FAST_FORWARD -> RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD
    RemoteKey.POWER -> RemoteKeyCode.KEYCODE_POWER
}

fun SessionState.toDomain(): ConnectionState = when (this) {
    SessionState.Disconnected -> ConnectionState.Disconnected
    SessionState.Connecting -> ConnectionState.Connecting
    SessionState.Connected -> ConnectionState.Connected
    SessionState.PairingRequired -> ConnectionState.PairingRequired
}

fun VolumeState.toDomain(): Volume = Volume(level = level, max = max, muted = muted)

fun PairingFailure.toReason(): PairingFailureReason = when (this) {
    PairingFailure.WRONG_CODE -> PairingFailureReason.WRONG_CODE
    PairingFailure.REJECTED -> PairingFailureReason.REJECTED
    PairingFailure.CONNECTION_LOST -> PairingFailureReason.CONNECTION_LOST
    PairingFailure.TIMEOUT -> PairingFailureReason.TIMEOUT
}

fun PairingEvent.toProgress(): PairingProgress = when (this) {
    PairingEvent.WaitingForCode -> PairingProgress.AwaitingCode
    PairingEvent.Paired -> PairingProgress.Paired
    is PairingEvent.Failed -> PairingProgress.Failed(reason.toReason())
}

fun DiscoveredTv.toDomain(): DiscoveredDevice = DiscoveredDevice(name = name, host = host, port = port)
```

(If the Wire-generated enum lacks one of these `KEYCODE_*` constants under the exact name, check `protocol/build/generated/` for the real constant name and use it — the `.proto` is the source of truth. All listed keys were confirmed present during Plan 1.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*ProtocolMappersTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[data] Pure protocol mappers (RemoteKey, SessionState, Volume, PairingEvent, DiscoveredTv)"
```

---

### Task 11: `:data` adapters (discovery, pairing, remote control)

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/DeviceDiscoveryImpl.kt`
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/resolve/HostResolver.kt`
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/DevicePairerImpl.kt`
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/RemoteControllerImpl.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/resolve/HostResolverTest.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/DeviceDiscoveryImplTest.kt`

**Interfaces:**
- Consumes: `:domain` contracts; `:protocol` (`MdnsBrowser`, `PairingClient`, `RemoteSession`, `TlsSocketFactory`, `HostAddress`); mappers (Task 10); `ClientIdentityStore.protocolIdentity()` (Task 9); `DeviceRepository` (Task 7)
- Produces:

```kotlin
class DeviceDiscoveryImpl(private val mdns: MdnsBrowser) : DeviceDiscovery
// pure, testable host-resolution policy shared by the session's resolveHost lambda:
object HostResolver {
    /** Prefer the freshest mDNS host matching this device's serviceName; fall back to the stored host. */
    fun resolve(device: PairedDevice, discovered: List<DiscoveredDevice>): HostAddress
}
class DevicePairerImpl(
    private val identityStore: ClientIdentityStore,
    private val deviceRepository: DeviceRepository,
    private val scope: CoroutineScope,
    private val newId: () -> String = { defaultId() },
) : DevicePairer
class RemoteControllerImpl(
    private val identityStore: ClientIdentityStore,
    private val discovery: DeviceDiscovery,
    /** Single-threaded-confined scope (see Plan-1 carried-forward note); the app supplies viewModelScope/main. */
    private val scope: CoroutineScope,
) : RemoteController
```

Design notes binding this task:
- **`HostResolver` is the only pure, fully-tested piece here** — it decides the host for each (re)connect. The three adapters otherwise wire `:domain` contracts to final `:protocol` classes (`MdnsBrowser`, `PairingClient`, `RemoteSession`), which can't be faked; those wirings are **compile-verified here and device-verified in Plan 3**. Keep them thin: construct the `:protocol` object, collect its `Flow`/`StateFlow`, map with Task-10 mappers, and (for pairing) persist on success via `deviceRepository`.
- `RemoteControllerImpl.scope` MUST be single-threaded-confined (Plan-1 carried-forward note). Document it on the class and in `data/README.md`.
- `RemoteControllerImpl.connect(device)` builds `resolveHost = { HostResolver.resolve(device, discovery.discovered().first()) }`, constructs `RemoteSession(scope, TlsSocketFactory(identity), resolveHost, ...)`, calls `connect()`, and mirrors `session.state.map { it.toDomain() }` / `session.volume.map { it?.toDomain() }` into its own `StateFlow`s. Switching device = `disconnect()` then build a new session.
- `DevicePairerImpl.start(device)` builds `PairingClient(TlsSocketFactory(identity), identity)`, collects `events` → `progress` (mapped), calls `client.start(device.host, device.port ... )` (pairing port 6467 is the client's default). `submitCode` forwards; on `PairingEvent.Paired`, persists `PairedDevice(id = newId(), name = device.name, host = device.host, port = 6466, serviceName = device.name)` via `deviceRepository.upsert(...)` and `setLastUsed(...)`.

- [ ] **Step 1: Write the failing tests for the testable pieces**

`HostResolverTest.kt`:

```kotlin
package com.dgmltn.dpad.data.resolve

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.session.HostAddress
import kotlin.test.*

class HostResolverTest {
    private val device = PairedDevice(id = "1", name = "Den", host = "10.0.0.4", port = 6466,
        serviceName = "den._androidtvremote2._tcp")

    @Test fun prefersFreshMdnsHostMatchingServiceName() {
        val discovered = listOf(DiscoveredDevice(name = "den._androidtvremote2._tcp", host = "10.0.0.99", port = 6466))
        assertEquals(HostAddress("10.0.0.99", 6466), HostResolver.resolve(device, discovered))
    }

    @Test fun fallsBackToStoredHostWhenNotDiscovered() {
        assertEquals(HostAddress("10.0.0.4", 6466), HostResolver.resolve(device, emptyList()))
    }

    @Test fun ignoresDiscoveredDevicesWithADifferentServiceName() {
        val discovered = listOf(DiscoveredDevice(name = "bedroom._androidtvremote2._tcp", host = "10.0.0.5", port = 6466))
        assertEquals(HostAddress("10.0.0.4", 6466), HostResolver.resolve(device, discovered))
    }
}
```

`DeviceDiscoveryImplTest.kt` — exercises the JVM `MdnsBrowser` no-arg actual (emits empty) to prove the wrapper maps and doesn't throw:

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DeviceDiscoveryImplTest {
    @Test fun mapsBrowserOutputToDomainList() = runTest {
        // JVM MdnsBrowser actual is a stub emitting emptyList; this proves the wrapper compiles + maps.
        val discovery = DeviceDiscoveryImpl(MdnsBrowser())
        assertEquals(emptyList(), discovery.discovered().first())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*HostResolverTest*' --tests '*DeviceDiscoveryImplTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the four files**

`HostResolver.kt`:

```kotlin
package com.dgmltn.dpad.data.resolve

import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.protocol.session.HostAddress

object HostResolver {
    fun resolve(device: PairedDevice, discovered: List<DiscoveredDevice>): HostAddress {
        val fresh = discovered.firstOrNull { it.name == device.serviceName }
        return HostAddress(host = fresh?.host ?: device.host, port = device.port)
    }
}
```

`DeviceDiscoveryImpl.kt`:

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.mapping.toDomain
import com.dgmltn.dpad.domain.DeviceDiscovery
import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceDiscoveryImpl(private val mdns: MdnsBrowser) : DeviceDiscovery {
    override fun discovered(): Flow<List<DiscoveredDevice>> =
        mdns.discovered().map { list -> list.map { it.toDomain() } }
}
```

`DevicePairerImpl.kt` — collects the pairing client's events into a mapped `progress` flow, persists on success. Use `kotlin.uuid.Uuid` for ids:

```kotlin
package com.dgmltn.dpad.data

import co.touchlab.kermit.Logger
import com.dgmltn.dpad.data.mapping.toProgress
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.pairing.PairingClient
import com.dgmltn.dpad.protocol.pairing.PairingEvent
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.CoroutineScope
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

    override suspend fun start(device: DiscoveredDevice) {
        pairing = device
        _progress.value = PairingProgress.Connecting
        val identity = identityStore.protocolIdentity()
        val c = PairingClient(TlsSocketFactory(identity), identity)
        client = c
        scope.launch {
            c.events.collect { event ->
                _progress.value = event.toProgress()
                if (event is PairingEvent.Paired) persist(device)
            }
        }
        c.start(device.host)   // pairing port defaults to 6467
    }

    override suspend fun submitCode(code: String) { client?.submitCode(code) }
    override fun cancel() { client?.cancel(); client = null }

    private suspend fun persist(device: DiscoveredDevice) {
        val paired = PairedDevice(id = newId(), name = device.name, host = device.host,
            port = 6466, serviceName = device.name)
        deviceRepository.upsert(paired)
        deviceRepository.setLastUsed(paired.id)
        log.i { "Paired ${device.name}, persisted as ${paired.id}" }
    }
}
```

`RemoteControllerImpl.kt`:

```kotlin
package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.mapping.toDomain
import com.dgmltn.dpad.data.mapping.toKeyCode
import com.dgmltn.dpad.data.resolve.HostResolver
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.session.RemoteSession
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.CoroutineScope
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

    override fun connect(device: PairedDevice) {
        disconnect()
        scope.launch {
            val identity = identityStore.protocolIdentity()
            val s = RemoteSession(
                scope = scope,
                factory = TlsSocketFactory(identity),
                resolveHost = { HostResolver.resolve(device, discovery.discovered().first()) },
            )
            session = s
            scope.launch { s.state.collect { _connection.value = it.toDomain() } }
            scope.launch { s.volume.collect { _volume.value = it?.toDomain() } }
            s.connect()
        }
    }

    override fun disconnect() {
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
```

Note the `sendText` gap: `:protocol`'s `RemoteSession` exposes `sendKey`/`launchApp` but no text API. Text input (per-character key events) is a Plan-3 concern that needs either a new `:protocol` method or ASCII→keycode mapping; Plan 2 leaves `sendText` as a documented no-op stub so the contract is complete. Flag this explicitly in the task report.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*HostResolverTest*' --tests '*DeviceDiscoveryImplTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Verify all targets compile (the adapters' real gate)**

Run: `./gradlew :data:compileKotlinIosSimulatorArm64 :data:compileAndroidMain :data:jvmTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "[data] Discovery/pairing/remote-control adapters + tested HostResolver policy"
```

---

### Task 12: `:data` Koin module + graph verification + docs + squash-merge

**Files:**
- Create: `data/src/commonMain/kotlin/com/dgmltn/dpad/data/di/DataModule.kt`
- Test: `data/src/jvmTest/kotlin/com/dgmltn/dpad/data/di/DataModuleTest.kt`
- Modify: `ARCHITECTURE.md`, `domain/README.md`, `data/README.md`

**Interfaces:**
- Consumes: every `:data` impl + `:domain` contract
- Produces:

```kotlin
package com.dgmltn.dpad.data.di
import org.koin.core.module.Module
/**
 * Wires :data implementations to :domain contracts. REQUIRES the platform module (Plan 3) to provide:
 *   single<DataStore<Preferences>> { ... platform path ... }
 *   single<MdnsBrowser> { ... Context on Android ... }
 *   single<CoroutineScope>(named("session")) { ... single-threaded-confined ... }
 */
val dataModule: Module
```

- [ ] **Step 1: Write the failing verification test**

`DataModuleTest.kt` — provides the platform singletons with test stand-ins and asserts every domain contract resolves:

```kotlin
package com.dgmltn.dpad.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.*

class DataModuleTest {
    @AfterTest fun tearDown() = stopKoin()

    @Test fun everyDomainContractResolves() {
        val platform = module {
            single<DataStore<Preferences>> { tempDataStore() }
            single { MdnsBrowser() }
            single<CoroutineScope>(named("session")) { CoroutineScope(Dispatchers.Unconfined) }
        }
        val koin = startKoin { modules(platform, dataModule) }.koin
        assertNotNull(koin.get<DeviceRepository>())
        assertNotNull(koin.get<ShortcutRepository>())
        assertNotNull(koin.get<ClientIdentityStore>())
        assertNotNull(koin.get<DeviceDiscovery>())
        assertNotNull(koin.get<DevicePairer>())
        assertNotNull(koin.get<RemoteController>())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :data:jvmTest --tests '*DataModuleTest*'`
Expected: FAIL — `dataModule` unresolved.

- [ ] **Step 3: Implement the Koin module**

```kotlin
package com.dgmltn.dpad.data.di

import com.dgmltn.dpad.data.*
import com.dgmltn.dpad.domain.*
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<DeviceRepository> { DeviceRepositoryImpl(get()) }
    single<ShortcutRepository> { ShortcutRepositoryImpl(get()) }
    single<ClientIdentityStore> { ClientIdentityStoreImpl(get()) }
    single<DeviceDiscovery> { DeviceDiscoveryImpl(get()) }
    factory<DevicePairer> { DevicePairerImpl(get(), get(), get(named("session"))) }
    single<RemoteController> { RemoteControllerImpl(get(), get(), get(named("session"))) }
}
```

(If Koin's `get(named("session"))` for the `CoroutineScope` needs a different resolution style on the pinned Koin version, adjust to match — the intent is: the session scope is a platform-provided named singleton.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :data:jvmTest --tests '*DataModuleTest*'`
Expected: PASS.

- [ ] **Step 5: Update docs**

`ARCHITECTURE.md`: mark `:domain` and `:data` implemented; update the dependency graph to show `:data → :domain`, `:data → :protocol`, `:domain` standalone. Add the module table rows.

`domain/README.md` / `data/README.md`: ensure they state the dependency rules and (for `:data`) the three platform-provided Koin singletons and the single-threaded-scope requirement for `RemoteController`.

- [ ] **Step 6: Full verification**

Run: `./gradlew :domain:jvmTest :data:jvmTest :domain:compileKotlinIosSimulatorArm64 :data:compileKotlinIosSimulatorArm64 :domain:compileAndroidMain :data:compileAndroidMain`
Expected: everything green. Do not merge red.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "[data] Koin dataModule, graph verification test, docs"
```

- [ ] **Step 8: Squash-merge (do NOT push)**

```bash
git switch main
git merge --squash feat/domain-data
git commit -m "[data] Domain + data layer: models, repositories, DataStore persistence, protocol adapters, DI

:domain — pure-Kotlin models (RemoteKey, PairedDevice, Shortcut, ConnectionState, …),
the curated shortcut catalog, and repository/service contracts.
:data — DataStore-backed DeviceRepository/ShortcutRepository/ClientIdentityStore, pure
protocol mappers, discovery/pairing/remote-control adapters over :protocol, and a Koin
dataModule. Repositories and mappers are JVM-tested against temp-file DataStore; the thin
:protocol-wiring adapters are compile-verified and device-verified in Plan 3."
git branch -D feat/domain-data
```

Stop here. Ask Doug before any push. Plan 3 (design system, feature UI, app targets, on-device verification) follows.

---

## Self-Review Notes (already applied)

- **Spec coverage:** shortcuts (curated catalog + custom, reorder/delete) → Tasks 4, 8; multiple paired devices + last-used + switch → Tasks 5, 7, 11; discovery via mDNS → Tasks 5, 11; pairing → Tasks 5, 11; remote control keys/app-launch → Tasks 5, 10, 11; persistence → Tasks 6-9; client cert as the credential → Task 9. UI (d-pad, buttons, text-input sheet) is Plan 3 by design.
- **Known deferrals (documented, not gaps):** `RemoteController.sendText` is a no-op stub — text input needs a `:protocol` text API or ASCII→keycode mapping, delivered with the Plan-3 UI. The pairing/session/discovery adapters wrap un-fakeable final `:protocol` classes and are compile-verified + Plan-3 device-verified; their pure sub-logic (`HostResolver`, all mappers) is fully unit-tested.
- **Type consistency:** domain contract signatures in Task 5 match their `:data` implementations in Tasks 7-11; mapper names in Task 10 match their uses in Task 11; `ClientIdentityHandle` uses the public-constructor `data class` form throughout.
- **Placeholder scan:** no TBD/TODO-as-requirement; the one deliberate stub (`sendText`) is called out explicitly with its reason and its Plan-3 owner.
