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