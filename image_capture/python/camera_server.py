import cv2
import numpy as np
import socket
import sys
import io
import signal
from time import sleep
from seekcamera import (
    SeekCamera,
    SeekCameraFrame,
    SeekCameraIOType,
    SeekCameraManager,
    SeekCameraManagerEvent,
    SeekCameraFrameFormat,
)


class CameraServer:
    def __init__(self):
        self.capture_status = 0  # 0: no capture, 1: capture, 2: captured
        self.float_image: np.ndarray = None
        self.grayscale_image: np.ndarray = None
        self.bgr_image: np.ndarray = None
        self.webcam = cv2.VideoCapture(0)  # Initialize the webcam at the start

        if not self.webcam.isOpened():
            print("Cannot open webcam")
            sys.exit(1)

    def on_frame(self, camera: SeekCamera, camera_frame: SeekCameraFrame, _):
        if self.capture_status == 1:
            self.float_image = camera_frame.thermography_float
            self.grayscale_image = camera_frame.grayscale
            self.capture_rgb_image()  # Capture RGB image from webcam
            self.capture_status = 2
            print(
                f"Frame available: {camera.chipid} (size: {self.float_image.width}x{self.float_image.height})"
            )

    def capture_rgb_image(self):
        """Captures an RGB image from the initialized webcam."""
        ret, frame = self.webcam.read()
        if ret:
            self.bgr_image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    def shutdown(self):
        """Releases the webcam and other resources properly before shutting down."""
        self.webcam.release()
        print("Webcam released.")

    def on_event(self, camera: SeekCamera, event_type, event_status, _user_data):
        """Callback fired whenever a camera event occurs.

        Args:
            camera: The camera instance related to the event.
            event_type: Type of event triggered.
            event_status: Status of the event.
        """
        print(f"{event_type}: {camera.chipid}")

        if event_type == SeekCameraManagerEvent.CONNECT:
            camera.register_frame_available_callback(self.on_frame)
            camera.capture_session_start(
                SeekCameraFrameFormat.THERMOGRAPHY_FLOAT
                | SeekCameraFrameFormat.GRAYSCALE
            )

        elif event_type == SeekCameraManagerEvent.DISCONNECT:
            camera.capture_session_stop()

        elif event_type == SeekCameraManagerEvent.ERROR:
            print(f"{event_status}: {camera.chipid}")

    def start_server(self, host="0.0.0.0", port=15555):
        """Starts a TCP server to listen for capture commands.

        Args:
            host: Host address to bind the server.
            port: Port number to listen on.
        """

        def graceful_exit(sig, frame):
            print("\nShutting down server...")
            sys.exit(0)

        signal.signal(signal.SIGINT, graceful_exit)

        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
            server_socket.bind((host, port))
            server_socket.listen()
            print(f"Listening on {host}:{port}...")

            while True:
                try:
                    conn, addr = server_socket.accept()
                    with conn:
                        print(f"Connected by {addr}")
                        while True:
                            data = conn.recv(1024)
                            if not data:
                                break
                            self.handle_client_data(data, conn)
                except socket.error as e:
                    print(f"Socket error occurred: {e}")
                except KeyboardInterrupt:
                    graceful_exit(None, None)
                except Exception as e:
                    print(f"An unexpected error occurred: {e}")
                    break

    def handle_client_data(self, data, conn):
        command = data.decode().strip()
        if command == "capture":
            print("Capture")
            self.capture_status = 1
            status = 1
            conn.sendall(status.to_bytes(4))
        elif command == "transferRGB":
            while self.capture_status == 1:
                sleep(0.1)
            if self.capture_status == 2:
                print("transferRGB")
                rgb_image_corrected = cv2.cvtColor(self.bgr_image, cv2.COLOR_BGR2RGB)
                bytes =cv2.imencode('.png', rgb_image_corrected)[1].tobytes()
                conn.sendall(len(bytes).to_bytes(4))
                conn.sendall(bytes)
                self.bgr_image = None
        elif command == "transferThermal":
            while self.capture_status == 1:
                sleep(0.1)
            if self.capture_status == 2:
                print("transferThermal")
                # Rotate the image 180 degrees
                rotated_image = cv2.rotate(self.grayscale_image.data, cv2.ROTATE_180)
                bytes =cv2.imencode('.png', rotated_image)[1].tobytes()
                conn.sendall(len(bytes).to_bytes(4, byteorder='big'))
                conn.sendall(bytes)
                self.grayscale_image = None
        elif command == "transferFloat":
            while self.capture_status == 1:
                sleep(0.1)
            if self.capture_status == 2:
                print("transferFloat")
                bytes = self.float_image.data.tobytes()
                conn.sendall(len(bytes).to_bytes(4, byteorder='big'))
                conn.sendall(bytes)
                self.float_image = None
    
    def send_shape_and_data(self, conn, image_array):
        height, width, channels = image_array.shape
        # Pack height and width as two 32-bit integers
        conn.sendall(height.to_bytes(4, byteorder='big'))
        conn.sendall(width.to_bytes(4, byteorder='big'))
        conn.sendall(channels.to_bytes(4, byteorder='big'))
        # Then send the image data
        conn.sendall(image_array.tobytes())



if __name__ == "__main__":
    server = CameraServer()
    try:
        with SeekCameraManager(SeekCameraIOType.USB) as manager:
            manager.register_event_callback(server.on_event)
            server.start_server()
    except KeyboardInterrupt:
        print("Interrupt received, shutting down.")
    finally:
        server.shutdown()
