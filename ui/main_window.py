import cv2
from PySide6.QtWidgets import QMainWindow, QWidget, QLabel, QVBoxLayout, QSizePolicy, QMenu, QHBoxLayout, QVBoxLayout
from PySide6.QtCore import Qt, QSize
from PySide6.QtGui import QImage, QPixmap, QAction, QPainter, QColor
import serial.tools.list_ports

from ui.widgets import OperationGroup, ModeGroup, SpeedGroup, ClassGroup,ZoomGroup
from core.camera_interface import CameraInterface
from core.flight_interface import FlightState
from utils.frame_overlay import FrameOverlay

class ConnectionIndicator(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._color = QColor("red")
        self.setFixedSize(14, 14)

    def set_color(self, color: QColor):
        self._color = color
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setBrush(self._color)
        painter.setPen(Qt.NoPen)
        painter.drawEllipse(0, 0, self.width(), self.height())


class MainWindow(QMainWindow):
    def __init__(self, app_controller):
        super().__init__()

        self.app_controller = app_controller
        self.setWindowTitle("Drone Control GUI")
        self.resize(1100, 700)

        self.camera = CameraInterface()
        self.camera.frame_ready.connect(self._update_frame)
        self.camera.camera_error.connect(self._on_camera_error)



        self._setup_ui()
        self._setup_menubar()
        self._setup_connection_indicator()
        self._start_default_camera()
        self._setup_control_groups()

        self.app_controller.flight_interface.register_ui_callback(
            self._update_from_flight
        )
        self.app_controller.flight_interface.connection_state_changed.connect(
            self._update_connection_indicator
        )
        self.app_controller.flight_interface.connect_auto()
        self.app_controller.flight_interface.push_state()

    def _setup_ui(self):
        central_widget = QWidget()
        self.setCentralWidget(central_widget)

        self.layout = QVBoxLayout(central_widget)

        self.video_label = QLabel()
        self.video_label.setMinimumSize(640, 480)
        self.video_label.setMaximumSize(1920, 1080)
        self.video_label.setAlignment(Qt.AlignCenter)
        self.video_label.setSizePolicy(
            QSizePolicy.Expanding,
            QSizePolicy.Expanding
        )
        self.video_label.setScaledContents(True)
        self.video_label.setStyleSheet("""
            QLabel {
                background-color: #1e1e1e;
                color: #aaaaaa;
                border: 2px solid #333;
            }
        """)
        self.layout.addWidget(self.video_label, stretch=1)

    def _setup_control_groups(self):
        container = QWidget()
        container_layout = QHBoxLayout()
        container_layout.setSpacing(15)
        container_layout.setContentsMargins(0, 0, 0, 0)
        container.setLayout(container_layout)

        fi = self.app_controller.flight_interface
        self.operation_group = OperationGroup(fi)
        self.mode_group = ModeGroup(fi)
        self.speed_group = SpeedGroup(fi)
        self.class_group = ClassGroup(fi)
        self.zoom_group = ZoomGroup(fi)           # ← NEW

        for g in [self.operation_group, self.mode_group, self.speed_group, self.class_group,self.zoom_group]:
            g.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
            container_layout.addWidget(g)

        self.layout.addWidget(container)

    def _setup_menubar(self):
        menubar = self.menuBar()

        # ---------------- Camera Menu ----------------
        camera_menu = menubar.addMenu("Camera")
        self.camera_actions = []

        cameras = self.camera.detect_cameras()
        for idx in cameras:
            action = QAction(f"Camera {idx}", self)
            action.setCheckable(True)
            action.triggered.connect(lambda checked, i=idx: self._switch_camera(i))
            camera_menu.addAction(action)
            self.camera_actions.append(action)

        if self.camera_actions:
            self.camera_actions[0].setChecked(True)

        # ---------------- Flight Controller (Bluetooth COM) Menu ----------------
        flight_menu = menubar.addMenu("Flight Controller")
        self.flight_actions = []

        ports = self.app_controller.flight_interface.detect_bt_ports()
        for device, is_client in ports:
            label = device
            if is_client:
                label = f"{device} *"  # mark the client port
            action = QAction(label, self)
            action.setCheckable(True)

            action.triggered.connect(lambda checked, d=device: self._switch_com(d))
            flight_menu.addAction(action)
            self.flight_actions.append(action)

        # for a in self.flight_actions:
        #     if "*" in a.text():
        #         a.setChecked(True)
        #         self.app_controller.flight_interface.connect(a.text().replace(" *", ""))

    def _setup_connection_indicator(self):
        # ===========================================================
        # menuBar()    = The kitchen counter
        # container    = A small tray on the counter
        # layout       = The rules for how things are arranged on the tray
        # indicator    = The actual object (a coffee cup) on the tray
        # ===========================================================
        container = QWidget(self.menuBar())  # 👈 parented
        layout = QHBoxLayout(container)
        layout.setContentsMargins(0, 0, 10, 0)

        self.connection_indicator = ConnectionIndicator(container)  # 👈 parented
        layout.addWidget(self.connection_indicator)

        self.menuBar().setCornerWidget(container, Qt.TopRightCorner)

    def _update_connection_indicator(self, connection_state):
        # print("mani_window:_update_connection_indicator: Connection state:", connection_state)
        # 🔵 Connection indicator
        if connection_state == FlightState.CONNECTED:
            self.connection_indicator.set_color(QColor("green"))
            is_connected = True
        elif connection_state == FlightState.CONNECTING:
            self.connection_indicator.set_color(QColor("orange"))
            is_connected = False
        elif connection_state == FlightState.ERROR:
            self.connection_indicator.set_color(QColor("red"))
            is_connected = False
        else:
            self.connection_indicator.set_color(QColor("black"))
            is_connected = False

        self.operation_group.setEnabled(is_connected)
        self.mode_group.setEnabled(is_connected)
        self.speed_group.setEnabled(is_connected)
        self.class_group.setEnabled(is_connected)

    def _update_from_flight(self, state):
        # print("Connection state:", state.connection_state)
        # self._update_connection_indicator(state.connection_state)

        self.operation_group.update_from_flight(state.op, state.mode, state.initialized)
        self.mode_group.update_from_flight(state.md, state.initialized)
        self.speed_group.update_from_flight(state.spd, state.op, state.st, state.initialized)
        self.class_group.update_from_flight(state.cls, state.op, state.st, state.initialized)
        self.zoom_group.update_from_flight(state.zoom, state.initialized)


    def _switch_camera(self, camera_index):
        # uncheck all camera menu items
        for a in self.camera_actions:
            a.setChecked(False)
        # check selected
        for a in self.camera_actions:
            if a.text() == f"Camera {camera_index}":
                a.setChecked(True)
        # restart camera
        self.camera.start_camera(camera_index)

    def _switch_com(self, com_port):
        # uncheck all COM menu items
        for a in self.flight_actions:
            a.setChecked(False)
        # check selected
        for a in self.flight_actions:
            if a.text() == com_port:
                a.setChecked(True)
        # tell controller to connect
        # self.app_controller.flight_interface.connect(com_port)
        self.app_controller.flight_interface.connect_async(com_port)

    def _start_default_camera(self):
        cameras = self.camera.detect_cameras()
        if cameras:
            self.camera.start_camera(cameras[0])
        else:
            self.video_label.setText("No camera detected")

    def _update_frame(self, frame):
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        h, w, ch = rgb.shape
        bytes_per_line = ch * w
        image = QImage(rgb.data, w, h, bytes_per_line, QImage.Format_RGB888)
        pixmap = QPixmap.fromImage(image)

        overlay = FrameOverlay(self.app_controller.flight_interface.state)
        pixmap = overlay.draw(pixmap)

        self.video_label.setPixmap(pixmap.scaled(
            self.video_label.size(),
            Qt.IgnoreAspectRatio,
            Qt.SmoothTransformation
        ))

    def _on_camera_error(self, msg):
        self.video_label.setText(msg)

    def closeEvent(self, event):
        self.camera.stop_camera()
        super().closeEvent(event)