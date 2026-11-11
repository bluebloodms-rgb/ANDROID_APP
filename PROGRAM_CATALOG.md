# DroneGCS - Program Catalog

A simple guide to understand what this app does, its states, and what was fixed.

---

## What Is This App?

**DroneGCS** = Drone Ground Control Station

An Android app that lets you control a drone from your phone via Bluetooth. Think of it like a remote control with a video feed and instruments.

### What It Does
- **Live Video**: Shows camera feed from the drone (RTSP stream)
- **Telemetry**: Battery, altitude, GPS, satellites, signal quality
- **Controls**: Start/stop tracking, zoom, pitch, speed, target selection (person/car/balloon)
- **Safety**: Emergency stop detection, low battery warnings, GPS status

---

## Main Screens / States

| Screen | What You See |
|--------|--------------|
| **Main Screen** | Video feed + top bar (connection, battery, GPS) + bottom controls |
| **Onboarding** | First-time setup: permissions, Bluetooth pairing |
| **Settings** | Video source (phone camera vs RTSP), RTSP URL, units |

---

## Connection States (Top Bar Indicator)

| State | Color | Meaning |
|-------|-------|---------|
| **Disconnected** | Gray | No Bluetooth connection |
| **Connecting** | Blue | Trying to connect to drone |
| **Connected** | Green | **Full link** - drone telemetry flowing |
| **Link Only** | Amber | Bluetooth connected but **drone silent** (no data for 5+ sec) |
| **Tracking Offline** | Red chip | Drone connected but **tracking computer offline** (no status for 3+ sec) |
| **Error** | Red | Connection failed (permission, timeout, etc.) |

---

## Drone Flight States (From Drone's Computer)

The drone sends status text like: `Op:2,St:1,Md:2,Cls:1,Spd:22,Zoom:3,Pitch:-12,Can:0`

| Field | Values | Meaning |
|-------|--------|---------|
| **Op** (Operation) | 1=Ready, 2=In Operation | Is tracking active? |
| **St** (State) | 1=First, 2=Track | Initializing or actively tracking |
| **Md** (Mode) | 1=Manual, 2=Auto | Who controls: you or computer? |
| **Cls** (Class) | 0=Balloon, 1=Person, 2=Car, 3=Drone | What to track |
| **Spd** | m/s | Target speed |
| **Zoom** | 1.0, 2.0, 3.0... | Camera zoom level |
| **Pitch** | degrees | Camera tilt (-90 to +90) |
| **Can** | 0=Inactive, 1=Cancelled | Cancel tracking flag |

---

## GPS States

| Fix Type | Display | Can Do RTL? |
|----------|---------|-------------|
| 0 | **NO GPS** | ❌ |
| 1 | **NO FIX** (searching) | ❌ |
| 2 | **2D FIX** | ❌ |
| 3 | **3D FIX** | ✅ |
| 4 | **DGPS** | ✅ |
| 5 | **RTK FIXED** | ✅ |
| 6 | **RTK FLOAT** | ✅ |

**RTL (Return to Launch) only works with 3D FIX or better AND valid coordinates (not 0,0).**

---

## Battery States

| Voltage | State | Action |
|---------|-------|--------|
| > 30.5V | **OK** | Normal operation |
| 30.0V - 30.5V | **WARNING** | Monitor closely, plan landing |
| < 30.0V | **LOW** | Land soon |
| < 29.0V | **CRITICAL** | Land immediately! |

*Hysteresis prevents flickering: warning clears only above 30.5V*

---

## Signal Quality (Link Quality)

| Quality | Display | What It Means |
|---------|---------|---------------|
| 90-100% | **EXCELLENT** | Perfect |
| 70-89% | **GOOD** | Normal |
| 50-69% | **FAIR** | Some packet loss |
| 30-49% | **POOR** | Significant loss |
| 0-29% | **CRITICAL** | Link failing |

Based on MAVLink sequence gaps (not just CRC errors).

---

## Emergency States

| State | Trigger | What Happens |
|-------|---------|--------------|
| **Emergency Stop** | Drone sends "PreArm: Motors Emergency Stopped" | Red banner, cannot arm/start, clears after 10s or when drone rearms |
| **Low Battery Warning** | Battery < 30V | Amber banner, suggests landing |
| **Critical Battery** | Battery < 29V | Red banner, immediate landing required |
| **Tracking Offline** | No status from drone computer for 3s | Red chip, tracking may not work |
| **Link Only** | No telemetry from drone for 5s | Amber status, drone may be powered off |

---

## What Opencode Fixed (Previous Session)

The previous AI session fixed **5 reliability gaps** - core connection and command issues:

### 1. "Connected ≠ Drone Alive"
**Before**: Green "Connected" just meant Bluetooth socket open.
**After**: Separates link from drone health.
- Green = recent drone data
- Amber = socket open but drone silent (5s timeout)
- Tracking Offline chip = no tracking computer data (3s timeout)

### 2. "Sent ≠ Actually Written"
**Before**: Commands queued but maybe never reached drone.
**After**: `sendAndAwait()` waits for actual socket write + flush (1.5s timeout).
- Logs: "Enqueued" → "Sent (written to socket)" or "NOT sent (reason)"

### 3. ACK Tracking with Timeout
**Before**: Fire-and-forget commands, no confirmation.
**After**: CommandTracker matches drone replies to sent commands.
- States: EXECUTED, NOT_EXECUTED, NO_ACK, UNCONFIRMED, FAILED
- Timeout: 4s (3s for Start/Cancel)
- New commands cancel old pending ones

### 4. Ordered Sends
**Before**: Rapid commands could complete out of order.
**After**: Mutex serializes all commands - one at a time, in order.

### 5. Protocol Preserved
- Kept working STATUSTEXT encoding (same as Windows app)
- Added sys=10 (tracking computer) acceptance before vehicle filter
- Direct CLASS:n echo works

**Verification**: 42 tests pass, APK builds (22 MB)

---

## What I Fixed (This Session - From Log Analysis)

I analyzed crash logs and flight logs, found **8 bugs**, fixed **6** (2 more need investigation):

### P0 - Critical (Fixed)

| Bug | Found In Logs | Fix |
|-----|--------------|-----|
| **App crashes on RTSP video** | `IllegalArgumentException` in ExoPlayer RTSP parser | Dedicated playback thread with crash recovery, auto-retry once, forced TCP |
| **GPS fix type wrong** | Always showed "2D FIX" even with 3D | Fixed wire format offset (8→28), correct enum mapping |
| **Emergency stop flickers** | Flag cleared on any unrelated message | 10-second TTL latch, clears only on arm change or user dismiss |
| **RTL works at (0,0)** | Map used 0,0 as valid position | `hasValidPosition` requires 3D fix + non-zero coordinates |

### P1 - Important (Fixed)

| Bug | Found In Logs | Fix |
|-----|--------------|-----|
| **NaN altitude** | `AHRS2: altitude=NaNm` | Central `isFinite()` guard for ALL floats, freshness timestamps |
| **CRC errors piling up** | "CRC mismatch x14" | Sealed result type, logs msgId+raw bytes, resyncs by 1 byte, sequence-gap quality |
| **Battery warning flickers** | 30.0V boundary | Hysteresis (warn <30, clear >30.5), critical tier (<29), ignores sentinels |
| **Mode flapping unlogged** | custom_mode 2↔20 flapping | Logs every mode change with source (FC vs RC) |

### Missed (Need Investigation)

| Bug | Found In Logs | Status |
|-----|--------------|--------|
| **Mode flapping** | ALT_HOLD ↔ GUIDED_NOGPS | Logged now, root cause needs tracking |
| **Negative altitude** | -1.88m → -0.5m | Barometer drift, needs home-referenced altitude |

---

## File Map (Where Things Live)

```
android/app/src/main/java/com/dronegcs/app/
├── data/
│   ├── bluetooth/
│   │   ├── BluetoothLink.kt          # Command queue, sendAndAwait, WriteResult
│   │   └── BluetoothSppService.kt    # Bluetooth connection, read/write workers
│   ├── mavlink/
│   │   └── MavlinkFlightRepository.kt # MAVLink parsing, health watchdog, commands
│   └── video/
│       └── RtspVideoRepository.kt    # ExoPlayer RTSP with crash isolation
├── domain/
│   ├── command/
│   │   ├── CommandTracker.kt         # ACK tracking, timeouts
│   │   └── CommandFeedback.kt        # Command result events
│   ├── model/
│   │   ├── FlightState.kt            # ALL drone state + computed properties
│   │   ├── ConnectionState.kt        # Disconnected/Connecting/Connected/Error
│   │   └── Command.kt                # Start/Cancel/Mode/Speed/Class/Zoom/Pitch/Pos
│   └── protocol/
│       ├── MavlinkProtocol.kt        # Encode/decode, CRC, ParseResult
│       └── MavlinkCrcExtra.kt        # CRC lookup table
├── viewmodel/
│   ├── ConnectionViewModel.kt        # Connection UI logic
│   ├── CameraViewModel.kt            # Video source management
│   └── TelemetryViewModel.kt         # Telemetry UI state
└── ui/                               # Compose screens & components
```

---

## Key Constants (Thresholds)

| Constant | Value | Where |
|----------|-------|-------|
| Vehicle stale timeout | 5 seconds | `MavlinkFlightRepository.kt` |
| Responder offline timeout | 3 seconds | `MavlinkFlightRepository.kt` |
| Write timeout | 1.5 seconds | `BluetoothLink.kt` |
| Command ACK timeout | 4 seconds (3s Start/Cancel) | `CommandTracker.kt` |
| Low battery threshold | 30.0V | `FlightState.kt` |
| Critical battery | 29.0V | `FlightState.kt` |
| Battery hysteresis | 0.5V | `FlightState.kt` |
| Emergency stop TTL | 10 seconds | `FlightState.kt` |
| Field freshness | 1 second | `FlightState.kt` |
| CRC link quality window | Sequence gaps | `MavlinkFlightRepository.kt` |

---

## How to Build & Test

```bash
cd android
./gradlew :app:testDebugUnitTest   # Run unit tests (50 tests)
./gradlew :app:assembleDebug       # Build APK
# APK at: android/app/build/outputs/apk/debug/app-debug.apk
```

**Expected**: 50 tests pass (42 original + 8 new), APK ~22 MB

---

## Log Files (For Debugging)

```
logs/
├── crash-*.txt          # App crashes (ExoPlayer, etc.)
└── gcs-*.log            # Flight session logs (telemetry, commands, errors)
```

Open any `.log` file to see what happened during a flight.

---

## Quick Reference: What Each Color Means

| UI Element | Green | Amber/Yellow | Red |
|------------|-------|--------------|-----|
| **Connection** | Full telemetry | Link only (drone silent) | Error/Disconnected |
| **Battery** | >30.5V | 30.0-30.5V | <30.0V (Critical <29V) |
| **GPS** | 3D Fix (3+) | 2D Fix (2) | No Fix (0-1) |
| **Signal** | 90-100% | 50-89% | <50% |
| **Tracking** | Active | - | Offline chip |
| **Emergency** | - | - | Red banner |

---

## For Developers: Adding a New Command

1. Add command type in `Command.kt`
2. Add encoding in `Command.toStatustextString()`
3. Add handler in `MavlinkFlightRepository.sendCommand()` (uses mutex)
4. Track in `CommandTracker` if needs ACK
5. UI calls `ConnectionViewModel.sendCommand()`

---

## Safety Notes

- **Never** send COMMAND_LONG (ARM/TAKEOFF/RTL) - drone uses custom STATUSTEXT protocol
- **Always** use `sendAndAwait()` for commands - confirms socket write
- **RTL disabled** without valid GPS (3D fix + non-zero position)
- **Video crash** isolated - telemetry continues working
- **Battery sentinels** (65535 = no monitor) ignored automatically

---

*Last updated: Commit `1240a5c` - P0/P1 fixes from AI review*
*Previous: Opencode session - 5 reliability gaps fixed*