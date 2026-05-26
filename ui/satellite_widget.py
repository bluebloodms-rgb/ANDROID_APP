from PySide6.QtWidgets import QWidget
from PySide6.QtGui import QPainter, QPen, QColor, QFont, QPixmap
from PySide6.QtCore import Qt, QRectF
from pathlib import Path

class SatelliteWidget(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedSize(85, 88)          # کمی ارتفاع بیشتر برای جلوگیری از تداخل
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.satellites = 0
        
        # بارگذاری آیکون
        assets_path = Path(__file__).parent.parent / "assets"
        self.icon_path = assets_path / "satellite.png"
        self.icon = QPixmap(str(self.icon_path)) if self.icon_path.exists() else None

    def setSatellites(self, count: int):
        self.satellites = count if count is not None else 0
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        w = self.width()
        h = self.height()

        # رسم آیکون ماهواره
        if self.icon:
            icon_size = 52
            x = (w - icon_size) // 2
            y = 16                    # آیکون کمی پایین‌تر آمد
            painter.drawPixmap(x, y, self.icon.scaled(
                icon_size, icon_size, 
                Qt.KeepAspectRatio, 
                Qt.SmoothTransformation)
            )

        # نمایش تعداد ماهواره
        painter.setPen(QColor(80, 200, 255))
        painter.setFont(QFont("Arial", 16, QFont.Bold))
        painter.drawText(
            QRectF(0, 64, w, 28),
            Qt.AlignCenter,
            str(self.satellites)
        )

        # برچسب SAT - بالاتر برده شد
        painter.setPen(QColor(50, 50, 50))   
        painter.setFont(QFont("Arial", 9, QFont.Bold))
        painter.drawText(
            QRectF(0, 3, w, 18),      # موقعیت بالاتر
            Qt.AlignCenter,
            "SAT"
        )