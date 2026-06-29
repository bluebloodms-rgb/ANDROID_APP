from PySide6.QtWidgets import QWidget, QPushButton, QVBoxLayout
from PySide6.QtGui import QPainter, QColor, QPen, QFont, QLinearGradient
from PySide6.QtCore import Qt, QRect, QTimer


class CustomSlider(QWidget):
    def __init__(self, parent=None, main_window=None):
        super().__init__(parent)
        self.main_window = main_window
        self.setFixedSize(90, 480)
        self.marker_position = 420
        self.set_steer(0)
        self.dragging = False
        self.steer_value = 0
        self.setMouseTracking(True)
        self.auto_repeat_timer = None
        
        self.setAttribute(Qt.WA_TranslucentBackground)
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        
        self.up_btn = QPushButton("▲")
        self.up_btn.setFixedSize(60, 25)
        self.up_btn.setStyleSheet("""
            QPushButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(80, 80, 255, 200), stop:0.5 rgba(80, 200, 255, 200), stop:1 rgba(80, 80, 255, 200));
                color: white;
                border: none;
                border-radius: 4px;
                font-size: 14px;
                font-weight: bold;
            }
            QPushButton:hover {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(100, 100, 255, 230), stop:0.5 rgba(100, 220, 255, 230), stop:1 rgba(100, 100, 255, 230));
            }
            QPushButton:pressed {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(60, 60, 200, 200), stop:0.5 rgba(60, 160, 200, 200), stop:1 rgba(60, 60, 200, 200));
            }
        """)
        
        self.down_btn = QPushButton("▼")
        self.down_btn.setFixedSize(60, 25)
        self.down_btn.setStyleSheet("""
            QPushButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(80, 80, 255, 200), stop:0.5 rgba(80, 200, 255, 200), stop:1 rgba(80, 80, 255, 200));
                color: white;
                border: none;
                border-radius: 4px;
                font-size: 14px;
                font-weight: bold;
            }
            QPushButton:hover {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(100, 100, 255, 230), stop:0.5 rgba(100, 220, 255, 230), stop:1 rgba(100, 100, 255, 230));
            }
            QPushButton:pressed {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 rgba(60, 60, 200, 200), stop:0.5 rgba(60, 160, 200, 200), stop:1 rgba(60, 60, 200, 200));
            }
        """)
        
        self.up_btn.pressed.connect(self._start_up)
        self.up_btn.released.connect(self._stop_auto)
        self.down_btn.pressed.connect(self._start_down)
        self.down_btn.released.connect(self._stop_auto)
        
        layout.addWidget(self.up_btn)
        layout.addStretch()
        layout.addWidget(self.down_btn)
        
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
        self.auto_repeat_timer.start(150)
        
    def _stop_auto(self):
        if self.auto_repeat_timer:
            self.auto_repeat_timer.stop()
            self.auto_repeat_timer = None
            
    def _increase(self):
        new_value = min(self.steer_value + 1, 90)
        self.set_steer(new_value)
        if self.main_window:
            self.main_window.update_steer_display(new_value)
            
    def _decrease(self):
        new_value = max(self.steer_value - 1, -90)
        self.set_steer(new_value)
        if self.main_window:
            self.main_window.update_steer_display(new_value)
        
    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        
        slider_top = 30
        slider_bottom = self.height() - 30
        slider_height = slider_bottom - slider_top
        
        painter.setBrush(QColor(0, 0, 0, 150))
        painter.setPen(Qt.NoPen)
        painter.drawRoundedRect(10, slider_top, 40, slider_height, 10, 10)
        
        track_rect = QRect(25, slider_top + 10, 8, slider_height - 20)
        
        gradient = QLinearGradient(0, track_rect.top(), 0, track_rect.bottom())
        gradient.setColorAt(0, QColor(80, 80, 255, 200))
        gradient.setColorAt(0.5, QColor(80, 200, 255, 200))
        gradient.setColorAt(1, QColor(255, 80, 80, 200))
        
        painter.fillRect(track_rect, gradient)
        
        painter.setPen(QPen(QColor(255, 255, 255, 180), 1))
        for i in range(0, 11):
            y = track_rect.top() + (i * (track_rect.height() / 10))
            painter.drawLine(20, int(y), 38, int(y))
        
        painter.setPen(QPen(QColor(255, 255, 255, 200), 1))
        font = QFont("Arial", 7)
        painter.setFont(font)
        painter.drawText(8, track_rect.top() + 8, "90°")
        painter.drawText(8, track_rect.bottom() - 2, "-90°")
        
        marker_x = 15
        marker_width = 28
        marker_height = 22
        
        painter.setPen(QPen(QColor(255, 255, 255, 180), 2))
        painter.setBrush(QColor(255, 255, 255, 220))
        painter.drawRoundedRect(marker_x, self.marker_position - 11,
                                marker_width, marker_height, 4, 4)
        
        painter.setPen(QPen(QColor(50, 50, 50), 2))
        painter.drawLine(marker_x + 8, self.marker_position - 4,
                        marker_x + 20, self.marker_position - 4)
        painter.drawLine(marker_x + 8, self.marker_position + 1,
                        marker_x + 20, self.marker_position + 1)
        painter.drawLine(marker_x + 8, self.marker_position + 6,
                        marker_x + 20, self.marker_position + 6)

        # text بیرون از محدوده clipping رسم میشه
        painter.setClipping(False)
        painter.setPen(QPen(QColor(255, 255, 255), 1))
        font = QFont("Arial", 9, QFont.Bold)
        painter.setFont(font)
        text_rect = QRect(marker_x + 30, self.marker_position - 10, 50, 20)
        painter.drawText(text_rect, Qt.AlignVCenter | Qt.AlignLeft, f"{self.steer_value:.0f}°")
        
    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            marker_rect = QRect(15, self.marker_position - 11, 28, 22)
            if marker_rect.contains(event.pos()):
                self.dragging = True
                
    def mouseMoveEvent(self, event):
        if self.dragging:
            new_y = event.pos().y()
            min_y = 40
            max_y = self.height() - 40
            self.marker_position = max(min_y, min(max_y, new_y))
            
            track_height = max_y - min_y  # ← از min_y تا max_y، نه کل height
            relative_pos = (self.marker_position - min_y) / track_height  # 0.0 تا 1.0
            
            self.steer_value = 90 - (relative_pos * 180)  # 90 تا -90
            self.steer_value = max(-90, min(90, round(self.steer_value, 0)))  # ← clamp
            
            self.update()
            if self.main_window:
                self.main_window.update_steer_display(self.steer_value)
            
    def mouseReleaseEvent(self, event):
        self.dragging = False
    
    def set_steer(self, value):
        self.steer_value = max(-90.0, min(90.0, float(value)))
        percent = (self.steer_value + 90) / 180  # 0.0 تا 1.0
        min_y = 40
        max_y = self.height() - 40
        self.marker_position = min_y + ((1 - percent) * (max_y - min_y))
        self.update()