from PySide6.QtGui import QPainter, QColor, QFont, QFontMetrics
from PySide6.QtCore import Qt
import math

import math
from PySide6.QtGui import QPainter, QColor, QFont, QFontMetrics
from PySide6.QtCore import Qt

class FrameOverlay:
    """Draw telemetry overlay on top of a QPixmap frame."""

    def __init__(self, flight_state):
        self.state = flight_state

        self.margin = 5
        self.padding = 8
        self.radius = 6
        self.alpha = 160

        self.font = QFont("Arial", 9)

    def _format_value(self, value, unit=""):
        if value is None:
            return "---"
        if isinstance(value, float):
            return f"{value:.1f}{unit}"
        return f"{value}{unit}"

    def draw(self, pixmap):
        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setFont(self.font)

        fm = QFontMetrics(self.font)

        # -------- TELEMETRY DATA (مخفف شده) --------
        items = [
            ("Bat", self._format_value(self.state.battery, "V")),
            ("Alt", self._format_value(self.state.altitude, "m")),
            ("Hdop", "---" if self.state.hdop is None else f"{self.state.hdop:.1f}"),
            ("Sat", "---" if self.state.satellites is None else str(self.state.satellites)),
            ("Mode", self.state.mode[:4] if self.state.mode else "---"),
        ]

        # -------- محاسبه عرض هر آیتم --------
        item_widths = []
        for label, value in items:
            label_w = fm.horizontalAdvance(label)
            value_w = fm.horizontalAdvance(value)
            item_widths.append(max(label_w, value_w) + self.padding * 2)

        line_height = fm.height() * 2 + 4
        total_width = sum(item_widths) + self.margin * 2 + self.padding * (len(items) - 1)

        x = self.margin
        y = self.margin

        # -------- DRAW BACKGROUND --------
        painter.setPen(Qt.NoPen)
        painter.setBrush(QColor(0, 0, 0, self.alpha))
        painter.drawRoundedRect(
            x, y,
            total_width, line_height + 8,
            self.radius, self.radius
        )

        # -------- DRAW TEXT (افقی) --------
        painter.setPen(QColor(0, 255, 0))
        
        current_x = x + self.padding
        text_y_label = y + self.padding + fm.ascent()
        text_y_value = text_y_label + fm.height()

        for label, value in items:
            # متن عنوان (مخفف)
            painter.drawText(current_x, text_y_label, label)
            
            # مقدار
            painter.drawText(current_x, text_y_value, value)
            
            # بروزرسانی موقعیت X برای آیتم بعدی
            current_x += item_widths[items.index((label, value))] + self.padding

        painter.end()
        return pixmap
import cv2

def calculate_midpoint(x1, y1, x2, y2):
    return (x1 + x2) / 2, (y1 + y2) / 2


def cornerRect(img, bbox, l=30, t=5, t_center=2, a_c=4, m_p=3,
               colorR=(0, 255, 0), colorC=(0, 255, 0)):
    """Draws a fancy targeting reticle around a bounding box"""
    x, y, w, h = bbox
    x1, y1 = x + w, y + h
    center_x, center_y = x + w / 2, y + h / 2

    new_w, new_h = w / a_c, h / a_c
    new_x, new_y = center_x - new_w / 2, center_y - new_h / 2

    # Center small rectangle
    cv2.rectangle(img, (int(new_x), int(new_y)), 
                        (int(new_x + new_w), int(new_y + new_h)), 
                        colorR, t_center)

    # Lines from center to corners
    cv2.line(img, (int(new_x + new_w / 2), int(new_y)), (int(x + w / 2), y), colorC, t)
    cv2.line(img, (int(new_x), int(new_y + new_h / 2)), (int(x), int(y + h / 2)), colorC, t)
    cv2.line(img, (int(new_x + new_w), int(new_y + new_h / 2)), (int(x + w), int(y + h / 2)), colorC, t)
    cv2.line(img, (int(new_x + new_w / 2), int(new_y + new_h)), (int(x + w / 2), int(y + h)), colorC, t)

    # Small perpendicular marks
    mid_x1, mid_y1 = calculate_midpoint(int(new_x + new_w / 2), int(new_y), int(x + w / 2), y)
    cv2.line(img, (int(mid_x1 - m_p), int(mid_y1)), (int(mid_x1 + m_p), int(mid_y1)), colorC, t)

    mid_x2, mid_y2 = calculate_midpoint(int(new_x), int(new_y + new_h / 2), int(x), int(y + h / 2))
    cv2.line(img, (int(mid_x2), int(mid_y2 - m_p)), (int(mid_x2), int(mid_y2 + m_p)), colorC, t)

    mid_x3, mid_y3 = calculate_midpoint(int(new_x + new_w), int(new_y + new_h / 2), int(x + w), int(y + h / 2))
    cv2.line(img, (int(mid_x3), int(mid_y3 - m_p)), (int(mid_x3), int(mid_y3 + m_p)), colorC, t)

    mid_x4, mid_y4 = calculate_midpoint(int(new_x + new_w / 2), int(new_y + new_h), int(x + w / 2), int(y + h))
    cv2.line(img, (int(mid_x4 - m_p), int(mid_y4)), (int(mid_x4 + m_p), int(mid_y4)), colorC, t)

    # Corner L shapes
    cv2.line(img, (x, y), (x + l, y), colorC, t)
    cv2.line(img, (x, y), (x, y + l), colorC, t)
    cv2.line(img, (x1, y), (x1 - l, y), colorC, t)
    cv2.line(img, (x1, y), (x1, y + l), colorC, t)
    cv2.line(img, (x, y1), (x + l, y1), colorC, t)
    cv2.line(img, (x, y1), (x, y1 - l), colorC, t)
    cv2.line(img, (x1, y1), (x1 - l, y1), colorC, t)
    cv2.line(img, (x1, y1), (x1, y1 - l), colorC, t)

    return img