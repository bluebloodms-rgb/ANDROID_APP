from PySide6.QtWidgets import QWidget, QLabel, QPushButton, QComboBox, QVBoxLayout, QHBoxLayout, QSizePolicy,QLineEdit
from PySide6.QtCore import Qt,QTimer


class GroupBox(QWidget):
    def __init__(self, title: str, default_state="---",
                 bg_color="#e6e6e6", content_color="#9a9a9a"):
        super().__init__()

        # ===========================================================
        # KITCHEN ANALOGY:
        # ===========================================================
        # self (GroupBox)     = The entire kitchen counter space
        # outer (QVBoxLayout) = The empty space on the counter (no placemat)
        # container (QWidget) = A TRAY placed on the counter (has color)
        # layout (QVBoxLayout)= Rules for arranging things ON the tray
        # ===========================================================

        # Outer layout (no background here)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)

        # Painted container (ONLY this has background)
        self.container = QWidget()
        self.container.setObjectName("groupContainer")
        outer.addWidget(self.container)

        # Inner layout
        layout = QVBoxLayout(self.container)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(6)

        # Title
        self.title_label = QLabel(title)
        self.title_label.setStyleSheet(
            "font-weight: 600; font-size: 13px; background: transparent;"
        )

        # State label (read-only)
        self.state_label = QLabel(default_state)
        self.state_label.setStyleSheet(
            "color: #444; font-size: 11px; background: transparent;"
        )

        layout.addWidget(self.title_label)
        layout.addWidget(self.state_label)

        # Controls layout
        self.inner_layout = QHBoxLayout()
        self.inner_layout.setSpacing(10)
        layout.addLayout(self.inner_layout)
        # ==================== UPDATED STYLESHEET ====================
        self.container.setStyleSheet(f"""
            QWidget#groupContainer {{
                background-color: {bg_color};
                border-radius: 12px;
            }}
            
            QPushButton, QComboBox, QLineEdit {{
                border-radius: 6px;
                padding: 6px;
                background-color: {content_color};
                color: #fff;
            }}
            
            QLineEdit {{
                border: 1px solid #555;
                padding: 5px 8px;
            }}
            
            QLineEdit:focus {{
                border: 2px solid #4CAF50;
                background-color: #333;
            }}
            
            QPushButton:disabled, QComboBox:disabled, QLineEdit:disabled {{
                background-color: #777;
                color: #bbb;
            }}
        """)



        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)

class OperationGroup(GroupBox):
    def __init__(self, flight_interface):
        super().__init__("Operation", default_state="Operation: ---")
        self.flight = flight_interface

        self.start_btn = QPushButton("Start")
        self.cancel_btn = QPushButton("Cancel")

        self.inner_layout.addWidget(self.start_btn)
        self.inner_layout.addWidget(self.cancel_btn)
        self.start_btn.clicked.connect(
            lambda: self.flight.send_operation(2)
        )
        self.cancel_btn.clicked.connect(
            lambda: self.flight.send_operation(1)
        )

    def update_from_flight(self, op, mode, initialized):
        if not initialized:
            self.state_label.setText("Operation: Not started")
            # Disable buttons completely
            self.start_btn.setEnabled(False)
            self.cancel_btn.setEnabled(False)
            return

        text = "Ready" if op == 1 else "In operation"
        self.state_label.setText(f"Operation: {text}")
        start_allowed = mode == "GUIDED"
        self.start_btn.setEnabled(start_allowed)
        self.cancel_btn.setEnabled(True)

class ModeGroup(GroupBox):
    def __init__(self, flight_interface):
        super().__init__("Mode", default_state="Operation: ---")
        self.flight = flight_interface

        self.manual_btn = QPushButton("Manual")
        self.auto_btn = QPushButton("Automate")

        self.inner_layout.addWidget(self.manual_btn)
        self.inner_layout.addWidget(self.auto_btn)

        self.manual_btn.clicked.connect(
            lambda: self.flight.send_mode(1)
        )
        self.auto_btn.clicked.connect(
            lambda: self.flight.send_mode(2)
        )

    def update_from_flight(self, md, initialized):
        if not initialized:
            self.state_label.setText("Mode: ---")
            # Disable buttons completely
            self.manual_btn.setEnabled(False)
            self.auto_btn.setEnabled(False)
        else:
            text = "Manual" if md == 1 else "Automatic"
            self.state_label.setText(f"Mode: {text}")
            self.manual_btn.setEnabled(True)
            self.auto_btn.setEnabled(True)


class SpeedGroup(GroupBox):
    def __init__(self, flight_interface):
        super().__init__("Speed", default_state="Operation: ---")
        self.flight = flight_interface

        self.combo = QComboBox()
        self.combo.addItems(["1.5", "3.0", "6.0"])
        self.send_btn = QPushButton("Send")

        self.inner_layout.addWidget(self.combo)
        self.inner_layout.addWidget(self.send_btn)

        self.send_btn.clicked.connect(
            lambda: self.flight.send_speed(float(self.combo.currentText()))
        )

    def update_from_flight(self, spd, op, st, initialized):
        if not initialized:
            self.state_label.setText("Speed: ---")
            self.combo.setEnabled(False)
            self.send_btn.setEnabled(False)
        else:
            self.state_label.setText(f"Speed: {spd} m/s")
            allowed = (op == 1 and st == 1)
            self.combo.setEnabled(allowed)
            self.send_btn.setEnabled(allowed)

class ClassGroup(GroupBox):
    def __init__(self, flight_interface):
        super().__init__("Class", default_state="Operation: ---")
        self.flight = flight_interface

        self.combo = QComboBox()
        self.combo.addItems(["Person", "Car"])
        self.send_btn = QPushButton("Send")

        self.inner_layout.addWidget(self.combo)
        self.inner_layout.addWidget(self.send_btn)

        self.send_btn.clicked.connect(
            lambda: self.flight.send_class(
                0 if self.combo.currentText() == "Person" else 2
            )
        )

    def update_from_flight(self, cls, op, st, initialized):
        if not initialized:
            self.state_label.setText("Target: ---")
            self.combo.setEnabled(False)
            self.send_btn.setEnabled(False)
        else:
            text = "Person" if cls == 0 else "Car"
            self.state_label.setText(f"Target: {text}")
            allowed = (op == 1 and st == 1)
            self.combo.setEnabled(allowed)
            self.send_btn.setEnabled(allowed)


class ZoomGroup(GroupBox):
    def __init__(self, flight_interface):
        super().__init__("Zoom", "Zoom: ---")
        self.flight = flight_interface
        
        # Text input for zoom
        self.zoom_input = QLineEdit()
        self.zoom_input.setPlaceholderText("1.0 - 10.0")
        self.zoom_input.setAlignment(Qt.AlignCenter)
        self.zoom_input.setText("1.0")
        
        self.send_btn = QPushButton("Send")
        
        # Use inner_layout (your original name)
        self.inner_layout.addWidget(self.zoom_input, stretch=1)
        self.inner_layout.addWidget(self.send_btn)
        
        # Connect signals
        self.send_btn.clicked.connect(self._send_zoom)
        self.zoom_input.returnPressed.connect(self._send_zoom)

    def _send_zoom(self):
        try:
            zoom_text = self.zoom_input.text().strip()
            if not zoom_text:
                return
                
            zoom_level = float(zoom_text)
            
            if not (1.0 <= zoom_level <= 10.0):
                print(f"⚠️ Zoom must be between 1.0 and 10.0")
                return
                
            self.flight.send_zoom(zoom_level)
            
            # Visual feedback
            self.zoom_input.setStyleSheet("border: 2px solid #4CAF50;")
            QTimer.singleShot(800, lambda: self.zoom_input.setStyleSheet(""))
            
        except ValueError:
            print("❌ Please enter a valid number (e.g. 2.5)")
        except Exception as e:
            print("Error sending zoom:", e)

    def update_from_flight(self, zoom: float | None, initialized: bool):
        if not initialized:
            self.state_label.setText("Zoom: ---")
            self.zoom_input.setEnabled(False)
            self.send_btn.setEnabled(False)
            return

        if zoom is not None:
            self.state_label.setText(f"Zoom: {zoom:.1f}x")
            self.zoom_input.setText(f"{zoom:.1f}")
        else:
            self.state_label.setText("Zoom: ---")

        self.zoom_input.setEnabled(True)
        self.send_btn.setEnabled(True)