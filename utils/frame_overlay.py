from PySide6.QtGui import QPainter, QColor, QFont, QFontMetrics
from PySide6.QtCore import Qt
import math

class FrameOverlay:
    """Draw telemetry overlay on top of a QPixmap frame."""

    def __init__(self, flight_state):
        self.state = flight_state

        self.margin = 2
        self.padding = 5
        self.radius = 5
        self.alpha = 120

        self.font = QFont("Arial", 10)

    def _floor2(self, value):
        if value is None:
            return "---"
        return f"{math.floor(value * 100) / 100:.2f}"

    def draw(self, pixmap):
        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setFont(self.font)

        fm = QFontMetrics(self.font)

        # -------- TELEMETRY TEXT --------
        rows = [
            ("Battery:",     self._floor2(self.state.battery)),
            ("Altitude:",    self._floor2(self.state.altitude)),
            ("Hdop:",        "---" if self.state.hdop is None else f"{self.state.hdop:.2f}"),
            ("SatNum:",  "---" if self.state.satellites is None else str(self.state.satellites)),
            ("Mode:",        self.state.mode or "---"),
        ]

        # -------- CALCULATE COLUMN WIDTHS --------
        label_width = max(fm.horizontalAdvance(label) for label, _ in rows)
        value_width = max(fm.horizontalAdvance(value) for _, value in rows)

        line_height = fm.height()
        total_height = line_height * len(rows)

        rect_width = (
            self.padding * 3 +
            label_width +
            value_width
        )
        rect_height = total_height + self.padding * 2

        x = self.margin
        y = self.margin

        # -------- DRAW BACKGROUND --------
        painter.setPen(Qt.NoPen)
        painter.setBrush(QColor(0, 0, 0, self.alpha))
        painter.drawRoundedRect(
            x, y,
            rect_width, rect_height,
            self.radius, self.radius
        )

        # -------- DRAW TEXT --------
        painter.setPen(QColor(0, 255, 0))

        text_x_label = x + self.padding
        text_x_value = text_x_label + label_width + self.padding
        text_y = y + self.padding + fm.ascent()

        for label, value in rows:
            painter.drawText(text_x_label, text_y, label)
            painter.drawText(text_x_value, text_y, value)
            text_y += line_height

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