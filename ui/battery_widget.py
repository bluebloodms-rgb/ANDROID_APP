from PySide6.QtWidgets import QWidget
from PySide6.QtGui import QPainter, QPen, QBrush, QColor, QFont
from PySide6.QtCore import Qt, QRectF

class BatteryWidget(QWidget):
    def __init__(self, voltage: float = 22.0, parent=None):
        super().__init__(parent)
        self.setFixedSize(48, 78)           # کوچکتر شد
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.percentage = self.voltage_to_percent(voltage)
        self.voltage = voltage

    def voltage_to_percent(self, voltage: float) -> int:
        min_v = 19.0
        max_v = 24.2
        percent = ((voltage - min_v) / (max_v - min_v)) * 100
        return max(0, min(100, int(percent)))

    def setVoltage(self, voltage: float):
        self.voltage = voltage
        self.percentage = self.voltage_to_percent(voltage)
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        w = self.width()
        h = self.height()
        bat_w = 36
        bat_h = 58
        x = (w - bat_w) // 2
        y = 10

        # بدنه باتری
        painter.setPen(QPen(QColor(80, 80, 80), 4))
        painter.setBrush(Qt.NoBrush)
        painter.drawRoundedRect(QRectF(x, y, bat_w, bat_h), 5, 5)

        # نوک باتری
        painter.setBrush(QColor(80, 80, 80))
        painter.setPen(Qt.NoPen)
        painter.drawRect(x + bat_w//2 - 6, y - 8, 12, 7)

        # سطح شارژ
        fill_h = (bat_h - 6) * (self.percentage / 100.0)
        fill_color = QColor(0, 230, 100) if self.percentage > 25 else QColor(255, 80, 80)

        painter.setBrush(QBrush(fill_color))
        painter.setPen(Qt.NoPen)
        painter.drawRoundedRect(
            QRectF(x + 3, y + bat_h - 3 - fill_h, bat_w - 6, fill_h),
            2, 2
        )

        # درصد
        painter.setPen(QColor(255, 255, 255))
        painter.setFont(QFont("Arial", 8, QFont.Bold))
        painter.drawText(
            QRectF(x, y + 4, bat_w, bat_h - 8),
            Qt.AlignCenter,
            f"{self.percentage}%"
        )

        # ولتاژ
        painter.setFont(QFont("Arial", 6.5))
        painter.setPen(QColor(200, 200, 200))
        painter.drawText(
            QRectF(0, y + bat_h + 6, w, 14),
            Qt.AlignCenter,
            f"{self.voltage:.1f}V"
        )