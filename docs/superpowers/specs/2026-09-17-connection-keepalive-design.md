# Dpad — Connection Keep-Alive & TV Power State — Design

**Date:** 2026-09-17
**Status:** Approved (pending spec review)

## Problem

While actively using Dpad as a remote, every screen-on forces a visible reconnect ("Connecting…"
banner, key presses silently dropped until the handshake completes).

Nothing in the app disconnects on screen-off — `RemoteController` is an app-wide singleton on an
app-wide scope. The likely cause is the OS: with the screen off the app is cached (and on
Android 14+ frozen), so it stops answering the TV's keepalive pings (`RemoteSession.readLoop`),
the TV drops the socket, and on screen-on `runLoop` goes through backoff + TCP + TLS +
configure/set-active before keys work again.

To confirm on hardware: `adb logcat -s Session` across a screen off/on cycle should show
`session connection lost` right at screen-on.

## Goals

- On Android, keep the remote session alive across screen-off while the user is actively using
  the remote, via a foreground service with an ongoing notification.
- The notification offers a **Disconnect** action plus a few state-aware quick controls.
- The connection ends on its own when the user is evidently done (TV turned off, TV unreachable,
  or long inactivity) — but only while the app is in the background.
- Surface the TV's power state (on / standby) in the shared remote UI.
- Reconnect automatically whenever the remote screen comes to the foreground.

## Non-goals

- iOS background keep-alive (iOS offers no equivalent; iOS gets the shared power-state UI and
  foreground reconnect only).
- An in-app Disconnect button (auto-disconnect covers the notifications-denied case).
- A `WifiLock`/wakelock up front (added only if device verification shows missed pings).
- `MediaStyle` or custom `RemoteViews` notifications.

## Background: detecting TV power state

The Android TV Remote v2 protocol carries `RemoteMessage.remote_start` (`RemoteStart { bool
started }`). After the session connects, the TV sends it with its current power state and again
on every change: `true` = screen on, `false` = standby. Open-source clients (e.g.
`androidtvremote2`, used by Home Assistant) derive on/off from it. `RemoteSession` currently
discards it.

Most Google TV / Android TV devices keep the remote service running in standby (that's how a
phone remote can wake them with `KEYCODE_POWER`), so turning the TV off usually does **not**
drop the connection — it just sends `started=false`. TVs that cut networking in standby show up
only as a dead socket plus failing reconnects, indistinguishable from "TV left the network".
Behavior varies by hardware, so everything below tolerates `remote_start` never arriving.

## Architecture

The lifetime rules live in shared, JVM-tested code (`AutoDisconnectPolicy` in `:data`). The
Android foreground service is a thin shell: it doesn't own the connection, it mirrors
`RemoteController.connection` (running while not disconnected) and renders the notification.

```
RemoteSession.power
        │
        ▼
RemoteController { connection, power, interactions }
        ├──► RemoteViewModel ──► RemoteScreen           (shared UI)
        ├──► AutoDisconnectPolicy ──► disconnect()      (shared, + appInForeground)
        ├──► ConnectionService ──► notification         (Android)
        └──► ConnectionServiceStarter ──► startForegroundService   (Android, + appInForeground)
```

## `:protocol` — `RemoteSession`

- Add `val power: StateFlow<Boolean?>` (`null` = unknown).
- `readLoop` sets it from `remote_start.started`.
- `handshakeReplies` does too: its two wait loops currently discard any message that isn't the
  one they're waiting for, and the TV may send its initial `remote_start` before
  `remote_set_active`. Each loop gets a one-line check that records `remote_start` before
  skipping the message. Volume handling is unchanged.
- Reset to `null` on connection loss and on `disconnect()`, same as `volume`.

## `:domain`

- `enum class TvPower { On, Off }`.
- `RemoteController` gains:
  - `val power: StateFlow<TvPower?>`
  - `val interactions: SharedFlow<Unit>` — emits on every `press`, `launchApp`, `sendText`.
- `RemoteController.connect()` KDoc already claims idempotency; the implementation must honor it
  (see below).

## `:data`

### `RemoteControllerImpl`

- **Idempotent `connect()` (bug fix):** today `connect()` always calls `disconnect()` first, so
  re-connecting to the same device tears down a live socket. New rule: if the requested device
  is the current one and the state is `Connecting` or `Connected`, `connect()` is a no-op.
  Connecting to a *different* device still switches. From `Disconnected` or `PairingRequired`,
  `connect()` starts a fresh session as today.
- Map and forward `session.power` → `power: StateFlow<TvPower?>` (collector tracked in
  `collectorJobs` like volume; reset to `null` in `disconnect()`).
- `press`/`launchApp`/`sendText` emit on `interactions` (a `MutableSharedFlow` with
  `extraBufferCapacity` so `tryEmit` never drops for a single collector).

### `AutoDisconnectPolicy` (new)

```kotlin
class AutoDisconnectPolicy(
    controller: RemoteController,
    appInForeground: StateFlow<Boolean>,
    scope: CoroutineScope,               // the named("session") scope
    timings: Timings = Timings(),
) {
    data class Timings(
        val tvOff: Duration = 2.minutes,
        val unreachable: Duration = 2.minutes,
        val idle: Duration = 30.minutes,
    )
}
```

Registered as a Koin `single` in `dataModule` (injecting `appInForeground` as
`StateFlow<Boolean>` qualified `named("appInForeground")`) and eagerly resolved by each platform
entry point after Koin starts (`DpadApplication.onCreate`, iOS `DpadShared` init).
All three rules apply **only while `appInForeground` is `false`**; the app becoming visible
cancels every pending timer. Each rule ends in `controller.disconnect()`:

| Rule        | Trigger (app in background)                                         | Resets when                                  |
|-------------|---------------------------------------------------------------------|----------------------------------------------|
| TV off      | `connection == Connected` and `power == Off` for `tvOff`            | power leaves `Off`, or connection changes    |
| Unreachable | `connection == Connecting` continuously for `unreachable`           | connection leaves `Connecting`               |
| Idle        | `connection == Connected` with no `interactions` for `idle`         | any interaction (incl. notification actions) |

Implemented with `collectLatest` + `delay` on the policy's scope so virtual time drives them in
tests (unlike `RemoteSession`'s backoff, which deliberately uses `Dispatchers.Default`).

`appInForeground` is supplied by each platform module (`androidPlatformModule` /
`IosPlatformModule`), alongside the other platform singletons `dataModule` requires:
- Android: from `ProcessLifecycleOwner` (`STARTED` ⇒ `true`; adds `androidx.lifecycle:lifecycle-process`).
- iOS: from the app's active state (in practice iOS suspends the process in the background).

## `:app-android`

### Manifest

- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`.
  (`connectedDevice` also requires one of a set of runtime-free permissions; the existing
  `CHANGE_WIFI_MULTICAST_STATE` qualifies.)
- `<service android:name=".ConnectionService" android:foregroundServiceType="connectedDevice"
  android:exported="false" />`

### `ConnectionService`

- Foreground service, type `connectedDevice`, `START_NOT_STICKY`.
- Koin-injects `RemoteController` and `DeviceRepository`.
- `onStartCommand`: immediately `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`,
  then handles any action intent.
- Collects `controller.connection`; `stopSelf()` on `Disconnected` or `PairingRequired`.
- Collects `connection` + `power` + current device name and re-posts the notification.
- Actions (explicit `PendingIntent`s back to the service, `FLAG_IMMUTABLE`):
  - `ACTION_KEY` with a `RemoteKey` name extra → `controller.press(key)` (also resets the idle rule).
  - `ACTION_DISCONNECT` → `controller.disconnect()` (the connection collector then stops the service).

### Notification

- Channel "Remote connection", `IMPORTANCE_LOW`; `setOngoing(true)`, `setSilent(true)`,
  `setOnlyAlertOnce(true)`.
- Title: TV name. Text: "Connected" / "TV is off" / "Reconnecting…".
- Content intent opens `MainActivity`.
- State-aware actions (standard notifications show at most three):
  - TV on or unknown: **Play/Pause** (`MEDIA_PLAY_PAUSE`), **Mute** (`MUTE`), **Disconnect**
  - TV off: **Power** (`POWER`), **Disconnect**

  Power is deliberately absent while the TV is on, so it can't be switched off by accident from
  the shade.

### `ConnectionServiceStarter`

- Created in `DpadApplication.onCreate` after Koin starts.
- Observes `controller.connection` combined with the Koin-provided `appInForeground`; when the
  connection leaves `Disconnected` **and** the app is in the foreground, calls
  `startForegroundService`. Starting only from the foreground
  satisfies the Android 12+ background-start restriction; background reconnects happen while the
  service is already running.
- Catches `ForegroundServiceStartNotAllowedException` (and `IllegalStateException` on older
  APIs), logs, and continues without keep-alive — behavior then matches today's build.

### Notification permission

On Android 13+, request `POST_NOTIFICATIONS` once, the first time the remote screen reaches
`Connected`. If denied, the service still runs (notification hidden from the shade); the
background auto-disconnect rules still bound its lifetime.

## `:ui` (shared)

- `RemoteUiState` gains `power: TvPower? = null`, combined from `controller.power`.
- When `connection == Connected` and `power == Off`:
  - status dot uses a dim neutral color (not green),
  - the banner reads "TV is off",
  - the Power button gets a filled container to mark it as the obvious next action.
- `power == null` renders exactly as today.
- **Foreground reconnect:** remove `RemoteViewModel.init { onConnectLastUsed() }`; `RemoteScreen`
  calls `onConnectLastUsed()` from a `LifecycleStartEffect` on every `ON_START`. Safe because
  `connect()` is now idempotent. Covers returning after notification Disconnect, after
  auto-disconnect, and after process death.

## iOS

- `IosPlatformModule` supplies `appInForeground` from the application's active state.
- Gets the power-state UI and foreground reconnect via shared code. No background service.

## Error handling

| Situation                                   | Behavior                                                                 |
|---------------------------------------------|--------------------------------------------------------------------------|
| TV never sends `remote_start`               | `power` stays `null`; UI as today; only Unreachable + Idle rules apply   |
| Notifications denied                        | Service runs with hidden notification; auto-disconnect bounds lifetime   |
| Foreground service start refused            | Logged; app runs without keep-alive (today's behavior)                   |
| `PairingRequired`                           | Service stops; app shows existing "Re-pair needed" banner on open        |
| Process killed while service running        | `START_NOT_STICKY`: not restarted; next app open reconnects              |

## Testing

- **`:protocol` — `RemoteSessionTest`:** add `FakeTvServer.sendRemoteStart(started)`. Assert
  `power` tracks `true`/`false`; resets to `null` on `dropConnection()` and on `disconnect()`; a
  `remote_start` sent before `remote_set_active` is captured and doesn't break the handshake.
- **`:data` — `RemoteControllerImplTest`:** same-device `connect()` while Connecting/Connected is
  a no-op (no teardown); different-device `connect()` switches; `power` maps to `TvPower`;
  `press`/`launchApp`/`sendText` each emit on `interactions`.
- **`:data` — new `AutoDisconnectPolicyTest`** (fake `RemoteController`, controllable
  `appInForeground`, `runTest` virtual time):
  - background + TV off 2 min → disconnect; TV back on at 1:59 → no disconnect
  - background + Connecting 2 min → disconnect
  - background + 30 min without interaction → disconnect; interaction at 29 min restarts the timer
  - foregrounding cancels all pending timers; while foregrounded nothing ever disconnects
- **`:ui` — `RemoteViewModelTest`:** `power` reaches `RemoteUiState`; repeated
  `onConnectLastUsed()` is harmless. Update existing `FakeController`s (Remote + Devices tests)
  for the new interface members.
- **`:app-android`:** service/notification/starter are thin wiring with no automated tests
  (as with `MainActivity`); covered by device verification.

## Device verification additions (`docs/DEVICE_VERIFICATION.md`)

- Screen off 5+ min, screen on → remote works immediately, no "Connecting…" banner.
- Notification actions (Play/Pause, Mute, Power, Disconnect) work from the lock screen.
- TV off → notification switches to Power + Disconnect; with the app backgrounded, the service
  stops ~2 min later.
- Record `remote_start` behavior per owned TV model (sent on connect? on power change? network
  kept in standby?).
- If pings are still missed with the screen off, that's the trigger to add a `WifiLock` held
  only while the TV is on.
