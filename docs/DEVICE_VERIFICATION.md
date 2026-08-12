# Device Verification Checklist

Dpad is code-complete: all modules compile, both app targets link, and ViewModels are
host-tested. What no automated gate in this repo can cover is behavior against **real
hardware** — an actual Android TV / Google TV device, a real streaming-box target, and a
real phone. This checklist is that manual pass. Run it on real devices before considering
a build release-ready.

## Building the apps

**Android:**

```bash
./gradlew :app-android:assembleDebug
```

Install the resulting APK (`app-android/build/outputs/apk/debug/app-android-debug.apk`) on
the test phone/tablet via `adb install` or your usual sideload method.

**iOS:** the Xcode project (`app-ios/*.xcodeproj`) is gitignored and generated from
`app-ios/project.yml` via XcodeGen — generate it first, then build/run from Xcode (or
`xcodebuild`):

```bash
cd app-ios && xcodegen generate
```

Then open the generated `.xcodeproj` in Xcode, select a target device/simulator, and
build & run (or drive it headlessly with `xcodebuild`). The app links `app-ios-shared`'s
`DpadShared` framework.

## Checklist

Run items 1-8 on Android first, then repeat the full pass on iOS (item 9). Check off each
item only once verified against real hardware — not the simulator/emulator, where noted.

- [ ] **1. Full-screen launch.** App launches edge-to-edge with no app bar / no system
  chrome eating into the remote UI — the d-pad screen fills the display.
- [ ] **2. mDNS discovery.** The Devices screen's discovery list surfaces both the real
  Google TV set and the streamer box present on the LAN. (This exercises `:protocol`'s
  mDNS discovery and `MdnsBrowser` on the real network — no automated test can simulate a
  real LAN's mDNS traffic.)
- [ ] **3. Pairing.** Selecting an unpaired TV from the discovery list shows the on-screen
  6-character pairing code on the TV; typing that code into the app's pairing sheet
  completes pairing successfully. Restart the app and confirm the paired device persists
  (no re-pairing prompt) across the restart.
- [ ] **4. Remote control.** Each of the following actuates the real TV: d-pad up, down,
  left, right, and center/select; back; home; volume up; volume down; mute; media
  play-pause; rewind; fast-forward; and power.
- [ ] **5. Shortcut launch.** Selecting a configured shortcut (catalog or custom) launches
  the corresponding app on the TV.
- [ ] **6. Text input.** Opening the keyboard/text-input sheet and typing characters
  correctly enters text into a TV search field (exercises `RemoteController.sendText`'s
  real char-to-keycode mapping against the actual TV's IME).
- [ ] **7. Auto-reconnect.** Let the TV go to sleep, then wake it. The app's session
  reconnects automatically and does **not** falsely demand re-pairing — confirms the
  3-strike `PairingRequired` threshold from Plan 1 tolerates transient post-wake failures
  instead of misfiring on the first reconnect attempt.
- [ ] **8. TLS 1.2 handshake.** The pairing/remote-session TLS handshake succeeds against
  the real Android TV Remote v2 service (Plan-1 open question: the protocol pins TLS 1.2,
  and this can only be confirmed against genuine hardware, not a mock server).
- [ ] **9. iOS pass.** Repeat items 1-8 above on a physical iPhone, additionally
  confirming the Plan-1 iOS device-test risks that only manifest on real hardware:
  - Keychain `SecIdentity` import for the client TLS identity succeeds.
  - `NWConnection` send/receive works end-to-end against the real TV.
  - `NSNetService` (Bonjour) discovery finds the same devices as Android's mDNS pass.
  - TLS rejection (e.g. wrong/unpaired device) is detected — coarse handling is
    acceptable, but the app must not hang or crash when the handshake is refused.

## Notes

- Items 2, 3, 4, 7, and 8 fundamentally require a real Android TV / Google TV device and
  cannot be verified in CI or against a simulator.
- Item 9's Keychain/`NWConnection`/`NSNetService` risks specifically require a physical
  iPhone — the iOS Simulator does not exercise the same Keychain or Bonjour code paths as
  real hardware.
- If any item fails, file it against the relevant Plan (1: protocol/pairing/session; 2:
  domain/data/persistence; 3: UI/app wiring) rather than reopening this checklist — this
  document only tracks what to verify, not the fix.
