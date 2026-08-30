# HANDOFF — AI-Session Status (NEW)

> **Read this file first.** It is the single source of truth for the current state of the
> Android GCS port. Older docs (`HANDOFF.md`, `projects_status.md`) are historical.
> Last updated: 2026-08-30, branch `android-gcs-bootstrap-77e8a`, HEAD includes commit `57272b8` + UI redesign commits (see git log).

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
- **Hidden:** full PID/speed/target panel → Material3 `ModalBottomSheet` via "More controls" chevron (scrollable, verified all fields render); pitch slider → left-edge handle (`Toggle pitch slider`) with slide-in animation; zoom → compact `ZoomPill` (+/−/label/reset) bottom-right; `DirectionalPad.kt` kept but unused.
- **`PitchSlider.kt` REWRITTEN** (old one had broken internal `fillMaxSize` layout + stale-closure gestures): weight-based track, tap-to-set + vertical drag + ±1° step buttons, live `°` label, `rememberUpdatedState` everywhere, top=+90 (red) / bottom=−90 (blue).
- **Pitch/zoom send-throttle:** pitch sends only when value moved ≥1° (`onPitchSliderChange`); sends are NOT gated on isConnected (safe no-ops offline for debugging); START/CANCEL/MODE stay gated (safety).
- **Device-verified results:** pitch 0°→1°→2° via steps, drag to 90°, tap-to-set −64°; zoom 1.0X→3.0X→reset 1.0X; sheet shows YAW1/YAW2/ROLL/THRUST/SERVO/SPEED/TARGET/MANUAL/AUTO; CONNECT opens Bluetooth Devices dialog; Settings opens (Baud 115200, Default Camera); 0 FATAL exceptions; 26/26 unit tests pass.

## 8. Remaining TODO (priority order)

1. **Live-hardware test day:** BT connect → verify telemetry chips, START/CANCEL ACK results, PID send, speed/target, zoom/pitch/Pos over the real link. Pair FC in Android BT settings first. (Pos tap + reticle not re-verified this session — canvas has no accessibility node; verify visually on hardware day.)
2. **RTSP decode:** `RtspVideoRepository.kt` needs real stream display (MediaCodec/ExoPlayer) — currently video shows phone camera. SIYI stream is 720p H.264 RTSP.
3. **Auto-connect last device on app start** (Windows remembers COM port; setting key exists in DataStore).
4. `git push origin android-gcs-bootstrap-77e8a` (remote exists; may need credentials).
5. Optional: landscape-specific layout tuning, night colors, haptics on ARM/TAKEOFF confirm, `DirectionalPad.kt` deletion once confirmed unused.

## 9. Pitfalls for the next AI

- Sandbox resets: `/tmp` is wiped; `~/.gradle`, `/home/mohammad/android-sdk`, `/home/mohammad/jdk17` survive. If build fails with missing SDK — rebuild only SDK parts from the Tencent mirror.
- ADB `unauthorized` -> needs the human at the phone.
- Gradle first-run downloads are slow; always build with `JAVA_HOME=/home/mohammad/jdk17`.
- Don't commit `local.properties` (gitignored) — it points at this machine's SDK.
- `stay_on_while_plugged_in` may reset on USB re-plug; re-run section 4 commands.
