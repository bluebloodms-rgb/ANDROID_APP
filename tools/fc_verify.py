#!/usr/bin/env python3
"""
fc_verify.py — DroneGCS companion verification script (PC side).

Connect to the ArduPilot flight controller over USB and:
  1. Read live telemetry (altitude, battery, GPS, mode) — "get altitude" style.
  2. Send a MAVLink command (COMMAND_LONG) and print the COMMAND_ACK result.
  3. Monitor the USB MAVLink stream so that when the PHONE app sends a command
     over the RF controller link, we can observe the FC's reaction/telemetry.

Usage:
    python3 fc_verify.py --port /dev/ttyACM1 --baud 115200 monitor            # watch + telemetry
    python3 fc_verify.py --port /dev/ttyACM1 --baud 115200 send ARM --on       # arm
    python3 fc_verify.py --port /dev/ttyACM1 --baud 115200 send DISARM         # disarm
    python3 fc_verify.py --port /dev/ttyACM1 --baud 115200 send TAKEOFF --alt 2.5
    python3 fc_verify.py --port /dev/ttyACM1 --baud 115200 send RTL
    python3 fc_verify.py --port /dev/ttyACM1 --baud 115200 send ALTITUDE

Uses dronekit if importable, else raw pymavlink. All libs load from
/home/mohammad/pylib (no pip on this machine).
"""

import argparse
import sys
import time

PYLIB = "/home/mohammad/pylib"
if PYLIB not in sys.path:
    sys.path.insert(0, PYLIB)

# --- MAVLink command ids (ArduPilot/common) ---
MAV_CMD_COMPONENT_ARM_DISARM = 400
MAV_CMD_NAV_TAKEOFF = 22
MAV_CMD_NAV_RETURN_TO_LAUNCH = 20
MAV_CMD_NAV_LAND = 21

try:
    from pymavlink import mavutil
    from pymavlink.dialects.v20 import ardupilotmega as mavlink
    HAVE_MAVLINK = True
except Exception as e:
    HAVE_MAVLINK = False
    print(f"[!] pymavlink import failed: {e}", file=sys.stderr)

try:
    import dronekit
    HAVE_DRONEKIT = True
except Exception as e:
    HAVE_DRONEKIT = False
    print(f"[!] dronekit import failed (falling back to pymavlink only): {e}", file=sys.stderr)

RESULT_NAMES = [
    "ACCEPTED", "TEMPORARILY_REJECTED", "DENIED", "UNSUPPORTED",
    "FAILED", "IN_PROGRESS", "CANCELLED", "AUTH_DENIED",
]


def result_name(res):
    return RESULT_NAMES[res] if 0 <= res < len(RESULT_NAMES) else f"UNKNOWN({res})"


def connect_mavutil(port, baud):
    mavutil.set_dialect("ardupilotmega")
    master = mavutil.mavlink_connection(port, baud=baud)
    print(f"[*] Waiting for HEARTBEAT on {port} @ {baud} ...")
    msg = master.recv_match(type="HEARTBEAT", blocking=True, timeout=15)
    if msg is None:
        raise TimeoutError("No heartbeat from flight controller. Check USB/serial & baud.")
    master.wait_heartbeat()
    print(f"[+] Connected to FC: system={master.target_system}, component={master.target_component}")
    return master


def read_telemetry(vehicle):
    """Print a one-shot telemetry snapshot (dronekit API)."""
    try:
        mode = vehicle.mode.name if vehicle.mode else "?"
        print(f"[*] mode={mode} armed={vehicle.armed}")
        if vehicle.location.global_relative_frame:
            print(f"[*] relative altitude = {vehicle.location.global_relative_frame.alt:.2f} m")
        if vehicle.location.global_frame:
            print(f"[*] lat={vehicle.location.global_frame.lat:.7f} lon={vehicle.location.global_frame.lon:.7f}")
        if vehicle.gps_0:
            print(f"[*] GPS fix={vehicle.gps_0.fix_type} sats={vehicle.gps_0.satellites_visible}")
        if vehicle.battery:
            print(f"[*] battery voltage={vehicle.battery.voltage if vehicle.battery.voltage is not None else '?'} V")
        if vehicle.attitude:
            a = vehicle.attitude
            print(f"[*] roll={a.roll:.1f} pitch={a.pitch:.1f} yaw={a.yaw:.1f}")
    except Exception as e:
        print(f"[!] telemetry read skipped: {e}", file=sys.stderr)


def send_command_long(master, command, *params, timeout=8.0):
    """Send COMMAND_LONG and wait for the matching COMMAND_ACK."""
    p = list(params) + [0.0] * (7 - len(params))
    master.mav.command_long_send(
        master.target_system, master.target_component,
        command, 0,
        p[0], p[1], p[2], p[3], p[4], p[5], p[6],
    )
    print(f"[->] COMMAND_LONG cmd={command} params={[round(x, 3) for x in p]}")
    deadline = time.time() + timeout
    while time.time() < deadline:
        ack = master.recv_match(type="COMMAND_ACK", blocking=True, timeout=timeout)
        if ack is None:
            print(f"[!] No ACK within {timeout}s", file=sys.stderr)
            return None
        if ack.command == command:
            print(f"[<-] COMMAND_ACK cmd={ack.command} result={result_name(ack.result)} (code {ack.result})")
            return ack.result
    return None


def send_command(args, master):
    """Dispatch a named command via COMMAND_LONG."""
    name = args.command.upper()
    if name == "ARM":
        r = send_command_long(master, MAV_CMD_COMPONENT_ARM_DISARM, 1.0 if args.on else 0.0)
        if r == 0:
            print("[+] Vehicle is now ARMED." if args.on else "[+] Vehicle DISARMED.")
    elif name == "DISARM":
        r = send_command_long(master, MAV_CMD_COMPONENT_ARM_DISARM, 0.0)
        if r == 0:
            print("[+] Vehicle DISARMED.")
    elif name == "TAKEOFF":
        alt = args.alt if args.alt else 2.5
        r = send_command_long(master, MAV_CMD_NAV_TAKEOFF, 0, 0, 0, 0, 0, 0, alt)
        if r == 0:
            print(f"[+] TAKEOFF accepted (target altitude {alt} m).")
    elif name == "RTL":
        r = send_command_long(master, MAV_CMD_NAV_RETURN_TO_LAUNCH)
        if r == 0:
            print("[+] RTL accepted.")
    elif name == "LAND":
        r = send_command_long(master, MAV_CMD_NAV_LAND)
        if r == 0:
            print("[+] LAND accepted.")
    elif name == "ALTITUDE":
        master.mav.command_long_send(
            master.target_system, master.target_component,
            mavlink.MAV_CMD_REQUEST_MESSAGE, 0,
            mavlink.MAVLINK_MSG_ID_GLOBAL_POSITION_INT, 0, 0, 0, 0, 0, 0,
        )
        print("[->] REQUEST_MESSAGE GLOBAL_POSITION_INT sent; waiting for reply ...")
        msg = master.recv_match(type="GLOBAL_POSITION_INT", blocking=True, timeout=5)
        if msg:
            rel = getattr(msg, "relative_alt", 0) / 1000.0
            lat = getattr(msg, "lat", 0) / 1e7
            hdg = getattr(msg, "hdg", 0) / 100.0
            print(f"[<-] GLOBAL_POSITION_INT: relative_alt={rel:.2f} m lat={lat:.7f} heading={hdg:.1f} deg")
        else:
            print("[!] No GLOBAL_POSITION_INT reply.", file=sys.stderr)
    else:
        print(f"[!] Unknown command '{name}' (use ARM/DISARM/TAKEOFF/RTL/LAND/ALTITUDE)", file=sys.stderr)
        return


def monitor(master, seconds=float("inf"), verbose=False):
    """Print telemetry + every interesting MAVLink message arriving on the USB port."""
    watch = {
        0: "HEARTBEAT",
        1: "SYS_STATUS",
        24: "GPS_RAW_INT",
        33: "GLOBAL_POSITION_INT",
        65: "RC_CHANNELS",
        74: "VFR_HUD",
        76: "COMMAND_LONG",
        77: "COMMAND_ACK",
        147: "BATTERY_STATUS",
        152: "MEMINFO",
        253: "STATUSTEXT",
    }
    start = time.time()
    counts = {}
    print("[*] Monitoring USB MAVLink stream (Ctrl-C to stop) ...")
    try:
        while time.time() - start < seconds:
            msg = master.recv_match(blocking=True, timeout=1.0)
            if msg is None:
                continue
            mid = msg.get_type()
            counts[mid] = counts.get(mid, 0) + 1
            if mid in watch:
                tag = watch[mid]
                if mid == "HEARTBEAT":
                    print(f"[{tag}] type={msg.type} autopilot={msg.autopilot} mode={msg.custom_mode}")
                elif mid == "COMMAND_LONG":
                    print(f"[{tag}] target_sys={msg.target_system} cmd={msg.command} params={[round(getattr(msg, f'param{i}', 0), 2) for i in range(1, 8)]}")
                elif mid == "COMMAND_ACK":
                    print(f"[{tag}] cmd={msg.command} result={result_name(msg.result)} ({msg.result})")
                elif mid == "STATUSTEXT":
                    txt = bytes(msg.text).decode("utf-8", "replace").strip("\x00")
                    print(f"[{tag}] {txt}")
                elif mid == "GLOBAL_POSITION_INT":
                    print(f"[{tag}] relative_alt={getattr(msg, 'relative_alt', 0)/1000.0:.2f} m lat={getattr(msg, 'lat', 0)/1e7:.7f}")
                elif mid == "GPS_RAW_INT":
                    print(f"[{tag}] fix={msg.fix_type} sats={msg.satellites_visible} eph={msg.eph} epv={msg.epv}")
                elif mid == "BATTERY_STATUS":
                    v = (msg.voltages[0] if len(msg.voltages) else 0) / 1000.0
                    t = getattr(msg, "current_battery", 0) / 100.0
                    print(f"[{tag}] cell1={v:.2f} V current={t:.2f} A")
                elif mid == "SYS_STATUS":
                    print(f"[{tag}] sensors_present=0x{msg.onboard_control_sensors_present:X}")
                elif mid == "VFR_HUD":
                    print(f"[{tag}] gcrspeed={msg.groundspeed} alt={msg.alt:.1f} climb={msg.climb}")
                elif mid == "RC_CHANNELS":
                    rssi = getattr(msg, "rssi", 0)
                    if rssi != 255:
                        print(f"[{tag}] nchannels={msg.chancount} rssi={rssi}")
                elif mid == "MEMINFO":
                    pass
    except KeyboardInterrupt:
        pass
    print("[*] message counts seen on USB:", dict(sorted(counts.items(), key=lambda kv: kv[1], reverse=True)))


def main():
    ap = argparse.ArgumentParser(description="DroneGCS FC verify (dronekit/pymavlink)")
    ap.add_argument("--port", default="/dev/ttyACM1", help="serial port to FC")
    ap.add_argument("--baud", type=int, default=115200, help="baud rate")
    ap.add_argument("--seconds", type=float, default=float("inf"), help="monitor duration (s)")
    ap.add_argument("--on", action="store_true", help="ARM: arm (param1=1)")
    ap.add_argument("--alt", type=float, default=0.0, help="TAKEOFF: altitude in m")
    sub = ap.add_subparsers(dest="cmd")
    sub.add_parser("monitor")
    send_parser = sub.add_parser("send")
    send_parser.add_argument("command", help="ARM/DISARM/TAKEOFF/RTL/LAND/ALTITUDE")
    args = ap.parse_args()
    if not HAVE_MAVLINK:
        print("[!] pymavlink required.", file=sys.stderr)
        return 1

    master = connect_mavutil(args.port, args.baud)

    vehicle = None
    if HAVE_DRONEKIT:
        try:
            from dronekit import connect
            vehicle = connect(args.port, wait_ready=False, baud=args.baud, heartbeat_timeout=15)
        except Exception as e:
            print(f"[!] dronekit connect failed ({e}) — using raw pymavlink.", file=sys.stderr)
            vehicle = None

    if vehicle:
        read_telemetry(vehicle)

    if args.cmd == "send":
        send_command(args, master)
    else:
        monitor(master, seconds=args.seconds, verbose=True)

    if vehicle:
        try:
            vehicle.close()
        except Exception:
            pass
    return 0


if __name__ == "__main__":
    sys.exit(main())