from core.flight_interface import FlightInterface, FlightState

class AppController:
    def __init__(self):
        self.flight_interface = FlightInterface()
        self.main_window = None

    def show_main_window(self):
        from ui.main_window import MainWindow
        self.main_window = MainWindow(self)
        self.main_window.show()

    def shutdown(self):
        """
        Centralized cleanup point.
        Later:
        - stop camera
        - disconnect drone
        - release COM ports
        """
        print("Application shutting down cleanly...")