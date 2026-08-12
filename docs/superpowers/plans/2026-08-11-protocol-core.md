# Dpad Plan 1 of 3: Protocol Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `:protocol` — a standalone KMP library implementing the Android TV Remote protocol v2 (pairing + session), fully tested on the JVM against a `FakeTvServer`.

**Architecture:** All protocol logic (Wire-generated protobufs, varint framing, pairing state machine, session state machine) lives in `commonMain`. Three narrow expect/actual seams: `sha256`, `TlsSocketFactory`, `ClientIdentityGenerator`, plus `MdnsBrowser`. JVM actuals double as the Android path where possible; iOS actuals are compile-verified (manual test on hardware comes in Plan 3).

**Tech Stack:** Kotlin Multiplatform (jvm + android + iosArm64 + iosSimulatorArm64), Wire (protobuf codegen), kotlinx-coroutines, Kermit (logging), BouncyCastle bcpkix (JVM/Android cert generation only), kotlin-test + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-08-11-android-tv-remote-design.md`. Plans 2 (domain/data) and 3 (UI/apps) follow after this plan lands.

## Global Constraints

- Branch: `feat/protocol-core` off `main`. WIP commits per task, subject format `[protocol] ...` (bracketed tag first). **No `Co-Authored-By` trailer, no "Generated with Claude Code" footer, ever.** Squash-merge to `main` only at plan completion; never push.
- All dependency versions pinned to **latest stable** in `gradle/libs.versions.toml` via `scripts/latest-versions.sh` (Task 1) — never from memory, no alpha/beta/rc.
- Project config (JDK, min/compile SDK) lives in `gradle.properties`, not the version catalog.
- No Ktor, no Room, no Coil anywhere in this plan. HTTP does not exist in this app.
- Logging: Kermit tagged loggers only — tags `Pairing`, `Session`, `Mdns`. No `println`.
- `:protocol` must not depend on `:domain` or any app module (they don't exist yet; keep it that way).
- Protocol constants: pairing port **6467**, session port **6466**, mDNS service type `_androidtvremote2._tcp`.
- The reverse-engineered protos are already checked in at `resources/proto/pairingmessage.proto` and `resources/proto/remotemessage.proto` (source: louis49/androidtv-remote, MIT). Do not edit them.
- Every new module gets a `README.md` (what it is, what it depends on).

---

### Task 1: Gradle scaffold + version pinning

**Files:**
- Create: `scripts/latest-versions.sh`, `gradle/libs.versions.toml`, `gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`, `.gitignore`, `ARCHITECTURE.md`
- Create: Gradle wrapper (`gradlew`, `gradle/wrapper/*`)

**Interfaces:**
- Consumes: nothing (first task)
- Produces: a building Gradle root with a version catalog whose aliases later tasks reference: `libs.plugins.kotlinMultiplatform`, `libs.plugins.androidLibrary`, `libs.plugins.wire`, `libs.kotlinx.coroutines.core`, `libs.kotlinx.coroutines.test`, `libs.kermit`, `libs.bouncycastle.bcpkix`, `libs.wire.runtime`

- [ ] **Step 1: Create branch**

```bash
git switch -c feat/protocol-core main
```

- [ ] **Step 2: Write the version-pinning script**

`scripts/latest-versions.sh` (mark executable). It queries live registry metadata; stable-only filter strips pre-releases:

```bash
#!/usr/bin/env bash
# Prints latest stable versions for Dpad dependencies from live registry metadata.
set -euo pipefail

central() { # groupPath artifact
  curl -sf "https://repo1.maven.org/maven2/$1/$2/maven-metadata.xml" \
    | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' \
    | grep -Ev '(alpha|beta|rc|RC|M[0-9]|dev|eap|snapshot|SNAPSHOT)' | tail -1
}
google() { # groupPath (e.g. com/android/library)
  curl -sf "https://dl.google.com/dl/android/maven2/$1/group-index.xml"
}

echo "kotlin                = $(central org/jetbrains/kotlin kotlin-gradle-plugin)"
echo "agp                   = $(google com/android | grep -o 'library versions="[^"]*"' | tr ',' '\n' | grep -Ev 'alpha|beta|rc' | tail -1)"
echo "wire                  = $(central com/squareup/wire wire-runtime)"
echo "kotlinx-coroutines    = $(central org/jetbrains/kotlinx kotlinx-coroutines-core)"
echo "kermit                = $(central co/touchlab kermit)"
echo "bcpkix                = $(central org/bouncycastle bcpkix-jdk18on)"
```

- [ ] **Step 3: Run it and capture output**

Run: `chmod +x scripts/latest-versions.sh && scripts/latest-versions.sh`
Expected: one line per dependency with a concrete stable version. If the `agp` line comes out garbled, read `https://dl.google.com/dl/android/maven2/com/android/library/group-index.xml` manually and take the highest stable. Use these exact values in the next step.

- [ ] **Step 4: Write the catalog and project config**

`gradle/libs.versions.toml` — fill `<pinned>` with Step 3 output (this is the one permitted "fill-in": values come from the live run, not memory):

```toml
[versions]
kotlin = "<pinned>"
agp = "<pinned>"
wire = "<pinned>"
kotlinx-coroutines = "<pinned>"
kermit = "<pinned>"
bcpkix = "<pinned>"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
wire-runtime = { module = "com.squareup.wire:wire-runtime", version.ref = "wire" }
bouncycastle-bcpkix = { module = "org.bouncycastle:bcpkix-jdk18on", version.ref = "bcpkix" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
wire = { id = "com.squareup.wire", version.ref = "wire" }
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g
android.useAndroidX=true
dpad.jdk=21
dpad.minSdk=26
dpad.compileSdk=36
```

(If Step 3's AGP requires a newer compileSdk, bump `dpad.compileSdk` to what it needs.)

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "Dpad"
include(":protocol")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.wire) apply false
}
```

`.gitignore`:

```
.gradle/
build/
local.properties
.kotlin/
*.xcodeproj
.DS_Store
```

`ARCHITECTURE.md` — seed with the module table from the spec's Architecture section (copy it) and a note that only `:protocol` exists yet.

- [ ] **Step 5: Generate the wrapper**

Run: `gradle wrapper` (any installed Gradle ≥ 8.x; `brew install gradle` if absent), then `./gradlew help`
Expected: `BUILD SUCCESSFUL`. (`:protocol` isn't created yet — if `include(":protocol")` makes `help` fail, comment the include out, run, restore it; Task 2 makes it real.)

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "[protocol] Gradle scaffold, version catalog pinned from live metadata"
```

---

### Task 2: `:protocol` module with Wire codegen

**Files:**
- Create: `protocol/build.gradle.kts`, `protocol/README.md`
- Create: `protocol/src/commonMain/proto/` (symlink-free copies of the two protos)
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/ProtoRoundTripTest.kt`

**Interfaces:**
- Consumes: catalog aliases from Task 1
- Produces: Wire-generated types in package `pairing` (`PairingMessage`, `PairingRequest`, `PairingOption`, `PairingConfiguration`, `PairingSecret`, …) and `remote` (`RemoteMessage`, `RemoteConfigure`, `RemoteDeviceInfo`, `RemoteSetActive`, `RemoteKeyInject`, `RemoteKeyCode`, `RemoteDirection`, `RemotePingRequest`, `RemotePingResponse`, `RemoteAppLinkLaunchRequest`, `RemoteSetVolumeLevel`); all later tasks use `.encode()` / `.ADAPTER.decode(bytes)`

- [ ] **Step 1: Module build file**

`protocol/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.wire)
}

kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // JVM and Android share actuals via this intermediate source set
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)
        jvmSharedMain.dependencies {
            implementation(libs.bouncycastle.bcpkix)
        }
    }
}

android {
    namespace = "com.dgmltn.dpad.protocol"
    compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
    defaultConfig { minSdk = providers.gradleProperty("dpad.minSdk").get().toInt() }
}

wire {
    kotlin {}
    sourcePath { srcDir("src/commonMain/proto") }
}
```

- [ ] **Step 2: Copy protos into the Wire source path**

```bash
mkdir -p protocol/src/commonMain/proto
cp resources/proto/pairingmessage.proto resources/proto/remotemessage.proto protocol/src/commonMain/proto/
```

(`resources/proto/` stays the canonical reference copy per the spec; the build reads the copy inside the module. Wire may reject the `UNRECOGNIZED = -1` enum entries — if it does, this is the one permitted proto edit: delete those lines in the *module copies only*, leaving `resources/proto/` untouched.)

`protocol/README.md`: "Standalone Android TV Remote protocol v2 implementation (pairing port 6467, session port 6466). Depends on Wire runtime, coroutines, Kermit; BouncyCastle on JVM/Android for cert generation. No app-module dependencies."

- [ ] **Step 3: Write the failing round-trip test**

```kotlin
package com.dgmltn.dpad.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import pairing.PairingMessage
import pairing.PairingRequest
import remote.RemoteKeyCode
import remote.RemoteKeyInject
import remote.RemoteDirection
import remote.RemoteMessage

class ProtoRoundTripTest {
    @Test fun pairingRequestRoundTrips() {
        val msg = PairingMessage(
            protocol_version = 2,
            status = PairingMessage.Status.STATUS_OK,
            pairing_request = PairingRequest(client_name = "Dpad", service_name = "com.dgmltn.dpad"),
        )
        val decoded = PairingMessage.ADAPTER.decode(msg.encode())
        assertEquals(msg, decoded)
    }

    @Test fun keyInjectRoundTrips() {
        val msg = RemoteMessage(
            remote_key_inject = RemoteKeyInject(
                key_code = RemoteKeyCode.KEYCODE_DPAD_UP,
                direction = RemoteDirection.SHORT,
            ),
        )
        assertEquals(msg, RemoteMessage.ADAPTER.decode(msg.encode()))
    }
}
```

- [ ] **Step 4: Run tests — expect compile failure first, then green**

Run: `./gradlew :protocol:jvmTest`
Expected: first run fails until Wire generates sources; after fixing any enum-entry issue from Step 2, `BUILD SUCCESSFUL` with both tests passing. If Wire's generated field names differ from the snake_case guesses above (Wire keeps proto snake_case for Kotlin properties), correct the *test* to the generated names — check `protocol/build/generated/`.

- [ ] **Step 5: Verify iOS + Android targets compile**

Run: `./gradlew :protocol:compileKotlinIosSimulatorArm64 :protocol:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "[protocol] Module with Wire codegen from reverse-engineered protos"
```

---

### Task 3: Varint framing over `BytePipe`

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/transport/BytePipe.kt`
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/transport/Framing.kt`
- Test: `protocol/src/commonTest/kotlin/com/dgmltn/dpad/protocol/transport/FramingTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:

```kotlin
interface BytePipe {
    /** Reads 1..max bytes; throws EofException when the peer closed. */
    suspend fun read(max: Int): ByteArray
    suspend fun write(bytes: ByteArray)
    fun close()
}
class EofException(message: String = "stream closed") : Exception(message)

fun encodeVarint(value: Int): ByteArray
suspend fun BytePipe.readVarint(): Int
suspend fun BytePipe.writeFrame(payload: ByteArray)   // varint length + payload
suspend fun BytePipe.readFrame(maxLength: Int = 1 shl 16): ByteArray
```

Both protocol ports frame every protobuf message with a protobuf varint length prefix.

- [ ] **Step 1: Write the failing tests (with an in-memory pipe test double)**

```kotlin
package com.dgmltn.dpad.protocol.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** In-memory BytePipe: what you write to one end, the other reads. Reused by later tasks. */
class InMemoryPipe : BytePipe {
    val incoming = Channel<Byte>(Channel.UNLIMITED)   // feed reads
    val outgoing = mutableListOf<Byte>()              // captures writes
    override suspend fun read(max: Int): ByteArray {
        val first = incoming.receiveCatching().getOrNull() ?: throw EofException()
        val out = mutableListOf(first)
        while (out.size < max) {
            val next = incoming.tryReceive().getOrNull() ?: break
            out.add(next)
        }
        return out.toByteArray()
    }
    override suspend fun write(bytes: ByteArray) { outgoing.addAll(bytes.toList()) }
    override fun close() { incoming.close() }
    suspend fun feed(bytes: ByteArray) = bytes.forEach { incoming.send(it) }
}

class FramingTest {
    @Test fun varintSingleByte() { assertContentEquals(byteArrayOf(0x05), encodeVarint(5)) }
    @Test fun varintMultiByte() { assertContentEquals(byteArrayOf(0xAC.toByte(), 0x02), encodeVarint(300)) }

    @Test fun frameRoundTrips() = runTest {
        val pipe = InMemoryPipe()
        val payload = ByteArray(300) { it.toByte() }
        pipe.writeFrame(payload)
        pipe.feed(pipe.outgoing.toByteArray())
        assertContentEquals(payload, pipe.readFrame())
    }

    @Test fun readFrameRejectsOversize() = runTest {
        val pipe = InMemoryPipe()
        pipe.feed(encodeVarint(1 shl 20))
        assertFailsWith<FramingException> { pipe.readFrame(maxLength = 1 shl 16) }
    }

    @Test fun readFrameThrowsEofMidFrame() = runTest {
        val pipe = InMemoryPipe()
        pipe.feed(byteArrayOf(0x05, 0x01))  // promises 5 bytes, delivers 1
        pipe.close()
        assertFailsWith<EofException> { pipe.readFrame() }
    }
}
```

Add `class FramingException(message: String) : Exception(message)` to the Produces block above — it lives in `Framing.kt`.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :protocol:jvmTest --tests '*FramingTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`Framing.kt`:

```kotlin
package com.dgmltn.dpad.protocol.transport

class FramingException(message: String) : Exception(message)

fun encodeVarint(value: Int): ByteArray {
    require(value >= 0)
    var v = value
    val out = mutableListOf<Byte>()
    do {
        val byte = v and 0x7F
        v = v ushr 7
        out.add(((if (v != 0) byte or 0x80 else byte)).toByte())
    } while (v != 0)
    return out.toByteArray()
}

suspend fun BytePipe.readVarint(): Int {
    var shift = 0
    var result = 0
    while (shift < 32) {
        val byte = readExactly(1)[0].toInt() and 0xFF
        result = result or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0) return result
        shift += 7
    }
    throw FramingException("varint too long")
}

suspend fun BytePipe.readExactly(count: Int): ByteArray {
    val out = ByteArray(count)
    var have = 0
    while (have < count) {
        val chunk = read(count - have)
        chunk.copyInto(out, have)
        have += chunk.size
    }
    return out
}

suspend fun BytePipe.writeFrame(payload: ByteArray) = write(encodeVarint(payload.size) + payload)

suspend fun BytePipe.readFrame(maxLength: Int = 1 shl 16): ByteArray {
    val length = readVarint()
    if (length > maxLength) throw FramingException("frame of $length exceeds $maxLength")
    return readExactly(length)
}
```

`BytePipe.kt` holds the interface + `EofException` exactly as in Produces.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :protocol:jvmTest --tests '*FramingTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[protocol] Varint framing over BytePipe"
```

---

### Task 4: `sha256` seam + pairing secret computation

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/crypto/Sha256.kt` (expect)
- Create: `protocol/src/jvmSharedMain/kotlin/com/dgmltn/dpad/protocol/crypto/Sha256.jvmShared.kt` (actual)
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/pairing/PairingSecret.kt`
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/pairing/PairingSecretTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:

```kotlin
expect fun sha256(data: ByteArray): ByteArray

/** Strips leading 0x00 sign bytes (BigInteger-style magnitudes must match the TV's). */
fun ByteArray.stripLeadingZeros(): ByteArray

data class RsaPublicParams(val modulus: ByteArray, val exponent: ByteArray)

/**
 * code: the 6-hex-char code from the TV screen. chars[0..1] = check byte, chars[2..5] = nonce.
 * Returns null if the check byte doesn't match (user typo) — callers show "wrong code".
 */
fun computePairingSecret(client: RsaPublicParams, server: RsaPublicParams, code: String): ByteArray?
```

(iOS `sha256` actual arrives in Task 10; until then iOS compilation of this file's expect is satisfied only at link time — that's fine for `compileKotlinIos*` checks.) **Correction:** expect declarations without actuals fail compilation. Create the iOS actual stub **now** in `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/crypto/Sha256.ios.kt` with `actual fun sha256(data: ByteArray): ByteArray = TODO("Task 10")` and replace it in Task 10. Add `iosMain` to `protocol/build.gradle.kts` sourceSets only if the default hierarchy hasn't created it (default hierarchy does).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dgmltn.dpad.protocol.pairing

import com.dgmltn.dpad.protocol.crypto.sha256
import com.dgmltn.dpad.protocol.crypto.stripLeadingZeros
import java.security.MessageDigest
import kotlin.test.*

class PairingSecretTest {
    private val client = RsaPublicParams(
        modulus = byteArrayOf(0x00, 0x7F, 0x33, 0x21),  // note leading zero to strip
        exponent = byteArrayOf(0x01, 0x00, 0x01),
    )
    private val server = RsaPublicParams(
        modulus = byteArrayOf(0x55, 0x44, 0x33),
        exponent = byteArrayOf(0x01, 0x00, 0x01),
    )

    private fun oracle(nonce: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(
            byteArrayOf(0x7F, 0x33, 0x21) + byteArrayOf(0x01, 0x00, 0x01) +
            byteArrayOf(0x55, 0x44, 0x33) + byteArrayOf(0x01, 0x00, 0x01) +
            nonce
        )

    @Test fun stripsLeadingZeros() =
        assertContentEquals(byteArrayOf(0x7F, 0x33), byteArrayOf(0x00, 0x00, 0x7F, 0x33).stripLeadingZeros())

    @Test fun sha256MatchesJdk() =
        assertContentEquals(
            MessageDigest.getInstance("SHA-256").digest("dpad".encodeToByteArray()),
            sha256("dpad".encodeToByteArray()),
        )

    @Test fun secretMatchesOracleWhenCheckByteCorrect() {
        val nonce = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val hash = oracle(nonce)
        val code = "%02x".format(hash[0]) + "abcd"
        assertContentEquals(hash, computePairingSecret(client, server, code))
    }

    @Test fun returnsNullOnWrongCheckByte() {
        val nonce = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val hash = oracle(nonce)
        val wrong = "%02x".format((hash[0] + 1).toByte()) + "abcd"
        assertNull(computePairingSecret(client, server, wrong))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :protocol:jvmTest --tests '*PairingSecretTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`Sha256.kt`: the expect + `stripLeadingZeros`:

```kotlin
package com.dgmltn.dpad.protocol.crypto

expect fun sha256(data: ByteArray): ByteArray

fun ByteArray.stripLeadingZeros(): ByteArray {
    val first = indexOfFirst { it != 0.toByte() }
    return if (first <= 0) (if (first == 0) this else byteArrayOf()) else copyOfRange(first, size)
}
```

`Sha256.jvmShared.kt`:

```kotlin
package com.dgmltn.dpad.protocol.crypto

import java.security.MessageDigest

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
```

`PairingSecret.kt`:

```kotlin
package com.dgmltn.dpad.protocol.pairing

import com.dgmltn.dpad.protocol.crypto.sha256
import com.dgmltn.dpad.protocol.crypto.stripLeadingZeros

data class RsaPublicParams(val modulus: ByteArray, val exponent: ByteArray)

fun computePairingSecret(client: RsaPublicParams, server: RsaPublicParams, code: String): ByteArray? {
    if (code.length != 6) return null
    val bytes = code.chunked(2).map { it.toIntOrNull(16) ?: return null }.map { it.toByte() }
    val checkByte = bytes[0]
    val nonce = byteArrayOf(bytes[1], bytes[2])
    val hash = sha256(
        client.modulus.stripLeadingZeros() + client.exponent.stripLeadingZeros() +
        server.modulus.stripLeadingZeros() + server.exponent.stripLeadingZeros() + nonce
    )
    return if (hash[0] == checkByte) hash else null
}
```

Also add the iOS `TODO` actual stub described in Produces.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :protocol:jvmTest --tests '*PairingSecretTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[protocol] sha256 seam and pairing secret computation"
```

---

### Task 5: `ClientIdentity` + JVM/Android generator (BouncyCastle)

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/crypto/ClientIdentity.kt` (types + expect)
- Create: `protocol/src/jvmSharedMain/kotlin/com/dgmltn/dpad/protocol/crypto/ClientIdentity.jvmShared.kt` (actual)
- Create: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/crypto/ClientIdentity.ios.kt` (`TODO("Task 10")` stub)
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/crypto/ClientIdentityTest.kt`

**Interfaces:**
- Consumes: `RsaPublicParams` (Task 4)
- Produces:

```kotlin
/** One self-signed RSA identity; the client cert is the pairing credential. PEMs are what Plan 2 persists. */
data class ClientIdentity(
    val certificatePem: String,     // -----BEGIN CERTIFICATE-----
    val privateKeyPem: String,      // -----BEGIN PRIVATE KEY----- (PKCS#8)
    val publicParams: RsaPublicParams,
)
expect object ClientIdentityGenerator {
    /** 2048-bit RSA, SHA256withRSA self-signed, CN=[commonName], 10-year validity. */
    fun generate(commonName: String): ClientIdentity
    /** Rebuild from persisted PEMs (validates they parse and match). */
    fun fromPem(certificatePem: String, privateKeyPem: String): ClientIdentity
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dgmltn.dpad.protocol.crypto

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.test.*

class ClientIdentityTest {
    private fun parse(pem: String): X509Certificate {
        val der = Base64.getMimeDecoder().decode(
            pem.lines().filterNot { it.startsWith("-----") }.joinToString("")
        )
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    @Test fun generatesValidSelfSignedCert() {
        val id = ClientIdentityGenerator.generate("dpad-test")
        val cert = parse(id.certificatePem)
        cert.verify(cert.publicKey)  // throws if not properly self-signed
        assertTrue(cert.subjectX500Principal.name.contains("dpad-test"))
    }

    @Test fun publicParamsMatchCertificate() {
        val id = ClientIdentityGenerator.generate("dpad-test")
        val cert = parse(id.certificatePem)
        val rsa = cert.publicKey as java.security.interfaces.RSAPublicKey
        assertEquals(rsa.modulus, BigInteger(1, id.publicParams.modulus))
        assertEquals(rsa.publicExponent, BigInteger(1, id.publicParams.exponent))
    }

    @Test fun roundTripsThroughPem() {
        val id = ClientIdentityGenerator.generate("dpad-test")
        val restored = ClientIdentityGenerator.fromPem(id.certificatePem, id.privateKeyPem)
        assertEquals(id.publicParams.modulus.toList(), restored.publicParams.modulus.toList())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :protocol:jvmTest --tests '*ClientIdentityTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the jvmShared actual**

```kotlin
package com.dgmltn.dpad.protocol.crypto

import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

actual object ClientIdentityGenerator {
    actual fun generate(commonName: String): ClientIdentity {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Principal("CN=$commonName")
        val now = System.currentTimeMillis()
        val cert = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000),
                Date(now + 10L * 365 * 86_400_000), name, keyPair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        )
        return ClientIdentity(
            certificatePem = pem("CERTIFICATE", cert.encoded),
            privateKeyPem = pem("PRIVATE KEY", keyPair.private.encoded),
            publicParams = (keyPair.public as RSAPublicKey).toParams(),
        )
    }

    actual fun fromPem(certificatePem: String, privateKeyPem: String): ClientIdentity {
        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(derOf(certificatePem).inputStream()) as java.security.cert.X509Certificate
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(derOf(privateKeyPem)))  // validates
        return ClientIdentity(certificatePem, privateKeyPem, (cert.publicKey as RSAPublicKey).toParams())
    }

    private fun RSAPublicKey.toParams() = RsaPublicParams(
        modulus = modulus.toByteArray(), exponent = publicExponent.toByteArray(),
    )
    private fun pem(label: String, der: ByteArray) =
        "-----BEGIN $label-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)}\n-----END $label-----\n"
    internal fun derOf(pem: String): ByteArray =
        Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
}
```

(Move `RsaPublicParams` from Task 4's file into `crypto/` if the import direction is cleaner — keep ONE definition; the canonical location after this task is `com.dgmltn.dpad.protocol.pairing.RsaPublicParams` imported here.) Also create the iOS stub actual with both functions `TODO("Task 10")`.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :protocol:jvmTest --tests '*ClientIdentityTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[protocol] ClientIdentity with BouncyCastle JVM/Android generator"
```

---

### Task 6: `TlsSocketFactory` JVM actual + TLS-level `FakeTvServer`

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/transport/TlsSocket.kt` (expect + types)
- Create: `protocol/src/jvmSharedMain/kotlin/com/dgmltn/dpad/protocol/transport/TlsSocket.jvmShared.kt`
- Create: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/transport/TlsSocket.ios.kt` (`TODO("Task 10")` stub)
- Create: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/fake/FakeTvServer.kt` (TLS plumbing only this task)
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/transport/TlsSocketTest.kt`

**Interfaces:**
- Consumes: `ClientIdentity`, `ClientIdentityGenerator` (Task 5), `BytePipe` (Task 3), `RsaPublicParams` (Task 4)
- Produces:

```kotlin
class TlsHandshakeRejectedException(cause: Throwable) : Exception(cause)  // → PairingRequired upstream

interface TlsConnection : BytePipe {
    val serverPublicParams: RsaPublicParams   // captured from the handshake's peer cert
}
expect class TlsSocketFactory(identity: ClientIdentity) {
    /** Trust-all client TLS. Throws TlsHandshakeRejectedException when the server rejects OUR cert. */
    suspend fun connect(host: String, port: Int): TlsConnection
}

// jvmTest — grows a protocol brain in Tasks 7/8; this task it only accepts TLS + echoes frames
class FakeTvServer(requireClientCert: Boolean) : AutoCloseable {
    val port: Int
    val lastClientCertParams: RsaPublicParams?   // set after a client connects
    val serverIdentity: ClientIdentity
    fun start(handler: suspend (BytePipe) -> Unit)
    override fun close()
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dgmltn.dpad.protocol.transport

import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.fake.FakeTvServer
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TlsSocketTest {
    private val clientIdentity = ClientIdentityGenerator.generate("dpad-client")

    @Test fun handshakesAndEchoesFrames() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.start { pipe -> pipe.writeFrame(pipe.readFrame()) }   // echo one frame
            val conn = TlsSocketFactory(clientIdentity).connect("127.0.0.1", server.port)
            conn.writeFrame(byteArrayOf(1, 2, 3))
            assertContentEquals(byteArrayOf(1, 2, 3), conn.readFrame())
            // both sides captured each other's certs
            assertContentEquals(server.serverIdentity.publicParams.modulus, conn.serverPublicParams.modulus)
            assertContentEquals(clientIdentity.publicParams.modulus, server.lastClientCertParams!!.modulus)
            conn.close()
        }
    }

    @Test fun rejectionSurfacesAsHandshakeRejected() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.rejectClientCerts = true
            server.start { }
            assertFailsWith<TlsHandshakeRejectedException> {
                TlsSocketFactory(clientIdentity).connect("127.0.0.1", server.port)
            }
        }
    }
}
```

Add `var rejectClientCerts: Boolean` to FakeTvServer's Produces block.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :protocol:jvmTest --tests '*TlsSocketTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement JVM actual**

`TlsSocket.jvmShared.kt` — key points, complete:

```kotlin
package com.dgmltn.dpad.protocol.transport

import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.pairing.RsaPublicParams
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class TlsSocketFactory actual constructor(private val identity: ClientIdentity) {
    actual suspend fun connect(host: String, port: Int): TlsConnection = withContext(Dispatchers.IO) {
        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(ClientIdentityGenerator.derOf(identity.certificatePem).inputStream())
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(ClientIdentityGenerator.derOf(identity.privateKeyPem)))
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null); setKeyEntry("dpad", key, CharArray(0), arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, CharArray(0)) }
        var peer: X509Certificate? = null
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { peer = chain[0] }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trustAll), null) }
        val socket = context.socketFactory.createSocket(host, port) as SSLSocket
        try {
            socket.startHandshake()
        } catch (e: SSLException) {
            socket.close(); throw TlsHandshakeRejectedException(e)
        }
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
        val n = socket.inputStream.read(buf, 0, max)
        if (n < 0) throw EofException() else buf.copyOf(n)
    }
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        socket.outputStream.write(bytes); socket.outputStream.flush()
    }
    override fun close() = runCatching { socket.close() }.let { }
}
```

Note: a server that *rejects* our client cert typically fails us mid-handshake or on first read — in `startHandshake` catch we get it directly. If the rejection arrives as `SSLException` on first read in Task 8's session connect, wrap it there identically.

`FakeTvServer.kt` (jvmTest): `SSLServerSocket` from an `SSLContext` built the same way from a generated `serverIdentity`; `needClientAuth = requireClientCert`; trust manager records `lastClientCertParams` from `checkClientTrusted` (and throws `CertificateException` when `rejectClientCerts`). `start(handler)` accepts one connection at a time on a daemon thread, wraps it in the same stream-backed `BytePipe`, and invokes `handler` inside `runBlocking`. Port 0 (ephemeral) — expose the bound `port`.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :protocol:jvmTest --tests '*TlsSocketTest*'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[protocol] Client-cert TLS socket seam with JVM actual and FakeTvServer"
```

---

### Task 7: `PairingClient` + fake pairing server

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/pairing/PairingClient.kt`
- Modify: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/fake/FakeTvServer.kt` (add pairing behavior)
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/pairing/PairingClientTest.kt`

**Interfaces:**
- Consumes: `TlsSocketFactory`/`TlsConnection` (6), `computePairingSecret`/`RsaPublicParams` (4), framing (3), Wire `pairing.*` types (2), `ClientIdentity` (5)
- Produces:

```kotlin
sealed interface PairingEvent {
    data object WaitingForCode : PairingEvent               // TV is now showing the code
    data object Paired : PairingEvent
    data class Failed(val reason: PairingFailure) : PairingEvent
}
enum class PairingFailure { WRONG_CODE, REJECTED, CONNECTION_LOST, TIMEOUT }

class PairingClient(
    private val factory: TlsSocketFactory,
    private val identity: ClientIdentity,
    private val clientName: String = "Dpad",
    private val serviceName: String = "com.dgmltn.dpad",
) {
    val events: Flow<PairingEvent>
    /** Connects to [host]:6467, runs request→option→configuration, emits WaitingForCode. */
    suspend fun start(host: String, port: Int = 6467)
    /** Computes + sends PairingSecret; emits Paired or Failed(WRONG_CODE). */
    suspend fun submitCode(code: String)
    fun cancel()
}
```

Message sequence the client drives (each message wrapped in `PairingMessage(protocol_version = 2, status = STATUS_OK, ...)`, varint-framed):
1. → `pairing_request(client_name, service_name)` ; ← `pairing_request_ack`
2. → `pairing_option(input_encodings = [HEXADECIMAL/6], preferred_role = ROLE_TYPE_INPUT)` ; ← `pairing_option`
3. → `pairing_configuration(encoding = HEXADECIMAL/6, client_role = ROLE_TYPE_INPUT)` ; ← `pairing_configuration_ack` → emit `WaitingForCode`
4. after `submitCode`: → `pairing_secret(computePairingSecret(...))` ; ← `pairing_secret_ack` → emit `Paired`
Any reply with `status != STATUS_OK` → `Failed(REJECTED)` (or `WRONG_CODE` after a secret). `computePairingSecret` returning null → `Failed(WRONG_CODE)` without sending. `EofException` → `CONNECTION_LOST`. 10s read timeout (`withTimeout`) → `TIMEOUT`. Log every transition, tag `Pairing`.

- [ ] **Step 1: Extend FakeTvServer with a scripted pairing peer**

Add `fun startPairingServer(showCode: (String) -> Unit)`: accepts a TLS connection, answers the sequence above, computes the real expected code from both certs plus a random 2-byte nonce (reusing `computePairingSecret` in reverse: build `code = checkByteHex + nonceHex` where checkByte comes from the same sha256), passes it to `showCode`, then verifies the received `pairing_secret` and answers `pairing_secret_ack` (or `status = STATUS_BAD_SECRET` on mismatch).

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.dgmltn.dpad.protocol.pairing

import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.fake.FakeTvServer
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PairingClientTest {
    private val identity = ClientIdentityGenerator.generate("dpad-client")

    @Test fun fullPairingFlowSucceeds() = runTest {
        FakeTvServer(requireClientCert = false).use { server ->
            var shownCode: String? = null
            server.startPairingServer(showCode = { shownCode = it })
            val client = PairingClient(TlsSocketFactory(identity), identity)
            launch { client.start("127.0.0.1", server.port) }
            client.events.filterIsInstance<PairingEvent.WaitingForCode>().first()
            client.submitCode(shownCode!!)
            assertIs<PairingEvent.Paired>(client.events.first { it !is PairingEvent.WaitingForCode })
        }
    }

    @Test fun typoCodeFailsWithWrongCode() = runTest {
        FakeTvServer(requireClientCert = false).use { server ->
            server.startPairingServer(showCode = { })
            val client = PairingClient(TlsSocketFactory(identity), identity)
            launch { client.start("127.0.0.1", server.port) }
            client.events.filterIsInstance<PairingEvent.WaitingForCode>().first()
            client.submitCode("000000")   // check byte almost certainly wrong
            val failed = client.events.filterIsInstance<PairingEvent.Failed>().first()
            assertEquals(PairingFailure.WRONG_CODE, failed.reason)
        }
    }
}
```

(`events` is a `MutableSharedFlow(replay = 8)` internally so late collectors see history; that makes these assertions race-free.)

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :protocol:jvmTest --tests '*PairingClientTest*'`
Expected: FAIL.

- [ ] **Step 4: Implement PairingClient**

Straight-line suspend implementation over the connection; hold the `TlsConnection` between `start` and `submitCode`; every await wrapped in `withTimeout(10_000)`; map exceptions to `Failed` events per the table in Produces; `cancel()` closes the connection.

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :protocol:jvmTest --tests '*PairingClientTest*'`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "[protocol] Pairing state machine with scripted fake-server tests"
```

---

### Task 8: `RemoteSession` + fake session server

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/session/RemoteSession.kt`
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/session/SessionState.kt`
- Modify: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/fake/FakeTvServer.kt` (add session behavior)
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/session/RemoteSessionTest.kt`

**Interfaces:**
- Consumes: everything prior
- Produces (this is the API Plan 2's `RemoteController` impl wraps):

```kotlin
data class HostAddress(val host: String, val port: Int = 6466)
data class VolumeState(val level: Int, val max: Int, val muted: Boolean)

sealed interface SessionState {
    data object Disconnected : SessionState
    data object Connecting : SessionState
    data object Connected : SessionState
    data object PairingRequired : SessionState   // TLS rejected our cert
}

class RemoteSession(
    private val scope: CoroutineScope,
    private val factory: TlsSocketFactory,
    /** Re-invoked before every (re)connect attempt — Plan 2 plugs mDNS re-resolution in here. */
    private val resolveHost: suspend () -> HostAddress,
    private val clientModel: String = "Dpad",
    private val backoffMillis: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 15_000),
) {
    val state: StateFlow<SessionState>
    val volume: StateFlow<VolumeState?>
    fun connect()      // idempotent; starts the connect/reconnect loop
    fun disconnect()   // stops the loop, state -> Disconnected
    fun sendKey(keyCode: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT)
    fun launchApp(appLinkUrl: String)
}
```

Behavior contract:
- On connect: read `RemoteMessage` frames; when `remote_configure` arrives, reply `RemoteMessage(remote_configure = RemoteConfigure(code1 = 622, device_info = RemoteDeviceInfo(model = clientModel, vendor = "dgmltn", unknown1 = 1, unknown2 = "1", package_name = "com.dgmltn.dpad", app_version = "1.0")))`; when `remote_set_active` arrives, reply `RemoteMessage(remote_set_active = RemoteSetActive(active = 622))`, then state → `Connected`.
- `remote_ping_request(val1)` → immediately write `remote_ping_response(val1)`, forever, from the read loop.
- `remote_set_volume_level` → update `volume` StateFlow from `volume_level`/`volume_max`/`volume_muted`.
- `sendKey`/`launchApp` while not `Connected`: **drop silently** (spec: never queue) with a `Session`-tagged log.
- Read-loop `EofException`/`SSLException` → `Connecting` + backoff schedule; each retry calls `resolveHost()` fresh; backoff resets on successful connect. `TlsHandshakeRejectedException` → `PairingRequired`, loop stops.
- Sends go through a `Channel(UNLIMITED)` drained by a writer coroutine — callers never block.

- [ ] **Step 1: Extend FakeTvServer with a scripted session peer**

`fun startSessionServer()`: on accept, send `remote_configure`, await client's configure reply, send `remote_set_active`... (per contract above; the real TV expects the client's replies — mirror the contract). Expose `val receivedMessages: MutableList<RemoteMessage>` and helpers `sendPing(v: Int)`, `sendVolume(level: Int, max: Int, muted: Boolean)`, `dropConnection()`.

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.dgmltn.dpad.protocol.session

import app.cash.turbine.test  // add libs.turbine to catalog + jvmTest deps if not present; pin via Step-3 method of Task 1
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.fake.FakeTvServer
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.test.runTest
import remote.RemoteKeyCode
import kotlin.test.*

class RemoteSessionTest {
    private val identity = ClientIdentityGenerator.generate("dpad-client")

    private fun session(server: FakeTvServer, scope: kotlinx.coroutines.CoroutineScope) = RemoteSession(
        scope = scope, factory = TlsSocketFactory(identity),
        resolveHost = { HostAddress("127.0.0.1", server.port) },
        backoffMillis = listOf(10, 20),   // fast for tests
    )

    @Test fun handshakesToConnected() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
        }
    }

    @Test fun answersPingAndTracksVolume() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            server.sendPing(42)
            server.awaitPingResponse(42)          // fake records ping responses; suspends until seen
            server.sendVolume(level = 7, max = 100, muted = false)
            s.volume.test { awaitItemUntil { it?.level == 7 && it.max == 100 } }
        }
    }

    @Test fun keySentWhenConnectedDroppedWhenNot() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.sendKey(RemoteKeyCode.KEYCODE_DPAD_UP)      // before connect: dropped
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            s.sendKey(RemoteKeyCode.KEYCODE_DPAD_DOWN)
            server.awaitKeyInject(RemoteKeyCode.KEYCODE_DPAD_DOWN)
            assertTrue(server.receivedMessages.none { it.remote_key_inject?.key_code == RemoteKeyCode.KEYCODE_DPAD_UP })
        }
    }

    @Test fun reconnectsAfterDrop() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test {
                awaitItemUntil { it == SessionState.Connected }
                server.dropConnection()
                awaitItemUntil { it == SessionState.Connecting }
                awaitItemUntil { it == SessionState.Connected }   // fake auto-accepts next connection
            }
        }
    }

    @Test fun certRejectionBecomesPairingRequired() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.rejectClientCerts = true
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.PairingRequired } }
        }
    }
}

/** Awaits items until predicate matches (Room-style condition awaiting, per Doug's test prefs). */
suspend fun <T> app.cash.turbine.TurbineTestContext<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) { val item = awaitItem(); if (predicate(item)) return item }
}
```

Add `awaitPingResponse(v: Int)` / `awaitKeyInject(code: RemoteKeyCode)` to the fake (Channel-backed). Add Turbine (`app.cash.turbine:turbine`) to the catalog, pinned live like every version.

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :protocol:jvmTest --tests '*RemoteSessionTest*'`
Expected: FAIL.

- [ ] **Step 4: Implement RemoteSession**

Single supervisor coroutine per `connect()`: loop { resolve → connect → handshake-replies → read loop }, with the send channel drained by a sibling coroutine writing frames; catch/dispatch per the behavior contract; real delays (tests pass short `backoffMillis`, so no virtual-time tricks needed).

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :protocol:jvmTest --tests '*RemoteSessionTest*'`
Expected: PASS (5 tests).

- [ ] **Step 6: Run the whole module suite**

Run: `./gradlew :protocol:jvmTest`
Expected: PASS — everything from Tasks 2-8.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "[protocol] Remote session state machine: handshake, keepalive, keys, reconnect"
```

---

### Task 9: `MdnsBrowser` — Android + JVM actuals

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/discovery/MdnsBrowser.kt`
- Create: `protocol/src/androidMain/kotlin/com/dgmltn/dpad/protocol/discovery/MdnsBrowser.android.kt`
- Create: `protocol/src/jvmMain/kotlin/com/dgmltn/dpad/protocol/discovery/MdnsBrowser.jvm.kt`
- Create: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/discovery/MdnsBrowser.ios.kt` (`TODO("Task 10")` stub)

**Interfaces:**
- Consumes: nothing protocol-internal
- Produces:

```kotlin
data class DiscoveredTv(val name: String, val host: String, val port: Int)

/** Platform mDNS browse+resolve for _androidtvremote2._tcp. Flow emits the full current set on every change. */
expect class MdnsBrowser {
    fun discovered(): Flow<List<DiscoveredTv>>
}
```

Note: because `MdnsBrowser` needs an Android `Context`, the expect uses **constructor injection per platform** — `expect class MdnsBrowser` with no common constructor is invalid; instead declare:

```kotlin
expect class MdnsBrowser {
    fun discovered(): kotlinx.coroutines.flow.Flow<List<DiscoveredTv>>
}
```

with actual constructors differing per platform (`actual class MdnsBrowser(private val context: Context)` on Android; `actual class MdnsBrowser()` on JVM/iOS). Koin wires the right constructor in Plan 2.

- [ ] **Step 1: Common expect + JVM stub**

JVM actual returns `flowOf(emptyList())` — mDNS on desktop JVM is out of scope; it exists so `:protocol:jvmTest` compiles and Plan 2 fakes discovery at the repository level.

- [ ] **Step 2: Android actual**

`callbackFlow` wrapping `NsdManager.discoverServices("_androidtvremote2._tcp", PROTOCOL_DNS_SD, listener)`; each found service goes through `resolveService`; maintain a `MutableMap<String, DiscoveredTv>` keyed by service name, emit `values.toList()` on every add/remove/resolve; `awaitClose { nsdManager.stopServiceDiscovery(listener) }`. Log events with tag `Mdns`. (No unit test — Android actual is device-verified in Plan 3; this task is compile-verified.)

- [ ] **Step 3: Compile-verify all targets**

Run: `./gradlew :protocol:compileDebugKotlinAndroid :protocol:jvmTest`
Expected: BUILD SUCCESSFUL, tests still green.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "[protocol] MdnsBrowser seam with NsdManager Android actual"
```

---

### Task 10: iOS actuals (CommonCrypto, DER cert builder, NWConnection, NWBrowser)

**Files:**
- Modify: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/crypto/Sha256.ios.kt` (replace TODO)
- Create: `protocol/src/commonMain/kotlin/com/dgmltn/dpad/protocol/crypto/DerX509.kt` (pure-Kotlin minimal X.509 builder)
- Test: `protocol/src/jvmTest/kotlin/com/dgmltn/dpad/protocol/crypto/DerX509Test.kt`
- Modify: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/crypto/ClientIdentity.ios.kt` (replace TODO)
- Modify: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/transport/TlsSocket.ios.kt` (replace TODO)
- Modify: `protocol/src/iosMain/kotlin/com/dgmltn/dpad/protocol/discovery/MdnsBrowser.ios.kt` (replace TODO)

**Interfaces:**
- Consumes: all expects from Tasks 4-6, 9
- Produces: working iOS actuals; plus (commonMain, JVM-tested):

```kotlin
/** Builds an unsigned TBSCertificate DER for CN=[commonName] + given RSA public key,
 *  and assembles the final cert from a detached SHA256withRSA signature.
 *  Lets iOS self-sign via SecKeyCreateSignature without any Apple X.509-building API. */
object DerX509 {
    fun tbsCertificate(commonName: String, modulus: ByteArray, exponent: ByteArray,
                       serial: Long, notBeforeEpochSec: Long, notAfterEpochSec: Long): ByteArray
    fun assembleCertificate(tbs: ByteArray, signature: ByteArray): ByteArray
}
```

- [ ] **Step 1: Write the failing DerX509 test (JVM verifies iOS's path)**

```kotlin
package com.dgmltn.dpad.protocol.crypto

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import kotlin.test.*

class DerX509Test {
    @Test fun builtCertParsesAndVerifies() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val now = System.currentTimeMillis() / 1000
        val tbs = DerX509.tbsCertificate(
            "dpad-ios", pub.modulus.toByteArray(), pub.publicExponent.toByteArray(),
            serial = 7, notBeforeEpochSec = now - 60, notAfterEpochSec = now + 3_650L * 86_400,
        )
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(kp.private); update(tbs) }.sign()
        val der = DerX509.assembleCertificate(tbs, sig)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
        cert.verify(kp.public)
        assertTrue(cert.subjectX500Principal.name.contains("dpad-ios"))
        assertEquals(BigInteger(1, (cert.publicKey as RSAPublicKey).modulus.toByteArray().let {
            if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }), BigInteger(1, pub.modulus.toByteArray().let {
            if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }))
    }
}
```

- [ ] **Step 2: Run to verify failure, then implement DerX509**

Run: `./gradlew :protocol:jvmTest --tests '*DerX509Test*'` → FAIL.

Implement with small private helpers — `derLength(Int)`, `derSequence(vararg ByteArray)`, `derInteger(ByteArray)`, `derBitString(ByteArray)`, `derUtcTime(Long)`, `derOid(String)`, `derSet`, `derUtf8`, `derExplicit(tag, content)` — then:
- `tbsCertificate`: `SEQUENCE { [0] EXPLICIT version(2), serial, sigAlg(sha256WithRSAEncryption 1.2.840.113549.1.1.11 + NULL), issuer Name(CN), validity(UTCTime pair), subject Name(=issuer), subjectPublicKeyInfo(rsaEncryption OID + BIT STRING wrapping SEQUENCE{modulus, exponent}) }`
- `assembleCertificate`: `SEQUENCE { tbs, sigAlg, BIT STRING(signature) }`

Run again → PASS.

- [ ] **Step 3: iOS `sha256` actual**

```kotlin
package com.dgmltn.dpad.protocol.crypto

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256(data: ByteArray): ByteArray {
    val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { pinned ->
        out.usePinned { outPinned ->
            CC_SHA256(pinned.addressOf(0), data.size.toUInt(), outPinned.addressOf(0).reinterpret())
        }
    }
    return out
}
```

(If `platform.CoreCrypto` isn't exposed in this Kotlin version, add a cinterop def for `<CommonCrypto/CommonDigest.h>` — check `platform.*` availability first.)

- [ ] **Step 4: iOS `ClientIdentityGenerator` actual**

`generate`: `SecKeyCreateRandomKey(kSecAttrKeyTypeRSA, 2048)` → `SecKeyCopyExternalRepresentation` (PKCS#1 DER) → parse modulus/exponent with a tiny DER reader (add `DerX509.readRsaPkcs1(der): RsaPublicParams` to the common file, covered by extending Step 1's test) → build TBS via `DerX509` → sign via `SecKeyCreateSignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256, tbs)` → `assembleCertificate` → PEM-encode both (base64 the DER; wrap PKCS#1 key in a PKCS#8 envelope via `DerX509.wrapPkcs8(pkcs1): ByteArray`, also JVM-tested). `fromPem`: reverse.

- [ ] **Step 5: iOS `TlsSocketFactory` actual**

`NWConnection` with `NWProtocolTLS.Options`: `sec_protocol_options_set_verify_block` accepting everything but capturing the peer cert (`sec_trust_copy_ref` → `SecTrustGetCertificateAtIndex` → `SecCertificateCopyKey` → external representation → `DerX509.readRsaPkcs1`); client identity via `sec_protocol_options_set_local_identity(sec_identity_create(SecIdentity))` — build the `SecIdentity` by importing cert+key into the keychain (`SecItemAdd`) once. `read`/`write` bridge `nw_connection_receive`/`nw_connection_send` to suspend functions with `suspendCancellableCoroutine`. Handshake refusal → `TlsHandshakeRejectedException`.

- [ ] **Step 6: iOS `MdnsBrowser` actual**

`NWBrowser(descriptor = bonjour("_androidtvremote2._tcp", domain = null))` in a `callbackFlow`; on results-changed, resolve each endpoint's host/port via `NWEndpoint` copy; emit the full list; `awaitClose { browser.cancel() }`. Tag `Mdns`.

- [ ] **Step 7: Compile-verify iOS, run full suite**

Run: `./gradlew :protocol:compileKotlinIosArm64 :protocol:compileKotlinIosSimulatorArm64 :protocol:jvmTest`
Expected: BUILD SUCCESSFUL, all JVM tests green. (Functional iOS verification happens on hardware in Plan 3 — these actuals are the riskiest untested code in the project; keep them thin and mirror the JVM actuals' structure exactly.)

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "[protocol] iOS actuals: CommonCrypto, DER-built identity, NWConnection TLS, NWBrowser"
```

---

### Task 11: Docs + squash-merge

**Files:**
- Modify: `ARCHITECTURE.md` (mark `:protocol` as implemented, add mermaid graph)
- Modify: `protocol/README.md` (final API summary: `PairingClient`, `RemoteSession`, `MdnsBrowser`, seams)

- [ ] **Step 1: Update docs**

`ARCHITECTURE.md` gets: module list status column, plus

```mermaid
graph TD
  protocol[":protocol (implemented)"]
  domain[":domain (plan 2)"] --> protocol
```

(Correction: `:domain` will NOT depend on `:protocol` — `:data` will. Draw `data[":data (plan 2)"] --> protocol` instead; `:domain` stands alone.)

- [ ] **Step 2: Full verification**

Run: `./gradlew :protocol:jvmTest :protocol:compileDebugKotlinAndroid :protocol:compileKotlinIosArm64`
Expected: everything green. Fix anything red before proceeding — do not merge red.

- [ ] **Step 3: Squash-merge (do NOT push)**

```bash
git switch main
git merge --squash feat/protocol-core
git commit -m "[protocol] Android TV Remote v2 protocol library: pairing, session, discovery seams

Wire-generated protobufs from reverse-engineered protos, varint framing,
client-cert TLS seams (JVM/Android SSLSocket, iOS NWConnection), BouncyCastle
JVM cert generation with a pure-Kotlin DER builder for iOS, and a FakeTvServer
JVM test harness covering pairing, keepalive, key events, and reconnect."
git branch -D feat/protocol-core
```

Stop here. Ask Doug before any push. Plan 2 (domain/data) is written after this plan lands.

---

## Self-Review Notes (already applied)

- Spec coverage: pairing, session, all listed keycodes reachable via `sendKey`, app-link launch, volume push, reconnect/backoff, PairingRequired, mDNS seams — covered. Persistence, repositories, UI: Plans 2-3 by design.
- Type consistency: `RsaPublicParams` defined once (Task 4, canonical package noted in Task 5); `BytePipe`/`EofException` (Task 3) used by 6-8; `TlsHandshakeRejectedException` thrown in 6, consumed in 8; corrections embedded where a task text superseded an earlier sketch (Task 4 expect-stub note, Task 9 constructor note, Task 11 mermaid note).
- Placeholders: the only `<pinned>` values are filled from a live script run by design; iOS `TODO` stubs are tracked and all replaced in Task 10.
