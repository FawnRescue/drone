
- clone this repo
- setup secrets.properties file
# SETUP
- Setup develeopment toolchain: https://docs.px4.io/main/en/dev_setup/dev_env.html
- `wget https://github.com/mavlink/MAVSDK/releases/download/v2.4.1/mavsdk_server_musl_x86_64` (Might be a newer release available)
- `chmod +x mavsdk_server_musl_x86_64`
- `./mavsdk_server_musl_x86_64`
# Running the Simulator
- open new console
- `cd PX4-Autopilot`
- `make px4_sitl gz_x500`
- run the code in this repo

# Installation

## Use our provided image

## Manual installation

### Python Camera Server

Install opencv
```
apt install python-opencv
```
Install seekcamera drivers

### Services

Install mavlink router
https://github.com/mavlink-router/mavlink-router


Adjust path to executables in each of the services

Copy services to
```
cp services/* /etc/systemd/system
```
Enable services
```
systemctl enable <service>
```
Restart the drone