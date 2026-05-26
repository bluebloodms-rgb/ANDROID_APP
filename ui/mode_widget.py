from PySide6.QtWidgets import QWidget
from PySide6.QtGui import QPainter, QPen, QColor, QFont, QPixmap
from PySide6.QtCore import Qt, QRectF
from pathlib import Path

class ModeWidget(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedSize(85, 88)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.mode = "---"
        
        # بارگذاری آیکون
        assets_path = Path(__file__).parent.parent / "assets"
        self.icon_path = assets_path / "plane-mode.png"
        self.icon = QPixmap(str(self.icon_path)) if self.icon_path.exists() else None

    def setMode(self, mode: str):
        if mode is None:
            self.mode = "---"
        else:
            # حداکثر 4 حرف
            self.mode = mode[:4] if len(mode) > 4 else mode
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        w = self.width()
        h = self.height()

        # رسم آیکون
        if self.icon:
            icon_size = 52
            x = (w - icon_size) // 2
            y = 16
            painter.drawPixmap(x, y, self.icon.scaled(
                icon_size, icon_size, 
                Qt.KeepAspectRatio, 
                Qt.SmoothTransformation)
            )

        # نمایش مقدار Mode
        painter.setPen(QColor(50, 50, 50))
        painter.setFont(QFont("Arial", 14, QFont.Bold))  # فونت کمی کوچکتر برای 4 حرف
        painter.drawText(
            QRectF(0, 64, w, 28),
            Qt.AlignCenter,
            self.mode
        )

        # برچسب MODE
        painter.setPen(QColor(50, 50, 50))
        painter.setFont(QFont("Arial", 9, QFont.Bold))
        painter.drawText(
            QRectF(0, 3, w, 18),
            Qt.AlignCenter,
            "MODE"
        )