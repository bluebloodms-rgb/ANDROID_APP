import cv2
from PySide6.QtCore import QObject, QThread, Signal

# cv2.setLogLevel(cv2.LOG_LEVEL_ERROR)

class CameraWorker(QObject):
    frame_ready = Signal(object)
    camera_error = Signal(str)

    def __init__(self, camera_index: int):
        super().__init__()
        self.camera_index = camera_index
        self._running = False
        self.cap = None

    def start(self):
        backends = [
            cv2.CAP_MSMF,
            cv2.CAP_DSHOW,
            cv2.CAP_ANY
        ]

        self.cap = None
        for backend in backends:
            cap = cv2.VideoCapture(self.camera_index, backend)
            if cap.isOpened():
                self.cap = cap
                break

        if not self.cap.isOpened():
            self.camera_error.emit(f"Cannot open camera {self.camera_index}")
            return

        self._running = True

        while self._running:
            ret, frame = self.cap.read()
            if ret:
                self.frame_ready.emit(frame)
            else:
                cv2.waitKey(50)
                continue

        self.cap.release()

    def stop(self):
        self._running = False


class CameraInterface(QObject):
    frame_ready = Signal(object)
    camera_error = Signal(str)

    def __init__(self):
        super().__init__()
        self.thread = None
        self.worker = None
        self.current_camera = None

    def start_camera(self, camera_index: int):
        self.stop_camera()

        self.thread = QThread()
        self.worker = CameraWorker(camera_index)
        self.worker.moveToThread(self.thread)

        self.thread.started.connect(self.worker.start)
        self.worker.frame_ready.connect(self.frame_ready)
        self.worker.camera_error.connect(self.camera_error)

        self.thread.start()
        self.current_camera = camera_index

    def stop_camera(self):
        if self.worker:
            self.worker.stop()
        if self.thread:
            self.thread.quit()
            self.thread.wait()

        self.worker = None
        self.thread = None
        self.current_camera = None

    @staticmethod
    def detect_cameras(max_devices=2):
        available = []
        for i in range(max_devices):
            cap = cv2.VideoCapture(i)#, cv2.CAP_DSHOW)
            if cap is not None and cap.isOpened():
                ret, _ = cap.read()
                if ret:
                    available.append(i)
                cap.release()
        return available