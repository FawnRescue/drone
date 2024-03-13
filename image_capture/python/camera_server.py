import socket
import sys
import signal
from time import sleep
from seekcamera import SeekCamera, SeekFrame, SeekCameraIOType, SeekCameraManager, SeekCameraManagerEvent, SeekCameraFrameFormat

class CameraServer:
    def __init__(self):
        self.capture_status = 0  # 0: no capture, 1: capture, 2: captured
        self.frame = None

    def on_frame(self, camera: SeekCamera, camera_frame: SeekFrame, _):
        """Callback fired whenever a new frame is available.

        Args:
            camera: The camera instance triggering the frame.
            camera_frame: The frame data.
        """
        if self.capture_status == 1:
            self.frame = camera_frame.thermography_float
            self.capture_status = 2
            print(f"Frame available: {camera.chipid} (size: {self.frame.width}x{self.frame.height})")

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
            camera.capture_session_start(SeekCameraFrameFormat.THERMOGRAPHY_FLOAT)

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
        """Handles data received from the client.

        Args:
            data: Data received from the client.
            conn: Connection object to send data back to the client.
        """
        command = data.decode().strip()
        if command == "capture":
            if self.capture_status == 0:
                self.capture_status = 1
                while self.capture_status == 1:
                    sleep(0.1)  # Adjust as necessary for your use case
                if self.capture_status == 2:
                    conn.sendall(self.frame.data.tobytes())  # Ensure 'frame' is a numpy array
                    self.capture_status = 0
                else:
                    conn.sendall("ERROR".encode())
            else:
                conn.sendall("BUSY".encode())

if __name__ == "__main__":
    server = CameraServer()
    with SeekCameraManager(SeekCameraIOType.USB) as manager:
        manager.register_event_callback(server.on_event)
        server.start_server()
