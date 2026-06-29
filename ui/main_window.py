import cv2
from PySide6.QtWidgets import QMainWindow, QWidget, QLabel, QVBoxLayout, QSizePolicy, QMenu, QHBoxLayout, QVBoxLayout
from PySide6.QtCore import Qt, QSize,QTimer
from PySide6.QtGui import QImage, QPixmap, QAction, QPainter, QColor
import serial.tools.list_ports

from ui.slider import CustomSlider
from ui.widgets import ControlBar
from core.camera_interface import CameraInterface
from core.flight_interface import FlightState
from utils.frame_overlay import cornerRect
from ui.battery_widget import BatteryWidget
from ui.satellite_widget import SatelliteWidget
from ui.altitude_widget import AltitudeWidget
from ui.hdop_widget import HdopWidget
from ui.mode_widget import ModeWidget
from ui.slider_buttons_zoom import DPadWidget_Zoom




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
                # تم دارک برای پنجره اصلی
        self.setStyleSheet("""
            QMainWindow {
                background-color: #1e1e1e;
            }
            QMenuBar {
                background-color: #2d2d2d;
                color: #ffffff;
            }
            QMenuBar::item:selected {
                background-color: #3d3d3d;
            }
            QMenu {
                background-color: #2d2d2d;
                color: #ffffff;
            }
            QMenu::item:selected {
                background-color: #3d3d3d;
            }
        """)
        self.resize(960, 650)

        self.camera = CameraInterface()
        self.camera.frame_ready.connect(self._update_frame)
        self.camera.camera_error.connect(self._on_camera_error)
        self.mouse_x = 0
        self.mouse_y = 0
        self.current_zoom=1
        self.current_steer = 0 


        self._setup_ui()
        self._setup_menubar()
        self._setup_connection_indicator()
        self._start_default_camera()
        self._setup_overlay_controls()
        self.zoom_value_label = QLabel(self.video_label)  # parent = video_label
        self.zoom_value_label.setStyleSheet("""
            QLabel {
                background-color: rgba(0, 0, 0, 200);
                color: #FFD700;
                font-size: 14px;
                font-weight: bold;
                border-radius: 6px;
                padding: 4px 8px;
            }
        """)
        self.zoom_value_label.setAlignment(Qt.AlignCenter)
        self.zoom_value_label.setText("1.0X")
        self.zoom_value_label.resize(50, 28)
        self.zoom_value_label.setAttribute(Qt.WA_TransparentForMouseEvents)
        self.steer_value_label = QLabel(self.video_label)
        self.steer_value_label.setStyleSheet("""
            QLabel {
                background-color: rgba(0, 0, 0, 200);
                color: #00BFFF;
                font-size: 14px;
                font-weight: bold;
                border-radius: 6px;
                padding: 4px 8px;
            }
        """)
        self.steer_value_label.setAlignment(Qt.AlignCenter)
        self.steer_value_label.setText("0°")
        self.steer_value_label.resize(70, 28)
        self.steer_value_label.setAttribute(Qt.WA_TransparentForMouseEvents)

        # Container برای باتری و ماهواره
        self.top_left_container = QWidget(self.video_label)
        self.top_left_container.setStyleSheet("background-color: transparent;")
        self.top_left_container.setAttribute(Qt.WA_TransparentForMouseEvents)

        self.zoom_buttons = DPadWidget_Zoom( 
            main_window=self,
            min_value=1.0, 
            max_value=10.0, 
            step=2,
            parent=self.video_label 
)
        self.pitch_slider = CustomSlider(
    main_window=self,
    parent=self.video_label  
)
          


        container_layout = QHBoxLayout(self.top_left_container)
        container_layout.setContentsMargins(0, 0, 0, 0)
        container_layout.setSpacing(10)
        self.battery_widget = BatteryWidget(22.0)
        self.satellite_widget = SatelliteWidget()
        self.altitude_widget = AltitudeWidget()
        self.hdop_widget = HdopWidget()
        self.mode_widget = ModeWidget()

        container_layout.addWidget(self.battery_widget)
        container_layout.addWidget(self.satellite_widget)
        container_layout.addWidget(self.altitude_widget)
        container_layout.addWidget(self.hdop_widget)
        container_layout.addWidget(self.mode_widget)  
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
                background-color: #0d0d0d;
                color: #cccccc;
                border: 2px solid #3d3d3d;
            }
        """)
        self.layout.addWidget(self.video_label, stretch=1)

        self.controls_overlay = QWidget(self.video_label)
        self.controls_overlay.setStyleSheet("background-color: transparent;")
        self.controls_overlay.setGeometry(0, 0, 100, 90)
       
        self.video_label.setMouseTracking(True)
        self.video_label.mouseMoveEvent = self.on_video_mouse_move
        self.video_label.mousePressEvent = self.on_video_mouse_press   # ← ADD THIS LINE


    def _setup_menubar(self):
        menubar = self.menuBar()
        # ---------------- Camera Menu ----------------
        camera_menu = menubar.addMenu("Camera")
        self.camera_actions = []

        cameras = self.camera.detect_cameras()
        for idx in cameras:
            action = QAction(f"Camera {idx}", self)
            action.setCheckable(True)
            action.triggered.connect(lambda _, i=idx: self._switch_camera(i))
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

            action.triggered.connect(lambda _, d=device: self._switch_com(d))
            flight_menu.addAction(action)
            self.flight_actions.append(action)

    def _setup_overlay_controls(self):
        fi = self.app_controller.flight_interface
        self.control_bar = ControlBar(fi, parent=self.controls_overlay)
        
        layout = QHBoxLayout(self.controls_overlay)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.control_bar)
        QTimer.singleShot(100, self._update_overlay_position)

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

    def _update_buttons_position(self):
        # موقعیت D-Pad زوم (سمت راست)
        if hasattr(self, 'zoom_buttons') and hasattr(self, 'video_label'):
            x = self.video_label.width() - self.zoom_buttons.width() - 30
            y = (self.video_label.height() - self.zoom_buttons.height()) // 2
            self.zoom_buttons.move(x, y)
            self.zoom_buttons.raise_()
            
            # موقعیت label زوم (بالای D-Pad زوم)
            if hasattr(self, 'zoom_value_label'):
                label_x = x + (self.zoom_buttons.width() - self.zoom_value_label.width()) // 2
                label_y = y - self.zoom_value_label.height() - 10
                self.zoom_value_label.move(label_x, label_y)
                self.zoom_value_label.raise_()

        if hasattr(self, 'pitch_slider') and hasattr(self, 'video_label'):
            x = 30
            y = (self.video_label.height() - self.pitch_slider.height()) // 2
            self.pitch_slider.move(x, y)
            self.pitch_slider.raise_()
            
            if hasattr(self, 'steer_value_label') and hasattr(self, 'pitch_slider'):
                slider_x = x  
                slider_y = y 
                slider_width = self.pitch_slider.width()
                
                label_width = self.steer_value_label.width()
                label_height = self.steer_value_label.height()
            
                label_x = slider_x + 30 - label_width // 2
                label_y = slider_y 
                
                self.steer_value_label.move(label_x, label_y)
                self.steer_value_label.raise_()
              

        



    def _update_from_flight(self, state):
        self.control_bar.update_from_flight(
            op=state.op,
            mode=state.md,
            spd=state.spd,
            cls=state.cls,
            initialized=state.initialized
        )
        if hasattr(self, 'battery_widget') and state.battery is not None:
            self.battery_widget.setVoltage(state.battery)

        if hasattr(self, 'satellite_widget'):
            self.satellite_widget.setSatellites(state.satellites)
        if hasattr(self, 'altitude_widget'):
             self.altitude_widget.setAltitude(state.altitude)
        if hasattr(self, 'hdop_widget'):
            self.hdop_widget.setHdop(state.hdop)
        if hasattr(self, 'mode_widget'):
            self.mode_widget.setMode(state.mode)

        # ========== آپدیت زوم ==========
        if hasattr(self, 'zoom_buttons') and state.zoom is not None:
            self.zoom_buttons.current_value = state.zoom
            self.zoom_buttons.update()  # بازکشیدن D-Pad (اختیاری)
            if hasattr(self, 'zoom_value_label'):
                self.zoom_value_label.setText(f"{int(state.zoom)}X")
                
        if hasattr(self, 'pitch_slider') and state.pitch is not None:
            self.pitch_slider.set_steer(state.pitch)
            if hasattr(self, 'steer_value_label'):
                self.steer_value_label.setText(f"{int(state.pitch)}°")



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
        display_frame = rgb.copy()
        if hasattr(self, 'mouse_x') and hasattr(self, 'mouse_y'):
            # تبدیل موقعیت موس از QLabel به فریم
            mouse_frame_x = int(self.mouse_x * w / max(self.video_label.width(), 1))
            mouse_frame_y = int(self.mouse_y * h / max(self.video_label.height(), 1))



            reticle_size = 50
            half = reticle_size // 2

            x = mouse_frame_x
            y = mouse_frame_y

            display_frame = cornerRect(
                display_frame,
                (x - half, y - half, reticle_size, reticle_size),
                l=18,
                t=2,
                t_center=2,
                a_c=4,
                m_p=3,
                colorR=(0, 255, 120),
                colorC=(0, 255, 120)
            )
        # =================================================================
        # Rest of your code (QImage + overlay + scaling)
        image = QImage(display_frame.data, w, h, bytes_per_line, QImage.Format_RGB888)
        pixmap = QPixmap.fromImage(image)

        scaled_pixmap = pixmap.scaled(
            self.video_label.size(),
            Qt.IgnoreAspectRatio,
            Qt.SmoothTransformation
        )
        self.video_label.setPixmap(scaled_pixmap)
        if hasattr(self, 'controls_overlay'):
                self._update_overlay_position()
                self._update_steer_position()

    def on_video_mouse_press(self, event):
        if event.button() == Qt.LeftButton:
            label_x = event.position().x()
            label_y = event.position().y()

            # ابعاد واقعی دوربین پرنده (720p)
            frame_w = 1280
            frame_h = 720

            real_x = int(label_x * frame_w / max(self.video_label.width(), 1))
            real_y = int(label_y * frame_h / max(self.video_label.height(), 1))

            print(f"🖱️ Clicked → Label({label_x:.0f}, {label_y:.0f}) → Frame({real_x}, {real_y})")
            self.app_controller.flight_interface.send_target_position(real_x, real_y)
        



    def on_video_mouse_move(self, event):
        """Track mouse movement over video"""
        self.mouse_x = event.position().x()
        self.mouse_y = event.position().y()
    def _on_camera_error(self, msg):
        self.video_label.setText(msg)

    def resizeEvent(self, event):
        """تنظیم مجدد موقعیت overlay هنگام تغییر سایز پنجره"""
        # تاخیر برای گرفتن ارتفاع جدید بعد از resize
        QTimer.singleShot(10, self._update_controls_visibility)
        QTimer.singleShot(10, self._update_top_left_position)
        QTimer.singleShot(10, self._update_buttons_position)
        QTimer.singleShot(10, self._update_steer_position)
        if hasattr(self, 'controls_overlay') and hasattr(self, 'video_label'):
            QTimer.singleShot(10, self._update_overlay_position)
        super().resizeEvent(event)

    def update_zoom_display(self, value):
        """ارسال زوم به flight controller (بدون آپدیت UI)"""
        print(f"Zoom: {value:.1f}x")
        self.app_controller.flight_interface.send_zoom(value)

    def update_steer_display(self, value):
        """ارسال زاویه به flight controller (بدون آپدیت UI)"""
        print(f"Steer: {value:.0f}°")
        self.app_controller.flight_interface.send_pitch(value)

    def _update_overlay_position(self):
        if not hasattr(self, 'controls_overlay') or not hasattr(self, 'video_label'):
            return
        
        label_width = self.video_label.width()
        label_height = self.video_label.height()
        
        if label_width < 100 or label_height < 100:
            return
        
        # محاسبه عرض و ارتفاع بر اساس سایز صفحه
        if label_height > 600:
            overlay_height = 130  # ارتفاع بیشتر برای فول اسکرین
        else:
            overlay_height = 115   # ارتفاع معمولی
        
        overlay_width = min(int(label_width * 0.95), 1600)
        overlay_width = max(overlay_width, 1200)
        
        x = (label_width - overlay_width) // 2
        y = label_height - overlay_height - 5
        
        x = max(0, x)
        y = max(0, y)
        
        self.controls_overlay.setGeometry(x, y, overlay_width, overlay_height)
        self.controls_overlay.raise_()


    def _update_steer_position(self):
        if not hasattr(self, 'pitch_slider') or not hasattr(self, 'video_label'):
            return
        
        label_width = self.video_label.width()
        label_height = self.video_label.height()  
        if label_width < 100 or label_height < 100:
            return
        
        if label_width < 900:
            self.pitch_slider.hide()
            if hasattr(self, 'steer_value_label'):
                self.steer_value_label.hide()
            return
        else:
            self.pitch_slider.show()
            if hasattr(self, 'steer_value_label'):
                self.steer_value_label.show()
        
        slider_width = 80
        slider_height = 400
        x = 20
        y = (label_height - slider_height) // 2
        
        self.pitch_slider.setGeometry(x, y, slider_width, slider_height)
        self.pitch_slider.raise_()
        



                    


    def _update_top_left_position(self):
        if hasattr(self, 'top_left_container') and hasattr(self, 'video_label'):
            # محاسبه عرض کل container
            container_width = self.top_left_container.sizeHint().width()
            if container_width <= 0:
                container_width = 85 * 5 + 40  # تقریباً 5 ویجت × 85 + فاصله‌ها
            
            label_width = self.video_label.width()
            
            # وسط چین: (عرض video_label - عرض container) / 2
            x = (label_width - container_width) // 2
            y = 15
            
            self.top_left_container.move(x, y)
            self.top_left_container.raise_()




    def showEvent(self, event):
        """وقتی پنجره نمایش داده می‌شود"""
        super().showEvent(event)
        # تاخیر برای گرفتن ارتفاع واقعی بعد از render کامل
        QTimer.singleShot(100, self._update_controls_visibility)
        QTimer.singleShot(50, self._update_overlay_position)
        QTimer.singleShot(10, self._update_top_left_position)
        QTimer.singleShot(10, self._update_buttons_position)
        QTimer.singleShot(10, self._update_steer_position)


    def _update_controls_visibility(self):
        """بروزرسانی visibility اسلایدرها بر اساس ارتفاع واقعی"""
        window_height = self.height()
        print(f"Real window height: {window_height}")
        

     
    def closeEvent(self, event):
        self.camera.stop_camera()
        super().closeEvent(event)