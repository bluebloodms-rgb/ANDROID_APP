from PySide6.QtWidgets import QWidget
from PySide6.QtGui import QPainter, QColor, QPen, QFont, QLinearGradient
from PySide6.QtCore import Qt, QRect


class CustomSteerSlider(QWidget):
    def __init__(self, parent=None, main_window=None):
        super().__init__(parent)
        self.main_window = main_window
        self.setFixedSize(60, 420)
        self.marker_position = 370  # موقعیت پایین برای 0 درجه
        self.dragging = False
        self.steer_value = 0  # 0 تا 90 درجه
        self.setMouseTracking(True)
        
        # Make background transparent
        self.setAttribute(Qt.WA_TranslucentBackground)
        
    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        
        # Draw semi-transparent background for slider area
        painter.setBrush(QColor(0, 0, 0, 150))
        painter.setPen(Qt.NoPen)
        painter.drawRoundedRect(10, 10, 40, self.height() - 20, 10, 10)
        
        # Draw slider track
        track_rect = QRect(25, 20, 8, self.height() - 40)
        
        # Gradient for steer track (آبی برای زاویه)
        gradient = QLinearGradient(0, track_rect.top(), 0, track_rect.bottom())
        gradient.setColorAt(0, QColor(80, 80, 255, 200))    # آبی (MAX = 90 درجه)
        gradient.setColorAt(0.5, QColor(80, 200, 255, 200)) # آبی روشن
        gradient.setColorAt(1, QColor(80, 255, 200, 200))   # سبز آبی (MIN = 0 درجه)
        
        painter.fillRect(track_rect, gradient)
        
        # Draw tick marks
        painter.setPen(QPen(QColor(255, 255, 255, 180), 1))
        for i in range(0, 11):
            y = track_rect.top() + (i * (track_rect.height() / 10))
            painter.drawLine(20, int(y), 38, int(y))
        
        # Draw labels
        painter.setPen(QPen(QColor(255, 255, 255, 200), 1))
        font = QFont("Arial", 7)
        painter.setFont(font)
        painter.drawText(8, track_rect.top() + 8, "90°")
        painter.drawText(8, track_rect.bottom() - 2, "0°")
        
        # Draw the movable marker
        marker_x = 15
        marker_width = 28
        marker_height = 22
        
        # Marker with transparency
        painter.setPen(QPen(QColor(255, 255, 255, 180), 2))
        painter.setBrush(QColor(255, 255, 255, 220))
        painter.drawRoundedRect(marker_x, self.marker_position - 11, 
                            marker_width, marker_height, 4, 4)
        
        # Draw marker handle lines
        painter.setPen(QPen(QColor(50, 50, 50), 2))
        painter.drawLine(marker_x + 8, self.marker_position - 4, 
                        marker_x + 20, self.marker_position - 4)
        painter.drawLine(marker_x + 8, self.marker_position + 1, 
                        marker_x + 20, self.marker_position + 1)
        painter.drawLine(marker_x + 8, self.marker_position + 6, 
                        marker_x + 20, self.marker_position + 6)
        
        # Draw steer value
        painter.setPen(QPen(QColor(255, 255, 255), 1))
        font = QFont("Arial", 9, QFont.Bold)
        painter.setFont(font)
        painter.drawText(marker_x + 32, self.marker_position + 4, f"{self.steer_value:.0f}°")
        
    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            marker_rect = QRect(15, self.marker_position - 11, 28, 22)
            if marker_rect.contains(event.pos()):
                self.dragging = True
                
    def mouseMoveEvent(self, event):
        if self.dragging:
            new_y = event.pos().y()
            min_y = 25
            max_y = self.height() - 30
            self.marker_position = max(min_y, min(max_y, new_y))
            
            track_height = self.height() - 55
            relative_pos = (self.marker_position - 25) / track_height
            
            # تبدیل: 0-100 به 0-90 درجه
            percent = 100 - (relative_pos * 100)  # 0 تا 100
            self.steer_value = percent * 0.9  # 0 تا 90
            self.steer_value = round(self.steer_value, 0)  # عدد صحیح
            
            self.update()
            if self.main_window:
                self.main_window.update_steer_display(self.steer_value)
            
    def mouseReleaseEvent(self, event):
        self.dragging = False
    
    def set_steer(self, value):
        """تنظیم زاویه از بیرون (value بین 0 تا 90)"""
        self.steer_value = max(0.0, min(90.0, float(value)))
        # تبدیل 0-90 به 0-100 برای موقعیت نشانگر
        percent = self.steer_value / 90  # 0 تا 1
        track_height = self.height() - 55
        self.marker_position = 25 + (track_height * (1 - percent))
        self.update()