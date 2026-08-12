Implements `:domain` contracts. Depends on `:domain` and `:protocol` — dependency is one-way (`:data → :domain`, `:data → :protocol`); neither `:domain` nor `:protocol` depends back on `:data`. Persists via DataStore Preferences (JSON-in-Preferences); owns all mapping between domain types and `:protocol`/Wire types. Exposes a Koin `dataModule` (`data/di/DataModule.kt`) that binds every `:domain` contract to its `:data` implementation.

`dataModule` REQUIRES the app/platform module (Plan 3) to provide three singletons it cannot construct itself:
- `single<DataStore<Preferences>> { ... }` — the platform-specific file path (e.g. Android `Context.filesDir`, iOS `NSDocumentDirectory`).
- `single<MdnsBrowser> { ... }` — on Android this needs a `Context`; the JVM `actual` is no-arg (used directly by tests).
- `single<CoroutineScope>(named("session")) { ... }` — MUST be single-threaded-confined (e.g. `viewModelScope` / `Dispatchers.Main.immediate`). It backs both `DevicePairerImpl` and `RemoteControllerImpl`; `RemoteSession`'s generation guard is atomic only under cooperative scheduling (Plan-1 carried-forward note), so a multi-threaded dispatcher here is unsafe.

`DeviceDiscoveryImpl`, `DevicePairerImpl`, and `RemoteControllerImpl` wrap the final `:protocol` classes (`MdnsBrowser`, `PairingClient`, `RemoteSession`) — thin, compile-verified adapters; device-verified in Plan 3. `HostResolver` (`data/resolve/`) is the one pure, fully-unit-tested policy piece: it decides which host to (re)connect to for a `PairedDevice` given the latest mDNS discovery snapshot.

`RemoteControllerImpl.sendText` is an intentional no-op stub: `:protocol`'s `RemoteSession` has no text-input API yet, so per-character key events are deferred to Plan 3 alongside the UI that produces them.

`DataModuleTest` (`data/src/jvmTest/.../di/DataModuleTest.kt`) starts Koin with test stand-ins for the three platform singletons (`tempDataStore()`, JVM no-arg `MdnsBrowser()`, `CoroutineScope(Dispatchers.Unconfined)`) and asserts every `:domain` contract resolves — a graph-verification test, not a behavioral one.
