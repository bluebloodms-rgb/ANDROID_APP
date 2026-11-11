# Session Handoff — Drone GCS (TX path FIXED & VERIFIED on FC wire)

Date: 2026-09-01 — Android TX path is FIXED and proven end-to-end. Remaining issue is vehicle-side.

## THE ACTUAL ROOT CAUSE (found last)

`BluetoothSppService.kt` `connect()`: after `socket.connect()` only `startReadLoop()` was
called — **`startWriteWorker()` was never started**. All sends were queued into
`link.outgoing` (`trySend` succeeds → app logged "Sent STATUSTEXT") but NO coroutine ever
consumed the channel → **bytes were never written to the Bluetooth socket.**
RX worked, TX silently dead. No MAVLink identity fix could help before this.

Fix applied (one line): `startWriteWorker()` added after `startReadLoop()` in connect().

## All fixes this session (built, unit-tested, installed)
1. `MavlinkProtocol.kt`: GCS identity sysId=255 / compId=190 (was 1/1 = vehicle's own id,
   which ArduPilot drops). Constants `GCS_SYSTEM_ID` / `GCS_COMPONENT_ID`.
2. `MavlinkFlightRepository.sendCommand()`: STATUSTEXT-only, matching Windows algorithm
   (START:TRUE / CANCEL:TRUE,Notcare:TRUE). Removed dangerous COMMAND_LONG arm/takeoff.
3. STATUSTEXT severity filter removed (PreArm texts arrive sev=2) + `RX STATUSTEXT` logging.
4. HEARTBEAT parsing -> `FlightState.updateHeartbeat(armed, customMode)` (+isArmed/fcMode).
5. **`BluetoothSppService.connect()`: startWriteWorker() now called.** <- the real killer bug
6. Build: `JAVA_HOME=/home/mohammad/jdk17 ./gradlew :app:assembleDebug` (system java is 11!).

## Verification (2026-09-01 ~10:44)
Setup: FC on laptop USB (/dev/ttyACM1), phone holds BT link to SIYI-5902210770.
- Phone telemetry: HEARTBEAT 1 Hz OK
- Phone sent START:TRUE -> observed on FC wire:
  `STATUSTEXT sev=6 src sys255/comp190: START:TRUE`  PROOF of end-to-end uplink.
- SiYI BT bridge is a RAW BYTE PIPE both directions (frames pass through unmodified,
  original seq/sys/comp preserved).

## REMAINING (vehicle-side, NOT an app bug)
The FC does NOT act on START:TRUE — no `Op:...` state reply ever comes. Findings:
- FC firmware has NO Lua scripting (no SCR_* params of 1129) and no companion port
  (SERIAL0=MAVLink2/USB, SERIAL1=MAVLink2(SiYI,57600), SERIAL2=23 RCIN, SERIAL3=GPS).
- The `Op:1,St:1,Md:1,Cls:0,Spd:19.0,Zoom:1.0,Pitch:0.0` responder is MISSING on this vehicle.
  User confirms the Windows app DID show live Op: state before -> responder existed then.
  => Check: same drone/build? Custom firmware or Lua script must be (re)installed, or the
  responder lives in the SiYI unit firmware (which sits in the BT<->FC path).
- FC PreArm blockers: Battery 1 unhealthy / Rangefinder 1 Not Detected /
  Motors Emergency Stopped (interlock) / RC not found (RC off) — arming blocked regardless.

## Misc notes
- ArduPilot re-broadcasts GCS STATUSTEXT between links preserving source sys/comp
  (that's how phone frames appear on the USB capture).
- SIYI BT bridge wedges after many abrupt connect/disconnect cycles (accepts SPP, streams
  nothing) -> power-cycle drone to recover. Avoid rapid reconnect loops.
- pymavlink: use ardupilotmega dialect; v2 frame = 12+len bytes (header is 10!);
  x25crc over frame[1:-2] + crc_extra byte; my earlier '0 valid frames' was my own CRC bug.
- App custom_mode parsing looks wrong (1359151616) — check offsets in processHeartbeat. TODO.
- Scripts: /tmp/usb_watch.py (STATUSTEXT/ACK listener on /dev/ttyACM1),
  /tmp/bt_start_test.py (BT v1/v2 START tester), /tmp/usb_dump.py (raw capture).
  Captures: /tmp/fc_usb.bin /tmp/fc_live.bin /tmp/siyi_bt2.bin.
- Phone: Samsung RZCW405PT1B (adb). UI taps: START (587,957), CANCEL (1345,957).
====================================================================
# SESSION 2 — Video latency fix + UI polish (2026-09-01)
====================================================================
User wants: (1) less video lag (ViewLink comparison,(2) flight mode NAME shown,
(3) welcome only on first launch;(4) UI scales across screen sizes;(5) bottom panel
closes when tapping outside it. ALL changes done AND the debug build COMPILES
(compileDebugKotlin -> EXIT 0).

## Build command (always use THIS Java):
   cd /home/mohammad/Desktop/ai-priject-folder/ANDROID_APP/android
   export JAVA_HOME=/home/mohammad/jdk17
   ./gradlew :app:assembleDebug   (do NOT use system java=11.)
   Quick check: ./gradlew :app:compileDebugKotlin --offline -q   (returns 0 = OK.

## 1) Video lag — RtspVideoRepository.kt
- Custom DefaultLoadControl: minBuffer 200 / maxBuffer 1000 /
  bufferForPlayback 200 / afterRebuffer 500(ExoPlayer default buffers ~50s and
  never catches up on RTSP — that's why VIEW lagged). NOTE: media3 1.2.1's
  DefaultLoadControl.Builder.setBufferDurationsMs takes EXACTLY 4 args —ther
  old code passed 5 backBuffer args whicн DON'T compile here. Already fixed.
- Catch-up watchdog (`startWatchdog`): every 2s, if currentLiveOffset > ~0.6s ->
  brief play at 1.1x to resync (else back to 1.0x.
- IMPORTANT media3 1.2.1 API fact: NO top-level `LiveConfiguration` class and NO
  `ExoPlayer.setLiveConfiguration()`. Live window IS set ONLY via nested
  `androidx.media3.common.MediaItem.LiveConfiguration.Builder()` (setTargetOffsetMs 0,
  setMinOffsetMs 0, setMaxOffsetMs 0, build)) applied in startPlayback(MediaItem Builder
  .setLiveConfiguration(...)). Done. Wrong import line REMOVED.

## 2) Flight mode name (Guided, AltHold...
- `FlightState.fcModeName`: maps ArduPilot Copter custom_mode enum 0..26 to names
  (0=STABILIZE,1=ACRO,2=ALT_HOLD,3=AUTO,4=GUIDED,5=LOITER,...25=AUTOROTATE.
- `FlightState.modeName` prefers fcModeName when heartbeat available, else legacy Md-based MANUAL/AUTO.
- TopBar.kt already renders the mode chip(green surface when armed, yellow text) — verify on DEVICE.
- `MavlinkFlightRepository.processHeartbeat` NOW VERIFIED CORRECT offsets:
  custom_mode u32 little-endian @0; base_mode u8 @6 (ARMED=bit7;6: bogus 1359151616 earlier
  was a WRONG offset in old code — FIXED.

## 3) Welcome screen only first launch — DONE
- SettingsViewModel `isFirstRun: StateFlow<Boolean?>` (NULL while DataStore loads).
- MainActivity `if (isFirstRun != null)` gate around AppNavHost; startDestination=ONBOARDING
  only when true, else MAIN. So later launches skip welcome entirely. verify: install, force-stop,relaunch -> MAIN.

## 4) UI scaling — NEW UiScale framework
- NEW FILE: ui/theme/UiScale.kt
  - LocalUiScale = staticCompositionLocalOf(UiScale(1f);
  - UiScaleContainer(composable,BoxWithConstraints-based): scale=min(w/411,h/914),coerce 0.8..1.6
  - `@Composable fun Dp.scaled()/invScaled()` — REQUIRED to read LocalUiScale.current
    (@Composable OPT-IN was mandatory to compile;was plain fn = error).
- MainScreen.kt wrapped whole layout in UiScaleContainer (enclosing Box body) — braces balanced (build passeds.
- ControlDock.kt applied .scaled() to height(64,button120x44,icon44/28,paddings,corner12. PATTERN reference.

## 5) Bottom panel tap-outside-to-close — DONE
- MainScreen.kt injBox,right after TapReticle,before TopBar, when showControlPanel:a fullscreen Box
  scrim Color 0x59000000).clickable(indication=null,MutableInteractionSource){showControlPanel=false}.
  Drawn BEHIND dock/panel so those stay interactive. verify on DEVICE.

## Current build state: WORKS
`./gradlew :app:compileDebugKotlin --offline -q` -> EXIT 0 after 3 compile-fixes this session:
  (a) DefaultLoadControl.Builder.setBufferDurationsMs 5->4 args;
  (b) removed wrong top-level LiveConfiguration — must use nested MediaItem.LiveConfiguration.Builder;
  (c) Dp.scaled() @Composable annotation. All earlier session-1 edits compiled already.

## NEXT AI TODO (verify + finish)
1. INSTALL assembledDebug on phone (adb install -r app-debug.apk)and TEST each ask on DEVICE:
   - video lag vs before/ViewLink(watchdog logic;if still laggy raise catch-up speed/aggressiveness);
   - mode name chip visible in TopBar;compare vs flightState.md;
   - force-stop+relaunch — must SKIP welcome;
   - run on a small AND large device/emulator → controls scale nicely;
   - pull up bottom panel,tap video area → panel closes.
2. Apply .scaled() to MORE components for full tablet support(BottomControlPanel widgets/rows,
   TopBar chips/fonts,ZoomPill,PitchSlider,CrosshairOverlay; keep MIN touch target~44.dp;
   TopBar already scrolls-horz asymtotic overflow fallback.
3. BluetoothSppService still logs RAW hex per message (RX#..,— Timber spam near BT pipe
   inlet adds GC pressure=micro-latency. Throttle/debug-only(or drop entirely;MAVLinkFlightRepository
   already logs parsed messages).
4. modePollJob in MavlinkFlightRepository is a busy no-op loop(delay 50ms,ddoes nothing,
   just reads/rewrites flight state — pure waste;delete entire coroutine+its start/job/cancel refs.

## Environment/hardware reminders(from session 1)
- Phone: Samsung RZCW405PT1B(adb — `adb shell pidof com.dronegcs.app`.). UI taps final app coords:
   START(587,957),CANCEL(1345,957).
- SIYI-5902210770 BT bridge;/dev/ttyACM1 FC USB;1209 VID(interesting but no claim].
- Vehicle-side 'Op:' responder still MISSING — see session-1 notes above(no Lua,no comp port,
   FC blocks arming:Battery 1 unhealthy,Emergency-Stopped interlock,Rangefinder missing,RC off).
