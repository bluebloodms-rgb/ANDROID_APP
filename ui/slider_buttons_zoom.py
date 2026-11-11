from PySide6.QtWidgets import QWidget, QPushButton, QGridLayout
from PySide6.QtCore import Qt, QTimer, Signal   # Signal رو هم می‌تونی حذف کنی
from PySide6.QtGui import QPainter, QLinearGradient, QColor, QPen, QBrush, QFont


class DPadWidget_Zoom(QWidget):
    """D-Pad کاملاً سازگار با CustomZoomSlider — بدون سیگنال"""
    
    def __init__(self, main_window=None, min_value=1.0, max_value=10.0, 
                 step=0.5, initial_value=1.0, parent=None):
        super().__init__(parent)
        
        self.main_window = main_window
        self.min_value = min_value
        self.max_value = max_value
        self.step = step
        self.current_value = initial_value
        self.auto_repeat_timer = None

        self.setFixedSize(160, 160)
        self.setAttribute(Qt.WA_TranslucentBackground)
        

        # Grid Layout
        layout = QGridLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        self.up_btn = self._create_btn("▲")
        self.down_btn = self._create_btn("▼")
        self.left_btn = self._create_btn("◀")
        self.right_btn = self._create_btn("▶")

        layout.addWidget(self.up_btn, 0, 1)
        layout.addWidget(self.left_btn, 1, 0)
        layout.addWidget(self.right_btn, 1, 2)
        layout.addWidget(self.down_btn, 2, 1)

        # اتصالات دکمه‌ها
        self.up_btn.pressed.connect(self._start_up)
        self.up_btn.released.connect(self._stop_auto)
        self.down_btn.pressed.connect(self._start_down)
        self.down_btn.released.connect(self._stop_auto)
        self.left_btn.pressed.connect(self._start_down)
        self.left_btn.released.connect(self._stop_auto)
        self.right_btn.pressed.connect(self._start_up)
        self.right_btn.released.connect(self._stop_auto)

    def _create_btn(self, text):
        btn = QPushButton(text)
        btn.setFixedSize(52, 52)
        btn.setFont(QFont("Arial", 24, QFont.Bold))
        btn.setStyleSheet("""
            QPushButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(255,170,0,150), stop:0.6 rgba(255,119,0,150), stop:1 rgba(230,92,0,150));
                color: white;
                border: 4px solid rgba(204,68,0,100);
                border-radius: 14px;
            }
            QPushButton:hover {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(255,187,34,200), stop:0.6 rgba(255,136,51,200), stop:1 rgba(255,102,51,200));
            }
            QPushButton:pressed {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(212,90,0,200), stop:0.6 rgba(181,74,0,200), stop:1 rgba(158,63,0,200));
            }
        """)
        return btn



    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        
        cx = self.width() // 2
        cy = self.height() // 2
        r = 26
        
        # اضافه کردن شفافیت به گرادیان
        gradient = QLinearGradient(cx-25, cy-25, cx+25, cy+25)
        gradient.setColorAt(0, QColor(255, 190, 60, 100))  # 100 = شفافیت
        gradient.setColorAt(1, QColor(255, 110, 30, 100))  # 100 = شفافیت
        
        painter.setBrush(QBrush(gradient))
        painter.setPen(QPen(QColor(180, 60, 0, 80), 7))
        painter.drawEllipse(cx - r, cy - r, r*2, r*2)

    def mousePressEvent(self, event):
    # event رو اینجا consume کن — به video_label نمی‌رسه
        event.accept()

    def mouseReleaseEvent(self, event):
        event.accept()

    


    # ====================== کنترل مقدار ======================
    def _start_up(self):
        self._increase()
        self._start_auto_repeat(self._increase)

    def _start_down(self):
        self._decrease()
        self._start_auto_repeat(self._decrease)

    def _start_auto_repeat(self, callback):
        if self.auto_repeat_timer:
            self.auto_repeat_timer.stop()
        self.auto_repeat_timer = QTimer()
        self.auto_repeat_timer.timeout.connect(callback)
        self.auto_repeat_timer.start(100)

    def _stop_auto(self):
        if self.auto_repeat_timer:
            self.auto_repeat_timer.stop()
            self.auto_repeat_timer = None

    def _increase(self):
        new_value = min(self.current_value + self.step, self.max_value)
        self._apply_value(new_value)

    def _decrease(self):
        new_value = max(self.current_value - self.step, self.min_value)
        self._apply_value(new_value)

    def _apply_value(self, new_value):
        if new_value != self.current_value:
            self.current_value = new_value
            print(self.current_value)
            
            # فقط به main_window اطلاع بده (مثل CustomZoomSlider)
            if self.main_window and hasattr(self.main_window, 'update_zoom_display'):
                self.main_window.update_zoom_display(self.current_value)
