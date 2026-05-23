import sys
from pathlib import Path
from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QIcon
from core.app_controller import AppController


def main():
    app = QApplication(sys.argv)

    # مسیر صحیح و مطمئن برای آیکون
    base_dir = Path(__file__).parent
    icon_path = base_dir / "assets" / "app_icon.ico"

    print("Icon Path:", icon_path)
    print("Icon Exists:", icon_path.exists())

    if icon_path.exists():
        icon = QIcon(str(icon_path))
        app.setWindowIcon(icon)
    else:
        print("⚠️ Warning: app_icon.ico not found!")

    # ایجاد کنترلر و نمایش پنجره
    controller = AppController()
    controller.show_main_window()

    # اطمینان از اعمال آیکون روی پنجره اصلی
    if hasattr(controller, 'main_window') and controller.main_window is not None:
        controller.main_window.setWindowIcon(icon)

    # اجرای برنامه
    exit_code = app.exec()
    controller.shutdown()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()