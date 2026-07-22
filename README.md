# Drone Control GUI

A PySide6-based ground control station for a camera-equipped drone. It streams a live video feed, overlays real-time telemetry, and lets an operator control flight mode, speed, target class, zoom, pitch/steer angle, and PID gains — all connected to the vehicle over MAVLink via DroneKit (typically through a Bluetooth serial link).

## Features

- **Live video feed** from a local/USB camera with click-to-target and a mouse-following reticle overlay.
- **Telemetry HUD** — battery voltage, satellite count, altitude, HDOP, and flight mode, each rendered as a custom widget.
- **Connection indicator** showing DISCONNECTED / CONNECTING / CONNECTED / ERROR state, with automatic reconnect and heartbeat monitoring.
- **Flight control bar** — Start/Cancel, Manual/Auto mode, speed presets, and target class selection (person/car/balloon/UAV).
- **PID tuning panel** — inline fields for Yaw, Roll, Thrust, and Servo gains (Kp/Kd/limit), sent with the Start command.
- **Zoom D-Pad** and **pitch/steer slider** for camera control, with on-screen value readouts.
- **Camera and Flight Controller menus** for switching between detected cameras and Bluetooth/COM ports at runtime.

## Requirements

- Python 3.10+
- A camera accessible via OpenCV
- A MAVLink-speaking flight controller reachable over a serial/Bluetooth COM port

## Installation

```bash
git clone <your-repo-url>
cd <your-repo-folder>
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # macOS/Linux

pip install -r requirements.txt
```

## Running

```bash
python main.py
```

On launch, the app auto-detects a camera and attempts to auto-connect to a flight controller over Bluetooth. Use the **Camera** and **Flight Controller** menus to switch devices manually if needed.

## Assets

The UI expects the following images under `assets/` (referenced by the widgets but not bundled in this repo):

```
app_icon.ico
altitude.png
placeholder.png
plane-mode.png
satellite.png
people.png
sedan.png
balloon.png
drone.png
```

## Notes

- Telemetry and mode updates arrive from a background MAVLink listener thread; UI updates should be marshalled to the Qt main thread (see `flight_interface.py`).
- PID input fields are cleared automatically after each Start command.

## License

Add your license of choice here (MIT, Apache-2.0, etc.).
