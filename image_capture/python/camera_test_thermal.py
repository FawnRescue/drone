import socket
import cv2
import numpy as np

def save_image(data, filename):
    """
    Saves the image data to disk.
    """
    nparr = np.frombuffer(data, np.uint8)
    img_np = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    cv2.imwrite(filename, img_np)

def receive_image(sock):
    """
    Receives the image size and image data from the server.
    """
    # First receive the size of the image
    length = sock.recv(4)
    length = int.from_bytes(length, byteorder='big')
    # Receive the image data based on the size
    data = b''
    while len(data) < length:
        packet = sock.recv(length - len(data))
        if not packet:
            return None
        data += packet
    return data

def main():
    host = '127.0.0.1'  # The server's hostname or IP address
    port = 15555        # The port used by the server
    
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.connect((host, port))
        print("Connected to server.")
        
        # Send a capture command
        sock.sendall(b'capture')
        # Wait for the server to process the capture command
        status = sock.recv(4)
        print("Capture command sent and acknowledged.")
        
        # Request transfer of the thermal image
        sock.sendall(b'transferThermal')
        print("Requested Thermal image transfer.")
        
        # Receive and save the thermal image
        data = receive_image(sock)
        if data:
            save_image(data, 'received_thermal_image.png')
            print("Thermal image saved as received_thermal-image.png.")
        else:
            print("Failed to receive Thermal image.")

if __name__ == "__main__":
    main()
