from re import T
from PySide6.QtWidgets import QWidget, QPushButton, QHBoxLayout, QSizePolicy, QVBoxLayout, QLabel, QLineEdit
from PySide6.QtCore import Qt, QSize         
from PySide6.QtGui import QIcon             
from pathlib import Path                     

class ControlBar(QWidget):
    """نوار کنترل پایین صفحه"""

    def __init__(self, flight_interface, parent=None):
        super().__init__(parent)
        self.flight = flight_interface

        # وضعیت‌های داخلی
        self.current_op = 1
        self.current_mode = 2
        self.current_spd = 19
        self.current_cls = 0
        self.initialized = False

        # تم نوار
        self.setStyleSheet("""
            QWidget {
                background-color: rgba(30, 30, 30, 200);
                border-radius: 10px;
            }
        """)

        # Layout اصلی
        layout = QHBoxLayout(self)
        layout.setContentsMargins(10, 5, 10, 5)
        layout.setSpacing(15)

        # ==================== Operation ====================
        self.start_btn = QPushButton("start")
        self.cancel_btn = QPushButton("cancel")

        self.start_btn.setStyleSheet(self._button_style("#4CAF50", "#388E3C"))
        self.cancel_btn.setStyleSheet(self._button_style("#f44336", "#d32f2f"))

        self.start_btn.clicked.connect(lambda: self.flight.send_operation(2,self._get_pid_values()))
        self.cancel_btn.clicked.connect(lambda: self.flight.send_operation(1))

        # ==================== Mode ====================

        self.manual_btn = QPushButton("manual")
        self.auto_btn = QPushButton("auto")

        # رنگ یکسان حرفه‌ای (آبی تیره و جدی)
        mode_style = self._button_style("#1976D2", "#1565C0")

        self.manual_btn.setStyleSheet(mode_style)
        self.auto_btn.setStyleSheet(mode_style)

        self.manual_btn.clicked.connect(lambda: self.flight.send_mode(1))
        self.auto_btn.clicked.connect(lambda: self.flight.send_mode(2))


        # ==================== Speed ====================
        self.speed_1_btn = QPushButton("12 m/s")
        self.speed_3_btn = QPushButton("19 m/s")
        self.speed_6_btn = QPushButton("22 m/s")

        speed_style = self._button_style("#FF9800", "#F57C00")
        self.speed_1_btn.setStyleSheet(speed_style)
        self.speed_3_btn.setStyleSheet(speed_style)
        self.speed_6_btn.setStyleSheet(speed_style)

        self.speed_1_btn.clicked.connect(lambda: self.flight.send_speed(12))
        self.speed_3_btn.clicked.connect(lambda: self.flight.send_speed(19))
        self.speed_6_btn.clicked.connect(lambda: self.flight.send_speed(22))


        # ========== بخش Class (فقط آیکون، بدون متن) ==========
        assets_path = Path(__file__).parent.parent / "assets"

        self.person_btn = QPushButton()
        self.car_btn = QPushButton()
        self.balloon_btn = QPushButton()
        self.uav_btn = QPushButton()

        # بارگذاری آیکون‌ها
        if (assets_path / "people.png").exists():
            self.person_btn.setIcon(QIcon(str(assets_path / "people.png")))
            print("✓ Person icon loaded")
        else:
            print("✗ people.png NOT FOUND")

        if (assets_path / "sedan.png").exists():
            self.car_btn.setIcon(QIcon(str(assets_path / "sedan.png")))
            print("✓ Car icon loaded")
        else:
            print("✗ sedan.png NOT FOUND")

        if (assets_path / "balloon.png").exists():
            self.balloon_btn.setIcon(QIcon(str(assets_path / "balloon.png")))
            print("✓ Balloon icon loaded")
        else:
            print("✗ balloon.png NOT FOUND")

        if (assets_path / "drone.png").exists():
            self.uav_btn.setIcon(QIcon(str(assets_path / "drone.png")))
            print("✓ Drone icon loaded")
        else:
            print("✗ drone.png NOT FOUND")

        icon_size = QSize(32, 32)
        self.person_btn.setIconSize(icon_size)
        self.car_btn.setIconSize(icon_size)
        self.balloon_btn.setIconSize(icon_size)
        self.uav_btn.setIconSize(icon_size)

        # ========== تنظیم سایز یکسان برای دکمه‌های کلاس ==========
        button_size = QSize(50, 40)
        self.person_btn.setFixedSize(button_size)
        self.car_btn.setFixedSize(button_size)
        self.balloon_btn.setFixedSize(button_size)
        self.uav_btn.setFixedSize(button_size)
        # ===================================================

        # استایل فقط آیکون (بدون متن)
        class_style = """
            QPushButton {
                background-color: #00BCD4;
                color: white;
                border: none;
                padding: 8px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }
            QPushButton:hover { background-color: #0097A7; }
            QPushButton:disabled { background-color: #777; }
        """
        self.person_btn.setStyleSheet(class_style)
        self.car_btn.setStyleSheet(class_style)
        self.balloon_btn.setStyleSheet(class_style)
        self.uav_btn.setStyleSheet(class_style)

        self.person_btn.clicked.connect(lambda: self.flight.send_class(1))
        self.car_btn.clicked.connect(lambda: self.flight.send_class(2))
        self.balloon_btn.clicked.connect(lambda: self.flight.send_class(0))
        self.uav_btn.clicked.connect(lambda: self.flight.send_class(3))
        # ==================== 3 ستون اول PID (Yaw_1, Yaw_2, Roll) ====================
        pid_left_widget = QWidget()
        pid_left_widget.setFixedSize(260, 100)  # از 300 به 260
        pid_left_widget.setStyleSheet("""
            QWidget {
                background-color: rgba(0, 0, 0, 100);
                border-radius: 8px;
                margin: 2px;
            }
            QLineEdit {
                background-color: #6a6a6a;
                color: #ffffff;
                border: 1px solid #888;
                border-radius: 4px;
                padding: 5px;
                font-size: 11px;
                min-width: 70px;
                max-width: 80px;
            }
            QLineEdit:focus {
                border: 1px solid #4CAF50;
            }
            QLineEdit::placeholder {
                color: #cccccc;
            }
        """)

        pid_left_layout = QVBoxLayout(pid_left_widget)
        pid_left_layout.setContentsMargins(5, 5, 5, 5)
        pid_left_layout.setSpacing(5)

        columns_left = QHBoxLayout()
        columns_left.setSpacing(8)

        # Yaw_1
        yaw1_layout = QVBoxLayout()
        self.kp_yaw1_input = QLineEdit()
        self.kp_yaw1_input.setPlaceholderText("kp_yaw_1")
        self.kd_yaw1_input = QLineEdit()
        self.kd_yaw1_input.setPlaceholderText("kd_yaw_1")
        self.limit_yaw1_input = QLineEdit()
        self.limit_yaw1_input.setPlaceholderText("limit_yaw_1")
        yaw1_layout.addWidget(self.kp_yaw1_input)
        yaw1_layout.addWidget(self.kd_yaw1_input)
        yaw1_layout.addWidget(self.limit_yaw1_input)

        # Yaw_2
        yaw2_layout = QVBoxLayout()
        self.kp_yaw2_input = QLineEdit()
        self.kp_yaw2_input.setPlaceholderText("kp_yaw_2")
        self.kd_yaw2_input = QLineEdit()
        self.kd_yaw2_input.setPlaceholderText("kd_yaw_2")
        self.limit_yaw2_input = QLineEdit()
        self.limit_yaw2_input.setPlaceholderText("limit_yaw_2")
        yaw2_layout.addWidget(self.kp_yaw2_input)
        yaw2_layout.addWidget(self.kd_yaw2_input)
        yaw2_layout.addWidget(self.limit_yaw2_input)

        # Roll
        roll_layout = QVBoxLayout()
        self.kp_roll_input = QLineEdit()
        self.kp_roll_input.setPlaceholderText("kp_roll")
        self.kd_roll_input = QLineEdit()
        self.kd_roll_input.setPlaceholderText("kd_roll")
        self.limit_roll_input = QLineEdit()
        self.limit_roll_input.setPlaceholderText("limit_roll")
        roll_layout.addWidget(self.kp_roll_input)
        roll_layout.addWidget(self.kd_roll_input)
        roll_layout.addWidget(self.limit_roll_input)

        columns_left.addLayout(yaw1_layout)
        columns_left.addLayout(yaw2_layout)
        columns_left.addLayout(roll_layout)
        pid_left_layout.addLayout(columns_left)

        # اضافه کردن 3 ستون اول به layout اصلی
       




        # ==================== Layout ====================
        layout.addStretch(1)
        layout.addWidget(pid_left_widget)
        
        layout.addWidget(self.start_btn)
        layout.addWidget(self.cancel_btn)
        layout.addWidget(self._make_separator())
        
        layout.addWidget(self.manual_btn)
        layout.addWidget(self.auto_btn)
        layout.addWidget(self._make_separator())
        
        layout.addWidget(self.speed_1_btn)
        layout.addWidget(self.speed_3_btn)
        layout.addWidget(self.speed_6_btn)
        layout.addWidget(self._make_separator())


        layout.addWidget(self.uav_btn)
        layout.addWidget(self.person_btn)
        layout.addWidget(self.car_btn)
        layout.addWidget(self.balloon_btn)


        # ==================== 2 ستون آخر PID (Thrust, Servo) ====================
        pid_right_widget = QWidget()
        pid_right_widget.setFixedSize(200, 120)  # از 180,100 به 200,120
        pid_right_widget.setStyleSheet("""
            QWidget {
                background-color: rgba(0, 0, 0, 100);
                border-radius: 8px;
                margin: 2px;
            }
            QLineEdit {
                background-color: #6a6a6a;
                color: #ffffff;
                border: 1px solid #888;
                border-radius: 4px;
                padding: 5px;
                font-size: 11px;
                min-width: 70px;
                max-width: 80px;
            }
            QLineEdit:focus {
                border: 1px solid #4CAF50;
            }
            QLineEdit::placeholder {
                color: #cccccc;
            }
        """)

        pid_right_layout = QVBoxLayout(pid_right_widget)
        pid_right_layout.setContentsMargins(5, 5, 5, 5)
        pid_right_layout.setSpacing(5)

        columns_right = QHBoxLayout()
        columns_right.setSpacing(8)

        # Thrust
        thrust_layout = QVBoxLayout()
        self.kp_thrust_input = QLineEdit()
        self.kp_thrust_input.setPlaceholderText("kp_thrust")
        self.kd_thrust_input = QLineEdit()
        self.kd_thrust_input.setPlaceholderText("kd_thrust")
        self.limit_thrust_input = QLineEdit()
        self.limit_thrust_input.setPlaceholderText("limit_thrust")
        thrust_layout.addWidget(self.kp_thrust_input)
        thrust_layout.addWidget(self.kd_thrust_input)
        thrust_layout.addWidget(self.limit_thrust_input)

        # Servo
        srv_layout = QVBoxLayout()
        self.kp_srv_input = QLineEdit()
        self.kp_srv_input.setPlaceholderText("kp_srv")
        self.kd_srv_input = QLineEdit()
        self.kd_srv_input.setPlaceholderText("kd_srv")
        self.limit_srv_input = QLineEdit()
        self.limit_srv_input.setPlaceholderText("limit_srv")
        srv_layout.addWidget(self.kp_srv_input)
        srv_layout.addWidget(self.kd_srv_input)
        srv_layout.addWidget(self.limit_srv_input)

        columns_right.addLayout(thrust_layout)
        columns_right.addLayout(srv_layout)
        pid_right_layout.addLayout(columns_right)

        # اضافه کردن 2 ستون آخر به layout اصلی
        layout.addWidget(pid_right_widget)

        



     
        
                # بعد از layout.addWidget همه دکمه‌ها، اضافه کن:
        layout.setStretch(0, 1)  # stretch قبل از دکمه‌ها
        for i in range(layout.count()):
            item = layout.itemAt(i)
            if item.widget() and isinstance(item.widget(), QPushButton):
                item.widget().setMinimumWidth(60)  # حداقل عرض هر دکمه
                item.widget().setSizePolicy(QSizePolicy.Minimum, QSizePolicy.Fixed)
        layout.setStretch(layout.count() - 1, 1)  # stretch بعد از دکمه‌ها

    def _button_style(self, base_color, hover_color):
        return f"""
            QPushButton {{
                background-color: {base_color};
                color: white;
                border: none;
                padding: 8px 12px;
                border-radius: 6px;
                font-weight: bold;
                font-size: 11px;
            }}
            QPushButton:hover {{ background-color: {hover_color}; }}
            QPushButton:disabled {{ background-color: #777; }}
        """
    def _get_pid_values(self):
        """گرفتن مقادیر PID و برگرداندن به صورت رشته"""
        # ستون 1: Yaw_1
        kp_yaw1 = self.kp_yaw1_input.text().strip()
        kd_yaw1 = self.kd_yaw1_input.text().strip()
        limit_yaw1 = self.limit_yaw1_input.text().strip()
        
        # ستون 2: Yaw_2
        kp_yaw2 = self.kp_yaw2_input.text().strip()
        kd_yaw2 = self.kd_yaw2_input.text().strip()
        limit_yaw2 = self.limit_yaw2_input.text().strip()
        
        # ستون 3: Roll
        kp_roll = self.kp_roll_input.text().strip()
        kd_roll = self.kd_roll_input.text().strip()
        limit_roll = self.limit_roll_input.text().strip()
        
        # ستون 4: Thrust
        kp_thrust = self.kp_thrust_input.text().strip()
        kd_thrust = self.kd_thrust_input.text().strip()
        limit_thrust = self.limit_thrust_input.text().strip()
        
        # ستون 5: Servo
        kp_srv = self.kp_srv_input.text().strip()
        kd_srv = self.kd_srv_input.text().strip()
        limit_srv = self.limit_srv_input.text().strip()
        
        values = []
        for val in [kp_yaw1, kd_yaw1, limit_yaw1, kp_yaw2, kd_yaw2, limit_yaw2,
                    kp_roll, kd_roll, limit_roll, kp_thrust, kd_thrust, limit_thrust,
                    kp_srv, kd_srv, limit_srv]:
            values.append(val if val else "None")
        
        # خالی کردن همه فیلدها
        self.kp_yaw1_input.clear()
        self.kd_yaw1_input.clear()
        self.limit_yaw1_input.clear()
        self.kp_yaw2_input.clear()
        self.kd_yaw2_input.clear()
        self.limit_yaw2_input.clear()
        self.kp_roll_input.clear()
        self.kd_roll_input.clear()
        self.limit_roll_input.clear()
        self.kp_thrust_input.clear()
        self.kd_thrust_input.clear()
        self.limit_thrust_input.clear()
        self.kp_srv_input.clear()
        self.kd_srv_input.clear()
        self.limit_srv_input.clear()
        
        return f"kp_yaw_1={values[0]},kd_yaw_1={values[1]},limit_yaw_1={values[2]},kp_yaw_2={values[3]},kd_yaw_2={values[4]},limit_yaw_2={values[5]},kp_roll={values[6]},kd_roll={values[7]},limit_roll={values[8]},kp_thrust={values[9]},kd_thrust={values[10]},limit_thrust={values[11]},kp_srv={values[12]},kd_srv={values[13]},limit_srv={values[14]}"
    def _make_separator(self):
        sep = QWidget()
        sep.setFixedSize(2, 30)
        sep.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        sep.setStyleSheet("background-color: rgba(255,255,255,0.15); border-radius: 1px;")
        return sep

    def update_from_flight(self, op=None, mode=None, spd=None, cls=None, initialized=None):
        if initialized is not None:
            self.initialized = initialized
        if op is not None:
            self.current_op = op
        if mode is not None:
            self.current_mode = mode
        if spd is not None:
            self.current_spd = spd
        if cls is not None:
            self.current_cls = cls
        
        enabled = self.initialized

        start_enabled   = (self.current_op != 2) and enabled 

        manual_enabled = (self.current_mode != 1) and enabled 
        auto_enabled   = (self.current_mode != 2) and enabled 
        self.manual_btn.setEnabled(manual_enabled)
        self.auto_btn.setEnabled(auto_enabled)

        

        self.start_btn.setEnabled(start_enabled)
        self.cancel_btn.setEnabled(enabled)
        # Speed - منطق دقیقاً مثل کد اصلی تو
        self.speed_1_btn.setEnabled(enabled and (self.current_spd != 12))
        self.speed_3_btn.setEnabled(enabled and (self.current_spd != 19))
        self.speed_6_btn.setEnabled(enabled and (self.current_spd != 22))

        # Class - منطق دقیقاً مثل کد اصلی تو
        self.person_btn.setEnabled(enabled  and (self.current_cls != 1))
        self.car_btn.setEnabled(enabled  and (self.current_cls != 2))
        self.balloon_btn.setEnabled(enabled  and (self.current_cls != 0))
        self.uav_btn.setEnabled(enabled  and (self.current_cls != 3))