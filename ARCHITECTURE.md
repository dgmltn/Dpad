# Dpad Architecture

## Modules

| Module | Status | Purpose |
|---|---|---|
| `:domain` | Plan 2 | Pure Kotlin. Models (`TvDevice`, `PairedDevice`, `RemoteKey`, `Shortcut`, `ConnectionState`) and contracts (`DeviceRepository`, `ShortcutRepository`, `RemoteController`, `DevicePairer`, `DeviceDiscovery`). Curated shortcut catalog constant. `jvm()` target. No `:protocol` dependency — depends on abstract contracts only. |
| `:protocol` | ✅ Implemented | Standalone Android TV Remote v2 library module — no dependency on `:domain` or app code. Wire-generated protobuf messages, length-prefixed framing, pairing state machine, remote session state machine, mDNS discovery, and the three expect/actual seams (TlsSocketFactory, sha256, DerX509). `jvm()` + `iosArm64()` + `iosSimulatorArm64()` targets. |
| `:data` | Plan 2 | Implements `:domain` contracts by wiring `:protocol` to persistence (DataStore). Depends on `:protocol`. |
| `:design` | Plan 3 | Design system: theme + remote-control primitives (circular d-pad composable, pill buttons, button grid). |
| `:remote` | Plan 3 | Main remote screen feature (d-pad, buttons, shortcut row, text-input sheet, connection banner). |
| `:devices` | Plan 3 | Device list (paired + discovered), pairing flow with code entry, device switcher. |
| `:shortcuts` | Plan 3 | Shortcut editor (catalog picker, custom entry, reorder/delete). |
| `app-android` | Plan 3 | Thin Android entry point + Koin composition root. |
| `app-ios` | Plan 3 | Thin iOS entry point (XcodeGen `project.yml` + Swift entry committed, `.xcodeproj` gitignored) + Koin composition root. |

## Dependency Graph

```mermaid
graph TD
  protocol[":protocol (implemented)"]
  data[":data (plan 2)"] --> protocol
  domain[":domain (plan 2)"]
```

**Key:** `:domain` and `:protocol` are independent; `:data` depends on `:protocol` and implements `:domain` contracts.
