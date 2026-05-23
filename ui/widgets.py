from PySide6.QtWidgets import QWidget, QLabel, QPushButton, QVBoxLayout, QHBoxLayout, QSizePolicy
from PySide6.QtCore import Qt, QTimer


class ControlBar(QWidget):
    """نوار کنترل پایین صفحه - فقط دکمه‌ها کنار هم"""
    
    def __init__(self, flight_interface, parent=None):
        super().__init__(parent)
                # تم دارک برای خود نوار
        self.setStyleSheet("""
            QWidget {
                background-color: rgba(30, 30, 30, 200);
                border-radius: 10px;
            }
        """)
        self.flight = flight_interface
        
        # چیدمان اصلی افقی
        layout = QHBoxLayout(self)
        layout.setContentsMargins(10, 5, 10, 5)
        layout.setSpacing(15)
        
        # ========== بخش Operation ==========
        self.start_btn = QPushButton("START")
        self.cancel_btn = QPushButton("CANCEL")
        self.start_btn.setStyleSheet("""
            QPushButton {
                background-color: #4CAF50;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }
            QPushButton:hover { background-color: #388E3C; }
            QPushButton:disabled { background-color: #777; }
        """)
        self.cancel_btn.setStyleSheet("""
            QPushButton {
                background-color: #f44336;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }
            QPushButton:hover { background-color: #d32f2f; }
            QPushButton:disabled { background-color: #777; }
        """)
        
        self.start_btn.clicked.connect(lambda: self.flight.send_operation(2))
        self.cancel_btn.clicked.connect(lambda: self.flight.send_operation(1))
        
        # ========== بخش Mode ==========
        self.manual_btn = QPushButton("MANUAL")
        self.auto_btn = QPushButton("AUTO")
        self.manual_btn.setStyleSheet("""
            QPushButton {
                background-color: #2196F3;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }
            QPushButton:hover { background-color: #1976D2; }
            QPushButton:disabled { background-color: #777; }
        """)
        self.auto_btn.setStyleSheet("""
            QPushButton {
                background-color: #9C27B0;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }
            QPushButton:hover { background-color: #7B1FA2; }
            QPushButton:disabled { background-color: #777; }
        """)
        
        self.manual_btn.clicked.connect(lambda: self.flight.send_mode(1))
        self.auto_btn.clicked.connect(lambda: self.flight.send_mode(2))
        
        # ========== بخش Speed (3 دکمه) ==========
        self.speed_1_btn = QPushButton("12 m/s")
        self.speed_3_btn = QPushButton("19 m/s")
        self.speed_6_btn = QPushButton("22 m/s")
        
        speed_style = """
            QPushButton {
                background-color: #FF9800;
                color: white;
                border: none;
                padding: 10px 15px;
                border-radius: 8px;
                font-weight: bold;
                font-size: 12px;
            }
            QPushButton:hover { background-color: #F57C00; }
            QPushButton:disabled { background-color: #777; }
        """
        self.speed_1_btn.setStyleSheet(speed_style)
        self.speed_3_btn.setStyleSheet(speed_style)
        self.speed_6_btn.setStyleSheet(speed_style)
        
        self.speed_1_btn.clicked.connect(lambda: self.flight.send_speed(12))
        self.speed_3_btn.clicked.connect(lambda: self.flight.send_speed(19))
        self.speed_6_btn.clicked.connect(lambda: self.flight.send_speed(22))
        
        # ========== بخش Class (3 دکمه با آیکون بعداً) ==========
        self.person_btn = QPushButton("👤 Person")
        self.car_btn = QPushButton("🚗 Car")
        self.balloon_btn = QPushButton("🎈 Balloon")
        
        class_style = """
            QPushButton {
                background-color: #00BCD4;
                color: white;
                border: none;
                padding: 10px 15px;
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
        
        self.person_btn.clicked.connect(lambda: self.flight.send_class(1))
        self.car_btn.clicked.connect(lambda: self.flight.send_class(2))
        self.balloon_btn.clicked.connect(lambda: self.flight.send_class(0))  
        
        # ========== اضافه کردن همه به layout ==========
        # Operation
        layout.addWidget(self.start_btn)
        layout.addWidget(self.cancel_btn)
        
        # جداکننده بصری (خط عمودی)
        sep1 = self._make_separator()
        layout.addWidget(sep1)
        
        # Mode
        layout.addWidget(self.manual_btn)
        layout.addWidget(self.auto_btn)
        
        sep2 = self._make_separator()
        layout.addWidget(sep2)
        
        # Speed
        layout.addWidget(self.speed_1_btn)
        layout.addWidget(self.speed_3_btn)
        layout.addWidget(self.speed_6_btn)
        
        sep3 = self._make_separator()
        layout.addWidget(sep3)
        
        # Class
        layout.addWidget(self.person_btn)
        layout.addWidget(self.car_btn)
        layout.addWidget(self.balloon_btn)
        
        # کشش به چپ و راست برای وسط‌چین شدن
        layout.insertStretch(0, 1)
        layout.addStretch(1)
        
        # ذخیره وضعیت برای update_from_flight
        self.current_op = 1
        self.current_mode = 1
        self.current_spd = 12
        self.current_cls = 0
        self.initialized = False
        
    def _make_separator(self):
        """ساخت جداکننده عمودی بین بخش‌ها"""
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
        
        # وضعیت عملیات
        is_operating = (self.current_op == 2)  # In operation
        
        # Operation buttons (همیشه فعال اگر initialized باشد)
        self.start_btn.setEnabled(enabled)
        self.cancel_btn.setEnabled(enabled)
        
        # Mode buttons (در حالت عملیات غیرفعال)
        self.manual_btn.setEnabled(enabled and not is_operating)
        self.auto_btn.setEnabled(enabled and not is_operating)
        
        # Speed buttons
        # اگر در حالت عملیات نباشد و مقدار spd با دکمه یکی نباشد → فعال
        speed_1_enabled = enabled and not is_operating and (self.current_spd != 12)
        speed_3_enabled = enabled and not is_operating and (self.current_spd != 19)
        speed_6_enabled = enabled and not is_operating and (self.current_spd != 22)
        
        self.speed_1_btn.setEnabled(speed_1_enabled)
        self.speed_3_btn.setEnabled(speed_3_enabled)
        self.speed_6_btn.setEnabled(speed_6_enabled)
        
        # Class buttons
        class_1_enabled = enabled and not is_operating and (self.current_cls != 1)  # Person
        class_2_enabled = enabled and not is_operating and (self.current_cls != 2)  # Car
        class_3_enabled = enabled and not is_operating and (self.current_cls != 0)  # Balloon
        
        self.person_btn.setEnabled(class_1_enabled)
        self.car_btn.setEnabled(class_2_enabled)
        self.balloon_btn.setEnabled(class_3_enabled)
            
