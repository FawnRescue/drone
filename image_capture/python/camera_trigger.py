from pymavlink import mavutil

# Connect to the MAVLink device
# Replace '/dev/ttyACM0' with your connection string. For example, for a UDP connection: 'udp:localhost:14550'
connection_string = 'udp:localhost:14540'
master = mavutil.mavlink_connection(connection_string)

print("Waiting for heartbeats from the MAVLink device...")
master.wait_heartbeat()
print("Heartbeat from MAVLink device received!")

# Loop to listen for the "TRIGGER_CAMERA" command
while True:
    msg = master.recv_match(type='CAMERA_TRIGGER', blocking=True)
    if msg:
        print("Camera trigger command received!")
        # Add your code here to handle the camera trigger event