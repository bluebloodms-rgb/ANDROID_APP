# DroneGCS - User Guide

**Version**: 1.3.0 | **For**: Drone operators using the Android app

---

## Quick Start

1. **Pair Bluetooth** once in Android Settings → Bluetooth → "SIYI-XXXXXX"
2. **Open App** → Grant permissions (Location, Camera, Files)
3. **Tap Connect** → Select your paired device
4. **Wait for Green** → You're live!

---

## Main Screen Layout

```
┌─────────────────────────────────────────┐
│  TOP BAR                                │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌────┐  │
│  │Link  │  │Batt  │  │GPS   │  │Mode│  │
│  │●Green│  │31.2V │  │●3D 3 │  │AUTO│  │
│  └──────┘  └──────┘  └──────┘  └────┘  │
├─────────────────────────────────────────┤
│                                         │
│         VIDEO FEED                      │
│         (RTSP Stream)                   │
│                                         │
│     [Tap anywhere = send position]      │
│                                         │
├─────────────────────────────────────────┤
│  BOTTOM CONTROLS                        │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐       │
│  │START│ │CANCEL│ │MODE │ │SPD  │       │
│  └─────┘ └─────┘ └─────┘ └─────┘       │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐       │
│  │CLASS│ │ ZOOM │ │PITCH│ │     │       │
│  └─────┘ └─────┘ └─────┘ └─────┘       │
└─────────────────────────────────────────┘
```

---

## TOP BAR - Status Indicators

### Connection Status (Leftmost)

| Icon | Color | Meaning | Action |
|------|-------|---------|--------|
| ● | **Green** | **FULL CONNECTION** - Drone telemetry flowing normally | Fly normally |
| ● | **Amber** | **LINK ONLY** - Bluetooth connected but drone silent (5+ sec no data) | Check drone power, wait or reconnect |
| ● | **Red** | **ERROR** - Connection failed | Tap to retry, check Bluetooth |
| ○ | **Gray** | **DISCONNECTED** - Not connected | Tap to connect |

### Battery (Voltage)

| Display | Color | Meaning |
|---------|-------|---------|
| `31.2V` | Green | Full / Good |
| `30.2V` | Amber | **Warning** - Plan landing soon |
| `29.5V` | Red | **Critical** - Land NOW! |
| `--` | Gray | No battery data |

> **Note**: Warning appears at 30V, clears only above 30.5V (prevents flickering). Critical at 29V.

### GPS (Satellites + Fix)

| Display | Color | Meaning | RTL Works? |
|---------|-------|---------|------------|
| `●3D 8` | Green | 3D Fix, 8 satellites | ✅ YES |
| `●2D 6` | Amber | 2D Fix only (no altitude) | ❌ NO |
| `●NO 0` | Red | No GPS fix | ❌ NO |
| `--` | Gray | No GPS data | ❌ NO |

> **RTL (Return to Launch) ONLY works with 3D Fix AND valid position (not 0,0)**

### Flight Mode

| Display | Meaning |
|---------|---------|
| `MANUAL` | You control everything |
| `AUTO` | Drone follows tracking computer |
| `GUIDED` | Drone follows GCS commands |
| `RTL` | Returning to launch |
| `LAND` | Landing now |
| `ALT_HOLD` | Holding altitude |

---

## VIDEO FEED

- **Tap anywhere** → Sends that screen position to drone as tracking target
- **Pinch** → Zoom (if using phone camera)
- **Long press** → Context menu (future)

### Video Status Overlays

| Overlay | Meaning |
|---------|---------|
| `Buffering...` | Connecting to RTSP stream |
| `Stream not available` | RTSP URL wrong or camera offline |
| `Camera permission required` | Grant permission in settings |

---

## BOTTOM CONTROLS - Buttons & Sliders

### Row 1: Primary Actions

| Button | Action | When to Use |
|--------|--------|-------------|
| **START** | Begin tracking operation | After selecting class, speed, mode |
| **CANCEL** | Stop tracking immediately | Emergency stop, or end mission |
| **MODE** | Toggle Manual ↔ Auto | Manual = you fly, Auto = tracking computer flies |
| **SPD** | Set target speed (m/s) | Adjust before START |

### Row 2: Tracking Parameters

| Control | Values | Description |
|---------|--------|-------------|
| **CLASS** | Balloon / Person / Car / Drone | What the drone should track |
| **ZOOM** | 1.0x → 10.0x | Camera optical zoom level |
| **PITCH** | -90° → +90° | Camera tilt (negative = down) |

---

## Drone Messages - What They Mean

The drone sends status text like: `Op:2,St:1,Md:2,Cls:1,Spd:22,Zoom:3,Pitch:-12,Can:0`

### Decoded

| Code | Name | Values | What It Tells You |
|------|------|--------|-------------------|
| **Op** | Operation | 1=Ready, 2=In Operation | Is tracking active right now? |
| **St** | State | 1=First, 2=Track | Just started or actively tracking? |
| **Md** | Mode | 1=Manual, 2=Auto | Who's flying? |
| **Cls** | Class | 0=Balloon, 1=Person, 2=Car, 3=Drone | What target type |
| **Spd** | Speed | m/s (e.g., 22) | Target speed setting |
| **Zoom** | Zoom | 1.0, 2.0, 3.0... | Current camera zoom |
| **Pitch** | Pitch | degrees (-90 to +90) | Camera angle |
| **Can** | Cancel | 0=No, 1=Yes | Is tracking cancelled? |

### Other Messages You'll See

| Message | Severity | Meaning | Your Action |
|---------|----------|---------|-------------|
| `PreArm: Motors Emergency Stopped` | 🔴 CRITICAL | Drone safety lock engaged | Cannot arm/start. Check drone, clear error, or reboot FC |
| `EKF3 IMU0 MAG0 in-flight yaw alignment complete` | ℹ️ INFO | Compass calibrated | Normal, ignore |
| `Battery low` / `Battery critical` | ⚠️/🔴 | Battery voltage thresholds | Land soon / Land NOW |
| `GPS: no fix` | ⚠️ | Lost GPS | Don't use RTL, fly manually |
| `COMMAND_ACK: DENIED` | ⚠️ | Drone refused command | Check preconditions (armed, mode, GPS) |

---

## Common Workflows

### First Flight Checklist
- [ ] Bluetooth paired in Android Settings
- [ ] App permissions granted (Location, Camera, Files)
- [ ] RTSP URL correct in Settings (if using drone camera)
- [ ] Battery > 30.5V (Green)
- [ ] GPS shows `●3D` with 6+ satellites
- [ ] Connection shows **Green**
- [ ] Video feed visible

### Start Tracking
1. Select **CLASS** (Person/Car/Balloon/Drone)
2. Set **SPD** (e.g., 15 m/s)
3. Set **ZOOM** and **PITCH** for camera framing
4. Tap **MODE** → `AUTO` (if you want tracking computer to fly)
5. Tap **START** → Wait for `Op:2` (In Operation)
6. Tap video feed to designate target position

### Emergency Stop
1. Tap **CANCEL** → Sends `Cancel:TRUE`
2. Or drone sends `PreArm: Motors Emergency Stopped` → Red banner appears
3. **To clear**: Fix issue on drone, then wait 10 seconds or re-arm

### Return to Launch (RTL)
- **Only works if**: GPS = `●3D` AND position ≠ (0,0)
- Tap **MODE** → `RTL` (if available) or use ground station
- If RTL unavailable → Use **LAND** or fly manually home

---

## Settings

| Setting | Where | Description |
|---------|-------|-------------|
| **Video Source** | Settings → Video | Phone Camera / RTSP Stream |
| **RTSP URL** | Settings → Video | e.g., `rtsp://192.168.144.25:554/mpeg4` |
| **Units** | Settings → General | Metric / Imperial |
| **Auto-connect** | Settings → Bluetooth | Connect on app launch |

---

## Troubleshooting

| Problem | Likely Cause | Fix |
|---------|--------------|-----|
| **Stuck on "Connecting"** | Bluetooth not paired / wrong device | Pair in Android Settings first |
| **Amber "Link Only"** | Drone powered off / telemetry radio off | Power on drone, check telemetry radio |
| **Red "Tracking Offline"** | Tracking computer (companion) not running | Start companion computer on drone |
| **Video black / "Buffering"** | Wrong RTSP URL / camera off | Check URL in Settings, power on camera |
| **GPS shows "NO FIX"** | Indoors / no sky view / GPS module issue | Go outside, wait 1-2 min |
| **Battery shows `--`** | Battery monitor not connected | Check drone wiring, or ignore if not equipped |
| **MODE stuck on MANUAL** | Drone not armed / no GPS / safety | Check pre-arm checklist on drone |
| **App crashes on video** | RTSP stream format issue | Fixed in v1.3.0 - update app |

---

## Safety Rules

1. **Never fly near people** without prop guards
2. **Always** have manual control ready (RC transmitter)
3. **RTL requires 3D GPS** - don't rely on it without `●3D`
4. **Battery < 30V** = start landing, **< 29V** = land immediately
5. **Emergency Stop** = motors locked, cannot arm until cleared
6. **Video crash** = telemetry still works, restart video from Settings

---

## Status Quick Reference Card

```
┌────────────────────────────────────────────────────────┐
│  GREEN  = GO        |  AMBER = CAUTION   |  RED = STOP  │
├────────────────────────────────────────────────────────┤
│  Link:    ● Connected    ● Link Only     ● Error       │
│  Battery: >30.5V        30.0-30.5V       <30.0V        │
│  GPS:     ●3D Fix        ●2D Fix         ●No Fix       │
│  Signal:  90-100%        50-89%          <50%          │
│  Tracking: Active        -               Offline       │
│  Emergency: -            -               STOPPED       │
└────────────────────────────────────────────────────────┘
```

---

## Version History (What's Fixed)

| Version | Key Fixes |
|---------|-----------|
| **1.3.0** | RTSP crash recovery, GPS fix type correct, Emergency stop latch, Battery hysteresis, NaN/CRC guards, Mode change logging |
| **1.2.0** | Connection health (Link Only/Tracking Offline), Command ACK tracking, Ordered sends, Actual write confirmation |

---

## Need Help?

- **Logs**: App stores flight logs in `logs/` folder
- **Crash reports**: Auto-saved as `crash-*.txt`
- **GitHub**: https://github.com/bluebloodms-rgb/ANDROID_APP

---

*DroneGCS v1.3.0 - Fly Safe!*