from PySide6.QtWidgets import QWidget, QPushButton, QHBoxLayout
from PySide6.QtCore import Qt


class ControlBar(QWidget):
    """نوار کنترل پایین صفحه"""

    def __init__(self, flight_interface, parent=None):
        super().__init__(parent)
        self.flight = flight_interface

        # وضعیت‌های داخلی
        self.current_op = 1
        self.current_mode = 1
        self.current_spd = 12
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
        self.start_btn = QPushButton("START")
        self.cancel_btn = QPushButton("CANCEL")

        self.start_btn.setStyleSheet(self._button_style("#4CAF50", "#388E3C"))
        self.cancel_btn.setStyleSheet(self._button_style("#f44336", "#d32f2f"))

        self.start_btn.clicked.connect(lambda: self.flight.send_operation(2))
        self.cancel_btn.clicked.connect(lambda: self.flight.send_operation(1))

        # ==================== Mode ====================
        self.manual_btn = QPushButton("MANUAL")
        self.auto_btn = QPushButton("AUTO")

        self.manual_btn.setStyleSheet(self._button_style("#2196F3", "#1976D2"))
        self.auto_btn.setStyleSheet(self._button_style("#9C27B0", "#7B1FA2"))

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

        # ==================== Class ====================
        self.person_btn = QPushButton("👤 Person")
        self.car_btn = QPushButton("🚗 Car")
        self.balloon_btn = QPushButton("🎈 Balloon")

        class_style = self._button_style("#00BCD4", "#0097A7")
        self.person_btn.setStyleSheet(class_style)
        self.car_btn.setStyleSheet(class_style)
        self.balloon_btn.setStyleSheet(class_style)

        self.person_btn.clicked.connect(lambda: self.flight.send_class(1))
        self.car_btn.clicked.connect(lambda: self.flight.send_class(2))
        self.balloon_btn.clicked.connect(lambda: self.flight.send_class(0))

        # ==================== Layout ====================
        layout.addStretch(1)
        
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
        
        layout.addWidget(self.person_btn)
        layout.addWidget(self.car_btn)
        layout.addWidget(self.balloon_btn)
        
        layout.addStretch(1)

    def _button_style(self, base_color, hover_color):
        """استایل مشترک دکمه‌ها"""
        return f"""
            QPushButton {{
                background-color: {base_color};
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }}
            QPushButton:hover {{ background-color: {hover_color}; }}
            QPushButton:disabled {{ background-color: #777; }}
        """

    def _make_separator(self):
        """جداکننده عمودی"""
        sep = QWidget()
        sep.setFixedSize(2, 30)
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
        is_operating = (self.current_op == 2)

        # Operation
        self.start_btn.setEnabled(enabled)
        self.cancel_btn.setEnabled(enabled)

        # Mode
        self.manual_btn.setEnabled(enabled and not is_operating)
        self.auto_btn.setEnabled(enabled and not is_operating)

        # Speed - منطق دقیقاً مثل کد اصلی تو
        self.speed_1_btn.setEnabled(enabled and not is_operating and (self.current_spd != 12))
        self.speed_3_btn.setEnabled(enabled and not is_operating and (self.current_spd != 19))
        self.speed_6_btn.setEnabled(enabled and not is_operating and (self.current_spd != 22))

        # Class - منطق دقیقاً مثل کد اصلی تو
        self.person_btn.setEnabled(enabled and not is_operating and (self.current_cls != 1))
        self.car_btn.setEnabled(enabled and not is_operating and (self.current_cls != 2))
        self.balloon_btn.setEnabled(enabled and not is_operating and (self.current_cls != 0))