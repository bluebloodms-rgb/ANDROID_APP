# HANDOFF — AI-Session Status (NEW)

> **Read this file first.** It is the single source of truth for the current state of the
> Android GCS port. Older docs (`HANDOFF.md`, `projects_status.md`) are historical.
> Last updated: 2026-08-30 (session 3: auto-connect + video-source persistence — see §7b),
> branch `android-gcs-bootstrap-77e8a`, HEAD includes commit `57272b8` + UI redesign commits (see git log).

## 1. Mission

Port the Windows Python GCS (`ui/`, `core/`) to an Android Kotlin/Compose app
(`android/`) for a SIYI-camera FPV drone with on-board target tracking.
Link: Bluetooth SPP → MAVLink v2 (custom STATUSTEXT command protocol).
Video: SIYI RTSP stream (Windows) / phone camera fallback (Android, until RTSP decode lands).

**User constraint:** no access to the drone/RC controller right now. Everything testable
without hardware is done/tested via `adb` UI automation; live-link tests come later.

## 2. Environment (rebuilt once — reuse, don't rebuild)

| Thing | Path / value |
|---|---|
| JDK 17 (temurin) | `/home/mohammad/jdk17` |
| Android SDK | `/home/mohammad/android-sdk` (platforms/android-34, build-tools/34.0.3, platform-tools, licenses accepted) |
| `android/local.properties` | `sdk.dir=/home/mohammad/android-sdk` |
| Gradle | wrapper 8.5 (`gradlew`), deps cached in `~/.gradle` (first download took ~1h — do not delete `~/.gradle`) |
| Compose BOM | 2024.05.00, Material3 (ModalBottomSheet available) |
| App | `com.dronegcs.app`, minSdk 26, target/compile 34, versionName 1.0 |

`dl.google.com` is filtered on this machine — SDK parts came from Tencent mirror
(`https://mirrors.cloud.tencent.com/AndroidSDK/`), JDK from GitHub adoptium releases.

## 3. Build / test / install / verify commands

```bash
cd /home/mohammad/Desktop/ai-priject-folder/ANDROID_APP/android
export JAVA_HOME=/home/mohammad/jdk17
./gradlew :app:assembleDebug          # build → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # 26 tests (MavlinkProtocolTest 13, CommandTest 12, RealFrameTest 1)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c && adb shell am start -n com.dronegcs.app/.MainActivity
adb logcat -d | grep -E 'FATAL|AndroidRuntime'   # crash check
```

## 4. Phone (Samsung Galaxy A54, SM-A546E, serial `RZCW405PT1B`)

- USB debugging **authorized** (user fixed it). Keep it that way; if `adb devices` shows
  `unauthorized`, ask user to tap the RSA prompt on the phone.
- Applied & verified device settings (re-apply if phone rebooted):
  ```bash
  adb shell svc power stayon usb                       # screen stays on while plugged
  adb shell settings put global stay_on_while_plugged_in 7
  adb shell settings put system accelerometer_rotation 0   # rotation locked
  adb shell settings put system user_rotation 0            # portrait
  ```
- App is installed and runs (verified: no FATAL, UI automation worked).

## 5. ADB UI-automation cheat sheet (Compose UI)

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
grep -oE '(text|content-desc)="[^"]+"' /tmp/ui.xml | sort -u      # find buttons
adb shell input tap X Y                                            # click
adb exec-out screencap -p > /tmp/s.png
adb shell input keyevent KEYCODE_WAKEUP                            # wake screen
```
Compose nodes appear in the dump mainly via `text=` and `content-desc=` (icons) —
always set `contentDescription`/text on tappable controls for testability.

## 6. What is DONE (commit lineage)

- `0aa91d7` MAVLink overhaul: standard v2 framing, official CRC_X25+crc_extra table, streaming parser.
- `7a407d7` GPS_RAW_INT eph offset fix (HDOP).
- `333314b` Connecting-spinner crash fix; live BT link to SIYI verified earlier.
- `98890d4` HANDOFF.md (historical).
- `57272b8` **Feature port from Windows** (verified 26/26 tests, installed on phone):
  - **START** = COMMAND_LONG (ARM 400/1) + COMMAND_LONG (TAKEOFF 22, alt 2 m) + STATUSTEXT `START:TRUE[,pid kv]` fallback; PID values sent as k=v pairs of filled fields.
  - **CANCEL** = COMMAND_LONG (RTL 20) + STATUSTEXT `CANCEL:TRUE,Notcare:TRUE` fallback.
  - `COMMAND_ACK` (77) parsed -> FlightState.lastCommandResult shown in TopBar.
  - `BATTERY_STATUS` (147) voltages at field index 5 (`@5`), GLOBAL_POSITION_INT (33) handler.
  - `BottomControlPanel`: PID rows (YAW1/YAW2/ROLL/THRUST/SERVO), START/CANCEL/MANUAL/AUTO, Speed 12/19/22, Targets Person/Car/Balloon/UAV — all wired to ConnectionViewModel.send*.
  - `PitchSlider` -> sendPitch; `DirectionalPad` zoom 1-10x + center reset + %1.1fX badge.
  - Tap-on-video -> `Pos:x,y` scaled to 1280x720 + TapReticle (3 s fade).
  - Settings screen (RTSP source toggle, defaults), camera-switch button.
  - All command strings validated by CommandTest against the Python protocol.

Command protocol (STATUSTEXT severity 6, <=50 chars) — see `Command.kt`:
`START:TRUE[,pid]`, `CANCEL:TRUE,Notcare:TRUE`, `MANUAL`/`AUTOMAT`, `Speed x.x -> Pitch x.x`, `CLASS:n,Notcare:n`, `Zoom:x.x`, `Pitch:x.x`, `Pos:x,y`.
Flight state arrives via STATUSTEXT `Op:1,St:1,Md:1,Cls:0,Spd:19.0,Zoom:1.0,Pitch:0.0,Can:0`.

## 7. UI REDESIGN (mobile-first) — DONE, verified on device

User: the Windows-clone layout wasted the small screen. Redesigned and verified via adb UI automation:
- **Always visible:** full-screen video, compact top HUD (telemetry chips + CONNECT + camera-switch + settings icons inside `TopBar.kt`), slim bottom `ControlDock` (chevron | START | CANCEL | MODE toggle).
- **Hidden:** full PID/speed/target panel → Material3 `ModalBottomSheet` via "More controls" chevron (scrollable, verified all fields render); pitch slider → left-edge handle (`Toggle pitch slider`) with slide-in animation; zoom → compact `ZoomPill` (+/−/label/reset) bottom-right; `DirectionalPad.kt` deleted in session 3 (was kept-but-unused).
- **`PitchSlider.kt` REWRITTEN** (old one had broken internal `fillMaxSize` layout + stale-closure gestures): weight-based track, tap-to-set + vertical drag + ±1° step buttons, live `°` label, `rememberUpdatedState` everywhere, top=+90 (red) / bottom=−90 (blue).
- **Pitch/zoom send-throttle:** pitch sends only when value moved ≥1° (`onPitchSliderChange`); sends are NOT gated on isConnected (safe no-ops offline for debugging); START/CANCEL/MODE stay gated (safety).
- **Device-verified results:** pitch 0°→1°→2° via steps, drag to 90°, tap-to-set −64°; zoom 1.0X→3.0X→reset 1.0X; sheet shows YAW1/YAW2/ROLL/THRUST/SERVO/SPEED/TARGET/MANUAL/AUTO; CONNECT opens Bluetooth Devices dialog; Settings opens (Baud 115200, Default Camera); 0 FATAL exceptions; 26/26 unit tests pass.
## 7b. SESSION 3 — auto-connect + video-source persistence (DONE, device-verified)

All offline-testable (no drone). Builds 14–19 green, 26/26 unit tests, final APK installed,
0 FATAL. Datastore left in sane state: video source = phone camera (facing=BACK),
**no saved BT device** (cleared after testing), baud 115200.

- **Auto-connect on launch** (`ConnectionViewModel.autoConnectOnLaunch()`, 600 ms delay; guards:
  auto-reconnect setting / no saved address / BT off / saved device not bonded — each logs its skip reason).
  `persistLastDevice()` now saves the device on **every successful connect**, not only via Settings.
  Device-verified: with saved `soundcore R60i NC` → launch log
  `Auto-connect on launch: connecting to soundcore R60i NC (34:09:C9:AE:1D:56)` → real BT stack
  `PAGE_TIMEOUT` (device absent — expected) → `Auto-reconnect attempt 1 in 1000ms to soundcore R60i NC`.
- **Auto-reconnect bug fixed:** `scheduleAutoReconnect()` used to fall back to the *first bonded
  device* (`_availableDevices.firstOrNull()`) when a connect attempt failed before ever succeeding —
  it retried the wrong device (DESKTOP-… instead of the one we tried). Now tracks
  `_lastAttemptedDevice` (set in `connect()`); `_connectedDevice` falls back to it too.
- **Video source persistence/restore:** `SettingsRepository` gains `video_source_mode` +
  `get/setVideoSourceMode()`; `CameraViewModel.setVideoSource()` persists mode/URL/facing;
  `restoreSavedVideoSource()` called from `MainScreen` (was hardcoded `PhoneCamera()`).
  Round-trips verified offline: camera → RTSP (`rtsp://192.168.144.25:8554/main.264`) → relaunch →
  RTSP restored + `Starting RTSP stream`; RTSP → camera → relaunch → `Restoring saved video source:
  phone camera (facing=1)`.
- **RtspVideoRepository rewritten (Media3 threading fix):** ExoPlayer was built/attached on
  `Dispatchers.IO` → `PlayerView.setPlayer()` asserts main thread → FATAL `IllegalStateException`
  (exposed by the restore path; also latent black screen when `play()` ran before `bindToPlayerView()`).
  Now owns `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` + `pendingUrl`;
  `CameraModule` no longer injects `CoroutineScope`.
- **SettingsScreen fixes:** (a) root `Column` had no `verticalScroll` — apply buttons/Flight
  Defaults unreachable on forced-landscape phone; (b) bonded-device list now refreshes on entry
  (`LaunchedEffect`) — each nav destination has its **own** `hiltViewModel()` instance, so Settings
  never saw MainScreen's list; (c) tapping the already-selected device row **clears** the saved
  device (`updateBtDevice(null, null)` removes the DataStore keys).
- **Connect dialog fix:** `bluetoothReady` was a stale snapshot param computed once before the
  dialog opened — enabling BT while the dialog was open + Refresh never showed devices. Dialog now
  calls `isBluetoothReady()` itself on every recomposition.
- `DirectionalPad.kt` deleted (`git rm`).
- Not verifiable offline: actual BT connect success + RTSP decode (see §8).



## 8. Remaining TODO (priority order)

1. **Live-hardware test day:** BT connect → verify telemetry chips, START/CANCEL ACK results, PID send, speed/target, zoom/pitch/Pos over the real link. Pair FC in Android BT settings first, then select it in the GCS Settings device list (auto-connect will fire on next launch). SIYI units (`SIYI-5902210770`, `SIYI-5902244052`) are already bonded. (Pos tap + reticle not re-verified this session — canvas has no accessibility node; verify visually on hardware day.)
2. **RTSP live decode:** ExoPlayer (Media3) pipeline is in place and restore/persistence work offline (§7b) — but actual H.264 decode was never seen (no reachable stream offline; app shows graceful ExoPlayer error while TCP times out). Verify against the real SIYI stream on hardware day.
3. `git push origin android-gcs-bootstrap-77e8a` (remote exists; may need credentials).
4. Optional: landscape-specific layout tuning, night colors, haptics on ARM/TAKEOFF confirm.


## 9. Pitfalls for the next AI

- Sandbox resets: `/tmp` is wiped; `~/.gradle`, `/home/mohammad/android-sdk`, `/home/mohammad/jdk17` survive. If build fails with missing SDK — rebuild only SDK parts from the Tencent mirror.
- ADB `unauthorized` -> needs the human at the phone.
- Gradle first-run downloads are slow; always build with `JAVA_HOME=/home/mohammad/jdk17`.
- Don't commit `local.properties` (gitignored) — it points at this machine's SDK.
- `stay_on_while_plugged_in` may reset on USB re-plug; re-run section 4 commands.
- **The manifest (not system rotation settings) forces landscape** on this phone — "re-apply
  rotation lock" was a misdiagnosis; keep-awake still applies.
- adb/UI-automation quirks (landscape): Compose buttons appear as `text=`, not `content-desc=`;
  swipes near the bottom edge (y≈1000) hit gesture-nav and exit the app — start swipes at y≤850;
  a focused TextField keeps re-panning above the IME — unfocus (keyevent 111/66) before scrolling.
- `svc bluetooth enable` / `svc bluetooth disable` work over adb shell on this phone; BT ships OFF.
- `uiautomator dump` can return stale content right after a dialog state change — wait ~2 s and re-dump.
- Datastore inspect: `adb shell run-as com.dronegcs.app cat files/datastore/settings.preferences_pb | strings`.
