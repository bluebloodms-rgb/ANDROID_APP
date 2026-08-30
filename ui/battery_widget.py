from PySide6.QtWidgets import QWidget
from PySide6.QtGui import QPainter, QPen, QBrush, QColor, QFont
from PySide6.QtCore import Qt, QRectF

class BatteryWidget(QWidget):
    def __init__(self, voltage: float = 22.0, parent=None):
        super().__init__(parent)
        self.setFixedSize(85, 88)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.voltage = voltage

    def setVoltage(self, voltage: float):
        self.voltage = voltage
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        w = self.width()
        h = self.height()

        # ========== رسم آیکون باتری (افقی) ==========
        bat_w = 52
        bat_h = 22
        x = (w - bat_w) // 2
        y = (h - bat_h) // 2 - 8  # وسط عمودی

        # بدنه باتری (دور سیاه)
        painter.setPen(QPen(QColor(0, 0, 0), 2))
        painter.setBrush(Qt.NoBrush)
        painter.drawRoundedRect(QRectF(x, y, bat_w, bat_h), 4, 4)

        # نوک باتری (سمت راست)
        painter.setBrush(QColor(0, 0, 0))
        painter.setPen(Qt.NoPen)
        painter.drawRect(x + bat_w, y + 5, 5, bat_h - 10)

        # سه خط داخلی (سفید)
        painter.setPen(Qt.NoPen)
        painter.setBrush(QBrush(QColor(0, 0, 0)))
        
        # خط اول (چپ)
        painter.drawRect(x + 6, y + 4, 10, bat_h - 8)
        # خط دوم (وسط)
        painter.drawRect(x + 21, y + 4, 10, bat_h - 8)
        # خط سوم (راست)
        painter.drawRect(x + 36, y + 4, 10, bat_h - 8)

        # ========== نمایش ولتاژ ==========
        painter.setPen(QColor(0, 0, 0))  # مشکی
        painter.setFont(QFont("Arial", 16, QFont.Bold))
        painter.drawText(
            QRectF(0, y + bat_h + 15, w, 28),
            Qt.AlignCenter,
            f"{self.voltage:.1f}"
        )

        # برچسب VOLT (مشکی)
        painter.setPen(QColor(0, 0, 0))   
        painter.setFont(QFont("Arial", 9, QFont.Bold))
        painter.drawText(
            QRectF(0, y - 20, w, 18),
            Qt.AlignCenter,
            "BAT"
        )