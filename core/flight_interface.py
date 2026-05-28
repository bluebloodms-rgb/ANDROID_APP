import serial.tools.list_ports
from threading import Thread
import time
from dronekit import connect, Vehicle
from PySide6.QtCore import QTimer, QObject, Signal

class FlightState:
    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    CONNECTED = "CONNECTED"
    ERROR = "ERROR"
    def __init__(self):
        self.initialized = False
        self.connection_state = FlightState.DISCONNECTED

        # Defaults (flight OFF)
        self.op = 1      # 1 = Ready, 2 = In operation
        self.st = 1      # 1 = First, 2 = Track
        self.md = 1      # 1 = Manual, 2 = Automatic
        self.cls = 0     # 0 = Person, 2 = Car
        self.spd = 19  # m/s
        self.pitch = None    
        self.can = 0  # 0 = inactive, 1 = cancelled/active
        self.zoom = None # ← NEW: Add this line
        self.mouse_x = None
        self.mouse_y = None
        

        # Telemetry for overlays
        self.battery = None
        self.altitude = None
        self.hdop = None
        self.mode = None
        self.satellites = None

    def update_from_message(self, msg: str):
        """
        Example:
        "Op: 1, St: 1, Md: 1, Cls: 0, Spd: 1.5"
        """
        self.initialized = True
        parts = msg.replace(" ", "").split(",")
        for p in parts:
            k, v = p.split(":")
            if k == "Op":
                self.op = int(v)
            elif k == "St":
                self.st = int(v)
            elif k == "Md":
                self.md = int(v)
            elif k == "Cls":
                self.cls = int(v)
            elif k == "Spd":
                self.spd = float(v)
            elif k == "Zoom":                 
                self.zoom = float(v)
            elif k == "Pitch":                    
                self.pitch = float(v)
            elif k == "Can":                
                self.can = int(v)

class FlightInterface(QObject):
    connection_state_changed = Signal(str)

    def __init__(self):
        super().__init__()
        self.connected_port = None
        self.vehicle: Vehicle | None = None
        self.state = FlightState()
        self._ui_callback = None
        self._thread = None
        self._stop_flag = False

        # Timer for mode updates
        self._mode_timer = QTimer()
        self._mode_timer.timeout.connect(self._update_mode)
        self._mode_timer.start(50)  # poll every 0.5s

        self._heartbeat_timer = QTimer()
        self._heartbeat_timer.timeout.connect(self._check_heartbeat)
        self._heartbeat_timer.start(1000)  # check every 1s

        self._last_heartbeat = None
        self._heartbeat_received = False


    def _update_mode(self):
        if self.vehicle:
            try:
                mode_name = self.vehicle.mode.name
                if self.state.mode != mode_name:
                    self.state.mode = mode_name
                    self.push_state()  # notify GUI
            except Exception as e:
                print(f"Mode poll error: {e}")

    def _check_heartbeat(self):
        if not self.vehicle:
            return

        if not self._heartbeat_received:
            return  # no heartbeat received yet

        elapsed = time.time() - self._last_heartbeat
        if elapsed > 5:  # 5 seconds timeout
            if self.state.connection_state != FlightState.DISCONNECTED:
                # print("No heartbeat detected! Marking as disconnected.")
                self.state.connection_state = FlightState.DISCONNECTED
                self.connection_state_changed.emit(self.state.connection_state)
        # else:
        #     if self.state.connection_state == FlightState.DISCONNECTED:
        #         print("No heartbeat detected! Marking as disconnected.")
        #         self.state.connection_state = FlightState.CONNECTED
        #         self.connection_state_changed.emit(self.state.connection_state)

    @staticmethod
    def detect_com_ports():
        return [p.device for p in serial.tools.list_ports.comports()]

    @staticmethod
    def detect_bt_ports():
        ports = serial.tools.list_ports.comports()
        bt_ports = []
        for p in ports:
            # print(f"Name: {p.device}")
            # print(f"Description: {p.description}")
            # print(f"HWID: {p.hwid}")
            if "Bluetooth" in p.description:
                for i in range(1,10):
                    is_client = f"localmfg&000{i}" in p.hwid.lower() if "BTHENUM" in p.hwid else False
                    if is_client:
                        bt_ports.append((p.device, is_client))
                is_client = "localmfg&000a" in p.hwid.lower() if "BTHENUM" in p.hwid else False
                # print((p.device, is_client), p.hwid.lower())
                bt_ports.append((p.device, is_client))
        return bt_ports

    def register_ui_callback(self, callback):
        """
        callback(state: FlightState)
        """
        self._ui_callback = callback

    def push_state(self):
        if self._ui_callback:
            self._ui_callback(self.state)

    # def push_state(self):
    #     if not self._ui_callback:
    #         return
    #     # Ensure GUI-thread execution
    #     QTimer.singleShot(0, lambda: self._ui_callback(self.state))

    def connect_auto(self):
        """Try to auto-connect to available COM ports in background."""
        self.stop_connect_thread()

        if self._thread and self._thread.is_alive():
            return
        self._stop_flag = False
        self._thread = Thread(target=self._connect_thread, daemon=True)
        self._thread.start()

    def _connect_thread(self):
        ports = self.detect_bt_ports()
        client_ports = [p for p, is_client in ports if is_client]

        self.state.connection_state = FlightState.CONNECTING
        self.connection_state_changed.emit(self.state.connection_state)

        for port in client_ports:
            if self._stop_flag:
                return
            try:
                print(f"Trying to connect to {port}...")
                self.vehicle = connect(port, baud=115200, heartbeat_timeout=5, wait_ready=False, timeout=5)
                self.connected_port = port
                print(f"Connected to {port}")
                self.state.initialized = True
                self.state.connection_state = FlightState.CONNECTED
                self.connection_state_changed.emit(self.state.connection_state)
                self._setup_listeners()
                self.state.mode = self.vehicle.mode.name
                self.push_state()
                break
            except Exception as e:
                print(f"Failed to connect to {port}: {e}")
                self.vehicle = None
                time.sleep(1)
        if not self.vehicle:
            print("No available ports, will retry later.")
            self.state.connection_state = FlightState.ERROR
            self.connection_state_changed.emit(self.state.connection_state)

    def _setup_listeners(self):
        """Set up message listeners for telemetry updates."""
        if not self.vehicle:
            return

        @self.vehicle.on_message('BATTERY_STATUS')
        def battery_listener(_, __, msg):
            try:
                voltage = msg.voltages[0] / 1000.0
                self.state.battery = voltage
                self.push_state()
            except Exception as e:
                print(f"Battery listener error: {e}")

        @self.vehicle.on_message('RANGEFINDER')
        def rangefinder_listener(_, __, msg):
            try:
                self.state.altitude = round(msg.distance + 0.05, 4)
                self.push_state()

            except Exception as e:
                print(f"Rangefinder listener error: {e}")

        # @self.vehicle.on_message('GLOBAL_POSITION_INT')
        # def position_listener(_, __, msg):
        #     try:
        #         self.state.altitude = msg.relative_alt / 1000.0  # meters
        #         self.push_state()
        #     except Exception as e:
        #         print(f"Position listener error: {e}")

        @self.vehicle.on_message('GPS_RAW_INT')
        def gps_listener(_, __, msg):
            try:
                self.state.hdop = msg.eph / 100.0
                self.state.satellites = msg.satellites_visible
                self.push_state()
            except Exception as e:
                print(f"GPS listener error: {e}")

        @self.vehicle.on_message('HEARTBEAT')
        def heartbeat_listener(_, __, msg):
            self._last_heartbeat = time.time()
            self._heartbeat_received = True

        @self.vehicle.on_message('STATUSTEXT')
        def statustext_listener(_, __, msg):
            try:
                if msg.severity != 6:
                    return
                # Debug (recommended during dev)
                print(f"[RX STATUSTEXT] {msg.text}")

                # Forward to parser
                self.state.update_from_message(msg.text)
                self.push_state()

            except Exception as e:
                print(f"STATUSTEXT listener error: {e}")

    def connect_async(self, com_port):
        self.stop_connect_thread()

        if self._thread and self._thread.is_alive():
            return

        self._stop_flag = False
        self._thread = Thread(
            target=self._connect_worker,
            args=(com_port,),
            daemon=True
        )
        self._thread.start()

    def _connect_worker(self, com_port):
        self._hard_disconnect()

        self.state.connection_state = FlightState.CONNECTING
        self.connection_state_changed.emit(self.state.connection_state)

        try:
            print(f"Connecting to {com_port}...")
            self.vehicle = connect(
                com_port,
                baud=115200,
                wait_ready=False,
                heartbeat_timeout=10,
                timeout=10
            )
            self.connected_port = com_port
            print(f"Connected to {com_port}")
            self._setup_listeners()
            self.state.mode = self.vehicle.mode.name
            self.state.connection_state = FlightState.CONNECTED
            self.state.initialized = True
            self.push_state()

        except Exception as e:
            print(f"Failed to connect to {com_port}: {e}")
            self.vehicle = None
            self.connected_port = None
            self.state.connection_state = FlightState.ERROR

        # Notify GUI safely
        self.connection_state_changed.emit(self.state.connection_state)

    def _hard_disconnect(self):
        self._stop_flag = True

        if self.vehicle:
            try:
                self.vehicle.close()
                time.sleep(0.5)
            except Exception as e:
                print("Disconnect error:", e)

        self.vehicle = None
        self.connected_port = None

        self._heartbeat_received = False
        self._last_heartbeat = None

    def stop_connect_thread(self):
        print("stop connected thread")
        self._stop_flag = True
        if self._thread and self._thread.is_alive() and self._thread != Thread.current_thread():
            self._thread.join(timeout=0.2)

    def stop(self):
        self._stop_flag = True
        if self.vehicle:
            try:
                self.vehicle.close()
            except:
                pass

    def on_message_received(self, msg: str):
        """
        Called whenever a packet is received from flight
        """
        self.state.update_from_message(msg)

        if self._ui_callback:
            self._ui_callback(self.state)

    def send_statustext(self, message_text: str, severity: int = 6) -> bool:
        """
        Send a MAVLink STATUSTEXT message via DroneKit.

        severity:
            0 = EMERGENCY
            3 = ERROR
            4 = WARNING
            6 = INFO
        """
        if not self.vehicle:
            print("❌ Cannot send: vehicle not connected")
            return False

        # MAVLink STATUSTEXT limit
        if len(message_text) > 50:
            message_text = message_text[:50]

        try:
            msg_bytes = message_text.encode("utf-8")
        except UnicodeEncodeError:
            print("❌ UTF-8 encode failed")
            return False

        for attempt in range(5):
            try:
                self.vehicle.send_mavlink(
                    self.vehicle.message_factory.statustext_encode(
                        severity,
                        msg_bytes
                    )
                )
                return True
            except Exception as e:
                time.sleep(0.05)

        print("❌ Failed to send STATUSTEXT after retries")
        return False

    def send_operation(self, op: int):
        # Op:1 or Op:2
        # msg = f"Op:{op}"
        # print(msg)
        # self.send_statustext(msg)
        if op ==1:
            self.send_cancel()

        else:
            self.send_start()

    def send_mode(self, md: int):
        # Md:1 = Manual, Md:2 = Automatic
        mode = "MANUAL" if md==1 else "AUTOMAT"
        msg = f"{mode}"
        print(msg)
        self.send_statustext(msg)

    def send_speed(self, spd: float):
        msg = f"Speed {spd:.1f} → Pitch {spd:.1f}"
        print(msg)
        self.send_statustext(msg)

    def send_class(self, cls: int):
        # Cls:0 = Person, Cls:2 = Car
        msg = f"CLASS:{cls},Notcare:{cls}"
        print(msg)
        self.send_statustext(msg)

    def send_start(self):
        print("start")
        self.send_statustext("START:TRUE,Notcare:TRUE")

    def send_cancel(self):
        print("cancel")
        self.send_statustext("CANCEL:TRUE,Notcare:TRUE")

    def send_target_position(self, x: int, y: int):
        """Send target position to flight controller when user clicks."""
        msg = f"Pos:{x},{y}"
        print(f"📤 Sending Position: {msg}")
        self.send_statustext(msg)

    def send_zoom(self, zoom_level: float):
        """Send zoom command (1.0 to 10.0)"""
        if not (1.0 <= zoom_level <= 10.0):
            print(f"❌ Zoom level must be between 1.0 and 10.0 (got {zoom_level})")
            return
        
        msg = f"Zoom:{zoom_level:.1f}"
        print(f"📤 Sending Zoom: {msg}")
        self.send_statustext(msg)
    def send_pitch(self, pitch_value: float):
        """Send pitch command (مثلاً زاویه steering)"""
        if not (0 <= pitch_value <= 90):   # محدوده دلخواه تو
            print(f"❌ Pitch value must be between 0 and 90 (got {pitch_value})")
            return
        
        msg = f"Pitch:{pitch_value:.1f}"
        print(f"📤 Sending Pitch: {msg}")
        self.send_statustext(msg)