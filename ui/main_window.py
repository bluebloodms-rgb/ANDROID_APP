import cv2
from PySide6.QtWidgets import QMainWindow, QWidget, QLabel, QVBoxLayout, QSizePolicy, QMenu, QHBoxLayout, QVBoxLayout
from PySide6.QtCore import Qt, QSize,QTimer
from PySide6.QtGui import QImage, QPixmap, QAction, QPainter, QColor
import serial.tools.list_ports

from ui.widgets import ControlBar
from core.camera_interface import CameraInterface
from core.flight_interface import FlightState
from utils.frame_overlay import FrameOverlay, cornerRect
from ui.zoom_slider import CustomZoomSlider
from ui.steer_slider import CustomSteerSlider

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
        self.resize(1100, 700)

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

            # اضافه کردن اسلایدر زوم (بعد از خط بالا)
        self.zoom_overlay = QWidget(self.video_label)
        self.zoom_overlay.setStyleSheet("background-color: transparent;")
        self.zoom_slider = CustomZoomSlider(self.zoom_overlay, main_window=self)
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


        self.steer_overlay = QWidget(self.video_label)
        self.steer_overlay.setStyleSheet("background-color: transparent;")
        self.steer_slider = CustomSteerSlider(self.steer_overlay, main_window=self)

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
        self.steer_value_label.resize(50, 28)


        

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
        # Overlay container برای کنترل‌ها (روی تصویر)
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

        # for a in self.flight_actions:
        #     if "*" in a.text():
        #         a.setChecked(True)
        #         self.app_controller.flight_interface.connect(a.text().replace(" *", ""))

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



    def _update_from_flight(self, state):
        self.control_bar.update_from_flight(
            op=state.op,
            mode=state.md,
            spd=state.spd,
            cls=state.cls,
            initialized=state.initialized
        )
        if state.st == 2:
            # =========================================================
            # بروزرسانی مقادیر اسلایدرها فقط در حالت TRACK (st == 2)
            # در این حالت، پرنده در حال تعقیب هدف است و مقادیر زوم و زاویه
            # از سمت Flight Controller دریافت و روی اسلایدرها نمایش داده می‌شود
            # =========================================================
            if hasattr(self, 'zoom_slider') and state.zoom is not None:
                self.zoom_slider.set_zoom(state.zoom)
                self.current_zoom = state.zoom
                if hasattr(self, 'zoom_value_label'):
                    self.zoom_value_label.setText(f"{state.zoom:.1f}X")

                    # ========== بروزرسانی اسلایدر زاویه (Steer / Pitch) ==========
            if hasattr(self, 'steer_slider') and state.pitch is not None:
                # pitch در محدوده 0 تا 90 درجه است
                self.steer_slider.set_steer(state.pitch)
                self.current_steer = state.pitch
                
                if hasattr(self, 'steer_value_label'):
                    self.steer_value_label.setText(f"{state.pitch:.0f}°")
            # ===================================================================

        



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

        overlay = FrameOverlay(self.app_controller.flight_interface.state)
        pixmap = overlay.draw(pixmap)

        scaled_pixmap = pixmap.scaled(
            self.video_label.size(),
            Qt.IgnoreAspectRatio,
            Qt.SmoothTransformation
        )
        self.video_label.setPixmap(scaled_pixmap)
        if hasattr(self, 'controls_overlay'):
                self._update_overlay_position()
                self._update_zoom_position()  # اضافه کن
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
        if hasattr(self, 'controls_overlay') and hasattr(self, 'video_label'):
            # تاخیر کوچک برای اطمینان از اعمال اندازه‌های جدید
            QTimer.singleShot(10, self._update_overlay_position)

        if hasattr(self, 'zoom_overlay') and hasattr(self, 'video_label'):
            QTimer.singleShot(10, self._update_zoom_position)  # 
        if hasattr(self, 'steer_overlay') and hasattr(self, 'video_label'):
                QTimer.singleShot(10, self._update_steer_position)
        

    # ====================================
    
        super().resizeEvent(event)
    def _update_zoom_position(self):
        """به‌روزرسانی موقعیت اسلایدر زوم (سمت راست، وسط عمودی)"""
        if not hasattr(self, 'zoom_overlay') or not hasattr(self, 'video_label'):
            return
        
        label_width = self.video_label.width()
        label_height = self.video_label.height()
        
        if label_width < 100 or label_height < 100:
            return
        
        slider_width = 80
        slider_height = 400
        x = label_width - slider_width - 20
        y = (label_height - slider_height) // 2
        
        self.zoom_overlay.setGeometry(x, y, slider_width, slider_height)
        self.zoom_overlay.raise_()
        # ========== موقعیت label بالای اسلایدر ==========
        if hasattr(self, 'zoom_value_label'):
            label_x = x + 15  # same x as slider + offset
            label_y = y - 30  # بالای اسلایدر
            self.zoom_value_label.setGeometry(label_x, label_y, 60, 32)
            self.zoom_value_label.raise_()
        # ===============================================


    def _update_steer_position(self):
            """به‌روزرسانی موقعیت اسلایدر زاویه (سمت چپ، وسط عمودی)"""
            if not hasattr(self, 'steer_overlay') or not hasattr(self, 'video_label'):
                return
            
            label_width = self.video_label.width()
            label_height = self.video_label.height()
            
            if label_width < 100 or label_height < 100:
                return
            
            slider_width = 80
            slider_height = 400
            x = 20  # سمت چپ
            y = (label_height - slider_height) // 2
            
            self.steer_overlay.setGeometry(x, y, slider_width, slider_height)
            self.steer_overlay.raise_()
            
            # موقعیت label بالای اسلایدر
            if hasattr(self, 'steer_value_label'):
                label_x = x + 15
                label_y = y - 30
                self.steer_value_label.setGeometry(label_x, label_y, 50, 28)
                self.steer_value_label.raise_()

    def update_zoom_display(self, value):
        print(f"Zoom: {value:.1f}x")
        if hasattr(self, 'zoom_value_label'):
            self.zoom_value_label.setText(f"{value:.1f}X")
        self.app_controller.flight_interface.send_zoom(value)
    def update_steer_display(self, value):
        """آپدیت نمایش زاویه و ارسال به flight controller"""
        print(f"Steer: {value:.0f}°")
        if hasattr(self, 'steer_value_label'):
            self.steer_value_label.setText(f"{value:.0f}°")
        self.app_controller.flight_interface.send_pitch(value)

        
    def _update_overlay_position(self):
        """به‌روزرسانی موقعیت نوار کنترل"""
        if not hasattr(self, 'controls_overlay') or not hasattr(self, 'video_label'):
            return
        
        # پهنای ثابت برای نوار
        overlay_width = 1100
        label_width = self.video_label.width()
        label_height = self.video_label.height()
        
        # اگر هنوز اندازه معتبر نداره، صبر کن
        if label_width < 100 or label_height < 100:
            return
        
        # محاسبه موقعیت: وسط افقی، پایین عمودی
        x = (label_width - overlay_width) // 2
        y = label_height - 100
        
        # اطمینان از اینکه موقعیت منفی نباشد
        x = max(0, x)
        y = max(0, y)
        
        self.controls_overlay.setGeometry(x, y, overlay_width, 90)
        self.controls_overlay.raise_()





    def showEvent(self, event):
        """وقتی پنجره نمایش داده می‌شود"""
        super().showEvent(event)
        QTimer.singleShot(50, self._update_overlay_position)
        QTimer.singleShot(50, self._update_zoom_position)  # اضافه کن
        QTimer.singleShot(50, self._update_steer_position)
    def closeEvent(self, event):
        self.camera.stop_camera()
        super().closeEvent(event)