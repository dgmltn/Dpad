# Dpad — Android TV Remote (KMP) — Design

**Date:** 2026-08-11
**Status:** Approved

## Overview

Dpad is a Kotlin Multiplatform app for Android and iOS (shared Compose Multiplatform UI) that
acts as a remote control for Android TV / Google TV devices. It discovers TVs via mDNS and
speaks the reverse-engineered Android TV Remote protocol v2 (protobuf over client-certificate
TLS) directly — no Google services, no cloud.

**Audience:** personal use now, designed so nothing blocks a later store release.
**Test hardware:** a Google TV television and a streamer box/stick on the local network.

## Requirements

### Controls (main remote screen)

- Circular **button d-pad** (four directional segments + center select) as the dominant UI
  element, with haptic feedback and hold-to-auto-repeat on the directional segments.
- **Back** and **Home** buttons.
- **Volume up / down** and **Mute**.
- **Media transport:** play/pause, rewind, fast-forward.
- **Power** toggle.
- **Keyboard text input:** a sheet with a hidden text field that streams characters as key
  events (covers search fields; deliberately not full IME sync).
- **Configurable shortcut buttons** that launch a specific app on the TV.

### Shortcuts

- Configured from a **curated catalog** of popular apps with known app-link URLs (Netflix,
  YouTube, Prime Video, Disney+, Max, Plex, Jellyfin, Spotify, Twitch — an extendable constant
  list in `:domain`) **plus custom entries** (user-entered name + app-link URL).
- Launching uses the protocol's `RemoteAppLinkLaunchRequest(app_link_url)`; the protocol offers
  no way to enumerate apps installed on the TV, hence the catalog approach.
- Shortcuts are reorderable and deletable in an editor screen.

### Devices

- **Multiple paired TVs** with a quick switcher: every paired TV is remembered (name, host,
  server-cert fingerprint), the app auto-reconnects to the last-used one, and a device picker
  switches between them.
- Discovery via mDNS service type `_androidtvremote2._tcp` on the local network.
- Unpairing removes the stored device.

## Architecture

Approach: **max-shared Kotlin protocol implementation** — one protocol implementation in
`commonMain`, narrow expect/actual seams for the platform-network primitives, JVM-testable
end-to-end against a fake TV server.

### Modules

| Module | Purpose |
|---|---|
| `:domain` | Pure Kotlin. Models (`TvDevice`, `PairedDevice`, `RemoteKey`, `Shortcut`, `ConnectionState`) and contracts (`DeviceRepository`, `ShortcutRepository`, `RemoteController`, `DevicePairer`, `DeviceDiscovery`). Curated shortcut catalog constant. `jvm()` target. |
| `:protocol` | Standalone Android TV Remote v2 library module — no dependency on `:domain` or app code. Wire-generated protobuf messages, length-prefixed framing, pairing state machine, remote session state machine, and the three expect/actual seams (below). `jvm()` target. |
| `:data` | Implements `:domain` contracts by wiring `:protocol` to persistence (DataStore). |
| `:design` | Design system: theme + remote-control primitives (circular d-pad composable, pill buttons, button grid). |
| `:remote` | Main remote screen feature (d-pad, buttons, shortcut row, text-input sheet, connection banner). |
| `:devices` | Device list (paired + discovered), pairing flow with code entry, device switcher. |
| `:shortcuts` | Shortcut editor (catalog picker, custom entry, reorder/delete). |
| `app-android` | Thin Android entry point + Koin composition root. |
| `app-ios` | Thin iOS entry point (XcodeGen `project.yml` + Swift entry committed, `.xcodeproj` gitignored) + Koin composition root. |

Feature modules depend on `:domain` and `:design`, never on `:data` or `:protocol` directly.

### expect/actual seams (all inside `:protocol`)

1. **`TlsSocket`** — a secure byte pipe supporting client certificates and trust-all server
   validation (TVs use self-signed certs; the server cert is captured from the handshake).
   - Android/JVM: `SSLSocket` with a custom `KeyManager` and `TrustManager`.
   - iOS: `NWConnection` with a `SecIdentity`.
2. **`ClientIdentity`** — generates and persists one self-signed X.509 client cert + key pair
   (the credential shared across all pairings) and exposes public-key modulus/exponent for both
   client and server certs (needed for the pairing PIN hash).
   - Android/JVM: `KeyPairGenerator` + X.509 generation (BouncyCastle if platform APIs fall short).
   - iOS: `SecKeyCreateRandomKey` + Security-framework cert handling.
3. **`MdnsBrowser`** — browse/resolve `_androidtvremote2._tcp`.
   - Android: `NsdManager`. iOS: `NWBrowser`. JVM (tests): a stub.

## Protocol

### Pairing (port 6467, first contact per TV)

1. TLS connect (trust-all), capture server cert.
2. `PairingRequest` → `PairingRequestAck`; `PairingOption` (hex encoding, 6 symbols);
   `PairingConfiguration` → TV displays a 6-character code.
3. User enters the code. Client computes SHA-256 over client-cert modulus + exponent +
   server-cert modulus + exponent + code prefix bytes; sends `PairingSecret`.
4. On `PairingSecretAck` the TV trusts the client cert permanently. Persist the device.

Wrong code or timeout restarts the flow with a user-facing message.

### Session (port 6466)

- TLS with the client cert. TV opens with `RemoteConfigure`; client replies with device info,
  then sends `RemoteSetActive`.
- Commands: `RemoteKeyInject` with press/release actions for `DPAD_*`, `DPAD_CENTER`, `BACK`,
  `HOME`, `VOLUME_UP`, `VOLUME_DOWN`, `VOLUME_MUTE`, `MEDIA_PLAY_PAUSE`, `MEDIA_REWIND`,
  `MEDIA_FAST_FORWARD`, `POWER`; text input as per-character key events;
  `RemoteAppLinkLaunchRequest` for shortcuts.
- Hold-to-repeat = repeated key events on a timer while a d-pad segment is held.
- TV pushes `RemoteSetVolumeLevel` (drives volume/mute display) and `RemotePingRequest`, which
  must be answered with `RemotePingResponse` or the TV drops the connection.

### Connection state machine

`Disconnected → Connecting → Connected`, plus `PairingRequired` when the session handshake is
rejected (client cert no longer trusted, e.g. after a TV factory reset). Exposed as
`StateFlow<ConnectionState>`.

- Auto-reconnect with capped exponential backoff (1s, 2s, 4s, 8s, cap ~15s) while the remote
  screen is foregrounded; each attempt re-resolves the host via mDNS first (TV IPs change).
- Sends are fire-and-forget onto a channel. When disconnected, key presses are **dropped, not
  queued** (replaying stale d-pad events on reconnect is worse); the UI shows a connection
  banner instead.
- The UI never sees raw exceptions — only state-machine states. Tagged Kermit loggers
  (`Pairing`, `Session`, `Mdns`) make protocol debugging greppable.

## Persistence

DataStore Preferences (KMP artifact, `createWithPath`) holds:

- Paired devices (JSON-serialized list), last-used device id.
- Shortcuts (JSON-serialized list, ordered).
- Client certificate + private key (PEM), plain DataStore per the personal-project secrets
  policy — upgrade to Keychain/Keystore is an explicit decision gated on a store release.

Structured lists in DataStore is a deliberate, acknowledged stretch of the key-value guidance:
the data is a handful of small records and Room would be overkill.

## UI

### Remote screen (root)

- Top bar: device name + connection-state dot (tap → device switcher), keyboard icon
  (tap → text-input sheet), power button.
- Center: circular d-pad dominating the screen.
- Rows below: back / home; volume down / mute / volume up; rewind / play-pause / fast-forward.
- Bottom: shortcut row (user's shortcuts + edit affordance → shortcut editor).
- Disconnected/reconnecting: slim status banner; controls stay visible, presses are dropped.

### Navigation

Navigation 3, single back stack: Remote (root) → Devices → Pairing; Remote → Shortcuts editor.

### Patterns

ViewModel per screen, single `UiState` StateFlow, UDF, immutable-collection fields
(kotlinx-collections-immutable). Compose Multiplatform on both platforms.

## Testing

- `:protocol` `jvmTest`: **`FakeTvServer`** — a JVM `SSLServerSocket` with a self-signed cert
  speaking both the pairing and session protocols. Covers the pairing hash, framing, keepalive,
  reconnect/backoff, and the full pair → connect → send-key flow as fast JVM integration tests.
  TDD for all protocol logic.
- `:data` `jvmTest`: temp-file DataStore, real serialization.
- Feature-module ViewModel tests: Android host-test source set, awaiting state conditions
  (never `advanceUntilIdle(); state.value`).
- Screens: compile-verified + smoke-tested. Final verification is manual against the Google TV
  set and the streamer box.

## Dependencies

Per standing preferences: Koin, Navigation 3, Kermit, DataStore Preferences,
kotlinx-serialization-json, kotlinx-collections-immutable, androidx ViewModel (KMP).

**New addition (approved):** **Wire** (`com.squareup.wire`) — KMP protobuf codegen from the
reverse-engineered `pairingmessage.proto` / `remotemessage.proto` (checked into
`resources/proto/` with generation into `:protocol`).

Explicitly absent: Ktor (no HTTP anywhere — raw TLS sockets only), Room, Coil.

Versions pinned at project start from live registry metadata via `scripts/latest-versions.sh`;
no alpha/beta/rc.

## Non-goals

- Full IME sync / voice input / Assistant.
- Casting or media playback on the phone.
- Enumerating apps installed on the TV (protocol does not support it).
- Wake-on-LAN power-on of fully powered-off TVs (POWER key covers sleep/wake; revisit if the
  streamer box proves unreachable when asleep).
- Tablet/adaptive layouts (phone-first; nothing in the module shape blocks adding them later).
