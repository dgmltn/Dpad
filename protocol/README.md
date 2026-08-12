# `:protocol` — Android TV Remote v2 Library

Standalone Android TV Remote protocol v2 implementation (pairing port 6467, session port 6466). Depends on Wire runtime, coroutines, Kermit; BouncyCastle on JVM/Android for cert generation. No app-module dependencies. `jvm()`, `iosArm64()`, and `iosSimulatorArm64()` targets.

## Public API

### PairingClient
State machine for device pairing with out-of-band code submission:
- `events: Flow<PairingEvent>` — emits `PairingEvent.WaitingForCode`, `PairingEvent.Paired`, `PairingEvent.Failed(reason: PairingFailure)`
- `suspend fun start(host: String, port: Int = 6467)` — begin the pairing handshake over a fresh TLS connection
- `suspend fun submitCode(code: String)` — send user-entered pairing code (6 digits)
- `cancel()` — abort current pairing attempt

### RemoteSession
State machine for ongoing remote-control and keepalive over TLS. Host resolution and the cert-bearing TLS factory are injected via the constructor, not passed to `connect()`:
- `RemoteSession(scope: CoroutineScope, factory: TlsSocketFactory, resolveHost: suspend () -> HostAddress, clientModel: String = "Dpad", backoffMillis: List<Long> = ...)` — `factory` carries the client cert from successful pairing; `resolveHost` is re-invoked before every (re)connect attempt (Plan 2 plugs mDNS re-resolution in here)
- `state: StateFlow<SessionState>` — `Disconnected`, `Connecting`, `Connected`, `PairingRequired` (3+ consecutive TLS cert rejections)
- `volume: StateFlow<VolumeState?>` — current remote volume/mute state
- `fun connect()` — idempotent; starts the connect/reconnect loop (no arguments)
- `fun disconnect()` — close session and reset to Disconnected
- `fun sendKey(keyCode: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT)` — fire-and-forget; silently dropped when not `Connected`
- `fun launchApp(appLinkUrl: String)` — launch an app by app-link URL; same fire-and-forget semantics

### MdnsBrowser
mDNS discovery interface for local TV device discovery:
- `discovered(): Flow<List<DiscoveredTv>>` — emits updated list of discovered TVs; `DiscoveredTv` carries `ipAddress` and `serviceName`

### Client Identity Generation
- `ClientIdentityGenerator.generate()` — create a new RSA-2048 key pair and self-signed X.509 cert (uniquely identifies this client)
- Cert validity: 10 years from generation
- Used by pairing to prove client identity during certificate exchange

## Platform Seams

Three expect/actual seams allow platform-specific implementation while preserving the pairing/session/discovery contracts:

### TlsSocketFactory (expect/actual)
- **JVM/Android:** `javax.net.ssl.SSLSocket` with client-cert authentication, pinned to TLS 1.2
- **iOS:** `NWConnection` (Network.framework) with `tls_protocol_version_t .tlsProtocol12`

### SHA-256 Hash (expect/actual)
- **JVM/Android:** BouncyCastle or platform Cipher
- **iOS:** CommonCrypto via native Swift bridge

### DerX509 (expect/actual)
- **JVM/Android:** BouncyCastle X.509 encoder
- **iOS:** Pure-Kotlin DER builder (no BouncyCastle on iOS; KeyChain integration done in Plan 3)

### Platform Compilation Status
- **JVM:** Full implementation with FakeTvServer test harness; `./gradlew :protocol:jvmTest` — 34/34 passing
- **Android:** Compiles successfully; runtime testing in Plan 3 (emulator + device)
- **iOS (Arm64 + Simulator):** Compiles successfully; **compile-verified only** — awaiting device testing in Plan 3

## Carried-Forward Notes

Important facts discovered during implementation that affect downstream planning:

### RemoteSession Threading Model
`RemoteSession` must be driven from a **single-threaded-confined scope** (e.g., `viewModelScope` in Android, `Dispatchers.Main.immediate`, or equivalent iOS main-thread confinement). The generation guard's check-then-act atomic behavior is guaranteed only under cooperative/single-threaded scheduling. A genuinely multi-threaded caller (with multiple coroutines competing) would require a `Mutex` wrapper in addition to the `@GuardedBy` semantics.

### TLS Protocol Pinning to 1.2
TLS is **hardwired to version 1.2** (`TV_TLS_PROTOCOL` constant) to ensure synchronous client-cert rejection at the protocol handshake — TV devices reject unrecognized certificates immediately, not at the application layer. This must be **re-verified against real Android TV hardware in Plan 3** to confirm that:
1. Android TV devices accept TLS 1.2
2. Client-cert rejection is synchronous and observable
3. Fallback to re-pairing works as expected under rejection

### Android Build Configuration
- Plugin: `com.android.kotlin.multiplatform.library`
- Gradle compile task: `./gradlew :protocol:compileAndroidMain` (not `compileDebugKotlinAndroid` or other debug variants)
- This target produces Kotlin/Android interop binaries; JVM test runs separately via `:protocol:jvmTest`

### iOS Plan-3 Device-Testing Risks
The iOS implementation is compile-verified but untested on real devices. Known risk areas:
1. **Keychain/SecIdentity import** — importing PKCS#12 cert into keychain, extracting SecIdentity for TLS handshake
2. **dispatch_data bridging** — converting between Kotlin ByteArray and dispatch_data for NWConnection send/receive
3. **NSNetService-based mDNS discovery** — asynchronous delegation model; needs event ordering verification
4. **Coarse TLS-rejection detection** — NWConnection error callback may not distinguish cert-rejection from network errors; may require retry logic or error-code parsing
