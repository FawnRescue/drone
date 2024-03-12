import socket
import sys
from time import sleep
import signal
from seekcamera import (
    SeekCameraIOType,
    SeekCameraManager,
    SeekCameraManagerEvent,
    SeekCameraFrameFormat,
    SeekCamera,
    SeekFrame,
)

global capture  # 0: no capture, 1: capture, 2: captured
capture = 0
global frame
frame = None


def on_frame(camera: SeekCamera, camera_frame: SeekFrame, _):
    global capture
    global frame
    """Callback fired whenever a new frame is available."""
    if capture == 1:
        frame = camera_frame.thermography_float
        capture = 2
        print(
            "Frame available: {cid} (size: {w}x{h})".format(
                cid=camera.chipid, w=frame.width, h=frame.height
            )
        )


def on_event(camera: SeekCamera, event_type, event_status, _user_data):
    """Callback fired whenever a camera event occurs."""
    print("{}: {}".format(str(event_type), camera.chipid))

    if event_type == SeekCameraManagerEvent.CONNECT:
        camera.register_frame_available_callback(on_frame)
        camera.capture_session_start(SeekCameraFrameFormat.THERMOGRAPHY_FLOAT)

    elif event_type == SeekCameraManagerEvent.DISCONNECT:
        camera.capture_session_stop()

    elif event_type == SeekCameraManagerEvent.ERROR:
        print("{}: {}".format(str(event_status), camera.chipid))


def start_server(host="0.0.0.0", port=12345):
    global capture
    global frame

    def graceful_exit(sig, frame):
        print("\nShutting down server...")
        sys.exit(0)

    # Register the signal handler for graceful exit
    signal.signal(signal.SIGINT, graceful_exit)

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind((host, port))
        s.listen()
        print(f"Listening on {host}:{port}...")
        while True:
            try:
                conn, addr = s.accept()
                with conn:
                    print(f"Connected by {addr}")
                    while True:
                        data = conn.recv(1024)
                        if not data:
                            break
                        if data.decode().strip() == "capture":
                            if capture == 0:
                                capture = 1
                                while capture == 1:
                                    sleep(0.1)  # Adjust as necessary for your use case
                                if capture == 2:
                                    conn.sendall(frame.data.tobytes())  # Ensure 'frame' is a numpy array
                                    capture = 0
                                else:
                                    conn.sendall("HILFE1".encode())
                            else:
                                conn.sendall("HILFE2".encode())
            except socket.error as e:
                if e.winerror == 10053:
                    print("Connection was aborted by the software in your host machine.")
                else:
                    print(f"Socket error occurred: {e}")
            except KeyboardInterrupt:
                graceful_exit(None, None)
            except Exception as e:
                print(f"An unexpected error occurred: {e}")
                break


if __name__ == "__main__":
    with SeekCameraManager(SeekCameraIOType.USB) as manager:
        manager.register_event_callback(on_event)
        start_server()
