from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QIcon
import sys

from core.app_controller import AppController


def main():
    app = QApplication(sys.argv)

    icon = QIcon("src/assets/app_icon.ico")
    app.setWindowIcon(icon)

    controller = AppController()
    controller.show_main_window()

    # IMPORTANT: force icon on main window
    controller.main_window.setWindowIcon(icon)

    exit_code = app.exec()

    controller.shutdown()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()